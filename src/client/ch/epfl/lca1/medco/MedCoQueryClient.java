package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.axis2.MedCoQueryRequestDelegate;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import ch.epfl.lca1.medco.unlynx.UnlynxDecrypt;
import ch.epfl.lca1.medco.util.MedCoUtil;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.apache.commons.cli.*;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.javatuples.Triplet;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.PrintWriter;

import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by misbach on 15.07.17.
 */
public class MedCoQueryClient {

//    static void  disableCertificateValidation() {
//
//        // Create a trust manager that does not validate certificate chains
//        TrustManager[] trustAllCerts = new TrustManager[] {
//                new X509TrustManager() {
//                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//                        return new X509Certificate[0];
//                    }
//                    public void checkClientTrusted(
//                            java.security.cert.X509Certificate[] certs, String authType) {
//                    }
//                    public void checkServerTrusted(
//                            java.security.cert.X509Certificate[] certs, String authType) {
//                    }
//                }
//        };
//
//        // Install the all-trusting trust manager
//        try {
//            SSLContext sc = SSLContext.getInstance("SSL");
//            sc.init(null, trustAllCerts, new java.security.SecureRandom());
//            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//        } catch (GeneralSecurityException e) {
//            e.printStackTrace();
//        }
//    }

    /**
     * Output on stderr information of the query, output on stdout the times
     *
     * @param args
     */
    public static void main(String[] args) throws InterruptedException {

        //disableCertificateValidation();

        // disable all logging (to control all output)
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.DEBUG);

        // get parameters from command-line and set the client configuration
        CommandLine cmd = parseCli(args);
        String[] serversUrl = cmd.getOptionValues("server");
        boolean querySHRINE = (serversUrl.length == 1);

        String queryName = cmd.getOptionValue("name");
        String queryId = cmd.getOptionValue("query");
        int numRepetitions;
        try{
            numRepetitions = Integer.parseInt(cmd.getOptionValue("repetitions"));
        } catch (NumberFormatException e){
            e.printStackTrace();
            return;
        }
        String filename = cmd.getOptionValue("filename");
        String username = cmd.getOptionValue("username");
        String password = cmd.getOptionValue("password");
        String domain = cmd.getOptionValue("domain");
        String projectId = cmd.getOptionValue("project");
        setClientConfig(cmd.getOptionValue("unlynxBinary"), cmd.getOptionValue("unlynxGroupFile"));


        // initialize timers
        ClientTimers ts = new ClientTimers();

        final Map<Integer, String> timesJsonOutput = new ConcurrentHashMap<>();

        for (int rep = 0; rep < numRepetitions; rep++) {
            final int repFinal = rep;

            // generate request
            MedCoI2b2MessageHeader auth = new MedCoI2b2MessageHeader(domain, projectId, username, false, 0, password);
            List<List<String>> parsedQuery = parseQuery(Integer.parseInt(queryId), querySHRINE);
            I2B2QueryRequest request = new I2B2QueryRequest(auth);
            request.setQueryDefinition(queryName, parsedQuery);

            // make request to every specified servers in a thread
            final UnlynxDecrypt decrypt = new UnlynxDecrypt();
            Thread[] queryThreads = new Thread[serversUrl.length];

            for (int i = 0; i < serversUrl.length; i++) {
                final int i_cpy = i;
                queryThreads[i] = new Thread(() -> {
                    try {

                        //todo: do better
                        I2B2QueryResponse response = null;
                        if (querySHRINE){
                            SHRINECell shrineCell = new SHRINECell(serversUrl[0], auth);
                            response = shrineCell.shrineQuery(request);
                        } else {
                            I2B2MedCoCell medCoCell = new I2B2MedCoCell(serversUrl[i_cpy], auth);
                            response = medCoCell.medcoQuery(request);
                        }
                        Triplet<String, String, String> results = response.getQueryResults();

                        timesJsonOutput.put(i_cpy, results.getValue2());
                        System.err.println("Query results for node " + serversUrl[i_cpy] + ", id " + i_cpy + ", iter " + repFinal + ":\n" +
                                " - pub key used: " + results.getValue0() + "\n" +
                                " - enc result: " + results.getValue1() + "\n" +
                                " - dec result: " + decrypt.decryptInt(results.getValue1(), MedCoQueryRequestDelegate.clientSeckey) + "\n" +
                                " - times:" + results.getValue2() + "\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            for (Thread queryThread : queryThreads) {
                queryThread.start();
            }

            for (Thread queryThread : queryThreads) {
                queryThread.join();
            }

            // output in stdout the times
            double tmp1, tmp2;
            tmp1 = tmp2 = 0;
            for (Map.Entry<Integer, String> result : timesJsonOutput.entrySet()) {
                System.out.println("{\"" + result.getKey() + "\":" + result.getValue() + "}");

                JsonObject jsonResult = Json.parse(result.getValue()).asObject();

                ts.tt += jsonResult.get("overall").asObject().getInt("", 0);
                ts.userinfo += jsonResult.get("steps").asObject().getInt("User information retrieval", 0);
                ts.qpi2b2 += jsonResult.get("steps").asObject().getInt("Query parsing/splitting", 0);
                ts.qt += jsonResult.get("steps").asObject().getInt("Query tagging", 0);
                ts.i2b2query += jsonResult.get("steps").asObject().getInt("i2b2 query", 0);
                ts.psretrieval += jsonResult.get("steps").asObject().getInt("i2b2 patient set retrieval", 0);

                ts.ddtet += jsonResult.getInt("DDTRequest execution time", 0);
                ts.ddtct += jsonResult.getInt("DDTRequest communication time", 0);
                ts.ddtpt += jsonResult.getInt("DDTRequest parsing time", 0);

                ts.agget += jsonResult.getInt("AggRequest execution time", 0);
                ts.aggpt += jsonResult.getInt("AggRequest parsing time", 0);
                ts.aggat += jsonResult.getInt("AggRequest aggregation time", 0);
                if (jsonResult.getInt("AggRequest communication time", 0) > tmp1)
                    tmp1 = jsonResult.getInt("AggRequest communication time", 0);


                // first run
                if (rep == 0) {
                    ts.first_tt += jsonResult.get("overall").asObject().getInt("", 0);
                    ts.first_userinfo += jsonResult.get("steps").asObject().getInt("User information retrieval", 0);
                    ts.first_qpi2b2 += jsonResult.get("steps").asObject().getInt("Query parsing/splitting", 0);
                    ts.first_qt += jsonResult.get("steps").asObject().getInt("Query tagging", 0);
                    ts.first_i2b2query += jsonResult.get("steps").asObject().getInt("i2b2 query", 0);
                    ts.first_psretrieval += jsonResult.get("steps").asObject().getInt("i2b2 patient set retrieval", 0);

                    ts.first_ddtet += jsonResult.getInt("DDTRequest execution time", 0);
                    ts.first_ddtct += jsonResult.getInt("DDTRequest communication time", 0);
                    ts.first_ddtpt += jsonResult.getInt("DDTRequest parsing time", 0);

                    ts.first_agget += jsonResult.getInt("AggRequest execution time", 0);
                    ts.first_aggpt += jsonResult.getInt("AggRequest parsing time", 0);
                    ts.first_aggat += jsonResult.getInt("AggRequest aggregation time", 0);
                    if (jsonResult.getInt("AggRequest communication time", 0) > tmp2)
                        tmp2 = jsonResult.getInt("AggRequest communication time", 0);
                }


            }

            ts.aggct += tmp1;
            ts.first_aggct += tmp2;

            System.out.flush();

        }

        ts.Divide(timesJsonOutput.size(), numRepetitions);

        // todo: client pub / priv keys --> hardcoded in axis2 service

        // store average times (in seconds) in the output file
        storeTimers(filename, ts);
    }

    /**
     * store timers in an output file (to ease out simulations)
     */
    private static void storeTimers(String filename, ClientTimers ts){
        try{
            PrintWriter writer = new PrintWriter(filename, "UTF-8");
            writer.println("QUERY FIRST RUN / AVERAGES: ");
            writer.println("Overall time: " + Double.toString(ts.first_tt) + " / " + Double.toString(ts.tt));

            writer.println("");

            writer.println("user information retrieval: " + Double.toString(ts.first_userinfo) + " / " + Double.toString(ts.userinfo));
            writer.println("Query parsing i2b2: " + Double.toString(ts.first_qpi2b2) + " / " + Double.toString(ts.qpi2b2));
            writer.println("Query tagging: " + Double.toString(ts.first_qt) + " / " + Double.toString(ts.qt));
            writer.println("i2b2 query: " + Double.toString(ts.first_i2b2query) + " / " + Double.toString(ts.i2b2query));
            writer.println("Patient set retrieval: " + Double.toString(ts.first_psretrieval) + " / " + Double.toString(ts.psretrieval));

            writer.println("");

            writer.println("ddt request exec time: " + Double.toString(ts.first_ddtet) + " / " + Double.toString(ts.ddtet));
            writer.println("ddt request comm time: " + Double.toString(ts.first_ddtct) + " / " + Double.toString(ts.ddtct));
            writer.println("ddt request parsing time: " + Double.toString(ts.first_ddtpt) + " / " + Double.toString(ts.ddtpt));

            writer.println("agg req exec time: " + Double.toString(ts.first_agget) + " / " + Double.toString(ts.agget));
            writer.println("agg req parsing time: " + Double.toString(ts.first_aggpt) + " / " + Double.toString(ts.aggpt));
            writer.println("agg req agg time: " + Double.toString(ts.first_aggat) + " / " + Double.toString(ts.aggat));
            writer.println("agg req comm time: " + Double.toString(ts.first_aggct) + " / " + Double.toString(ts.aggct));

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * parse query, ex: X OR Y AND F AND Z OR H ==> (X OR Y) AND (F) AND (Z OR H)
     */
    private static List<List<String>> parseQuery(int queryId, boolean useSHRINE) {
        List<List<String>> parsedQuery = new ArrayList<>();
        String[] orTerms = useSHRINE ?
                getShrineQueryUseCase(queryId).split(" AND ") :
                getQueryUseCase(queryId).split(" AND ");

        for (String orTerm : orTerms) {
            String[] terms = orTerm.split(" OR ");
            List<String> orTermsList = new ArrayList<>();

            Collections.addAll(orTermsList, terms);
            parsedQuery.add(orTermsList);
        }

        return parsedQuery;
    }

    private static void setClientConfig(String unlynxBinPath, String groupFilePath) {
        MedCoUtil.getInstance().setProperty("medco.i2b2.waittimems", "600000"); // 10m timeout
        MedCoUtil.getInstance().setProperty("medco.unlynx.binarypath", unlynxBinPath);
        MedCoUtil.getInstance().setProperty("medco.unlynx.debuglevel", "0");
        MedCoUtil.getInstance().setProperty("medco.unlynx.proofs", "0");
        MedCoUtil.getInstance().setProperty("medco.unlynx.groupfilepath", groupFilePath);
    }

    // command line parsing
    private static CommandLine parseCli(String[] args) {
        Options options = new Options();

        Option serverInput = new Option("s", "server", true, "MedCo cell server URL");
        serverInput.setRequired(true);
        options.addOption(serverInput);

        Option nameInput = new Option("n", "name", true, "Query name");
        nameInput.setRequired(true);
        options.addOption(nameInput);

        Option queryInput = new Option("q", "query", true, "The query in i2b2 format: OR groups separated by AND");
        queryInput.setRequired(true);
        options.addOption(queryInput);

        Option repetitionsInput = new Option("r", "repetitions", true, "Number of repetitions for a given query");
        repetitionsInput.setRequired(true);
        options.addOption(repetitionsInput);

        Option filenameInput = new Option("f", "filename", true, "Output filename");
        filenameInput.setRequired(true);
        options.addOption(filenameInput);

        Option usernameInput = new Option("u", "username", true, "The login username");
        usernameInput.setRequired(true);
        options.addOption(usernameInput);

        Option pwInput = new Option("w", "password", true, "The login password");
        pwInput.setRequired(true);
        options.addOption(pwInput);

        Option domainInput = new Option("d", "domain", true, "The login domain");
        domainInput.setRequired(true);
        options.addOption(domainInput);

        Option projectInput = new Option("p", "project", true, "The login project id");
        projectInput.setRequired(true);
        options.addOption(projectInput);

        Option unlynxBinInput = new Option("b", "unlynxBinary", true, "Path to the unlynx binary");
        unlynxBinInput.setRequired(true);
        options.addOption(unlynxBinInput);

        Option unlynxGroupFileInput = new Option("g", "unlynxGroupFile", true, "Path to the unlynx group file (servers public keys)");
        unlynxGroupFileInput.setRequired(true);
        options.addOption(unlynxGroupFileInput);

        CommandLineParser parser = new GnuParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("medcoClient", options);
            System.exit(1);
            return null;
        }

        return cmd;
    }

    // todo: use this to send to shrine
    private static String getShrineQueryUseCase(int nb) {
        switch (nb) {

            case 101: // use-case 1 medco-normal encrypted
                return
                        // skin -> MEDCO_ENC:1	; cut melanoma: MEDCO_ENC:2
                        "\\\\ENCRYPTED_KEY\\ZJGanAVkwmYlFIA49fcj47udIoVzDNIGvnGL6C29dtlk/CEt2bqhFoe+fFQ35qiFnRZo6kice7GWm+A1y1IArA==\\" +
                                " AND " +
                                "\\\\ENCRYPTED_KEY\\IF1ZzAX6xZnpPKBFCZGSriAXuY7PsKWbSkOnBwmDFj2lQZ3/wn+HmWcupHR0A7maSr4YJnGkyNXKnP3AvkpDwQ==\\" +
                                " AND " +

                                // variants ids for [Hugo_Symbol=BRAF AND Protein_Position=600/766]
                                "\\\\ENCRYPTED_KEY\\odaeRfdWL+IRaCcZp5NMUtCUT8NW4ldAlHLRMWBl4gsO5dmlQ0xfy2ORNFuSLygRRymF7ld9XM87o8/ewy9LaA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\ZsM3nNP0zGWEmbhUcWmJAb0PWtGMRWJwHGI4RjBRyCht3rZjGDJWu5XYhkh1z3eSUOAVY/jwrdopn/220I5jOg==\\ OR " +
                                // 2 replacement variants for when the limit 50000 (desc sorted by concept_cd) are deleted
                                "\\\\ENCRYPTED_KEY\\5MbgrJvcvJdHBWJ+zwpXC9bunSNqtpVhp3j9gHew2w5BaCm/WKjkkdw3zZi0c4Mv+kFL/KUcQrC/yBIDuTAD1w==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\o/ZiHbcXWC76DvuOD/3F+tKaD8jUTyZLVAE7ym1kWznwQr8XyYxg4v0KxKYPPws0tTtQpqpslfwh0KePDgfVZg==\\";

            case 102: // use-case 2 medco-normal encrypted
                return
                        // skin -> MEDCO_ENC:1	; cut melanoma: MEDCO_ENC:2
                        "\\\\ENCRYPTED_KEY\\ZJGanAVkwmYlFIA49fcj47udIoVzDNIGvnGL6C29dtlk/CEt2bqhFoe+fFQ35qiFnRZo6kice7GWm+A1y1IArA==\\" +
                                " AND " +
                                "\\\\ENCRYPTED_KEY\\IF1ZzAX6xZnpPKBFCZGSriAXuY7PsKWbSkOnBwmDFj2lQZ3/wn+HmWcupHR0A7maSr4YJnGkyNXKnP3AvkpDwQ==\\" +
                                " AND " +
//["-7054948997223410688",
// "-7054948998062267136",
// "-7054968999892742144",
// "-7054948999337337856",
// "-7054948997064022784",
// "-7054953138544961536",
// "-7054948997064020736",
// "-7054923607457132544",
// "-7054904773018905600",
// "-7054898625779855360",
// "-7054948987408734208",
// "-7054923379857424384",
// "-7054917142457610240",
// "-7054861692282335232",
// "-7054948050082458624",
// "-7054922546600210432",
// "-7054949048695910400",
// "-7054923381098933248",
// "-7054861517262417920",
// "-7054918645662608384",
// "-7054905185268658176",
// "-7054904954414166016",
// "-7054898626853597184",
// "-7054904932905773056",
// "-7054861823278837760"]}
                                // variants ids for [Hugo_Symbol=BRAF AND (PTEN OR CDKN2A OR MAP2K1 OR MAP2K2)]
                                "\\\\ENCRYPTED_KEY\\PLAnpWTz8C92IPKXf7vFNQVeBWUP6bH7XbhDNAYfqhPoxZwY02SRRLFO2QEIxF2v3YtyHD4RoHO8mHrZ23Usdg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\ATWzLIq9+Q5P6foLqYK9UoUzWOuAzUZQz98WFER/cOS0rzVgD/fPLIg/R671gqm9+Ps1kGBgStRfSwSaYBIfhg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\WCC2LTY3Isd1hTL+USIcA4c8whnKY2aANdCipORs2Kh6XquUDXQXZFe5uX8JHgjG/rivMUS1FHR7p8m01RjPzw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\jutng4OJnR7wMheVo10DU8lR9LcISSmsYNc3vLE0anxV/Zp4qTgfOl+bHSeETh0KijSkgyOGcD54ANSukbIE1A==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\zMA8yk0AfuxhOrce5a3xp8jD2hjLSqr8Wg39TCs+F1V4hKSdUUuAK/2eAH8yhpnyeZuuKugMa4v0Oylcgu27NA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\PDFvuwlzyliqi5EN+7oxR4Ty93sO9AjHAlXrslzhJC5n22LzypScZ2z4cB978VIfFbcJ3R9yMI1t9nJuAHYgPw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Z6RWKoFkTps3QgnKGy7amEPligC06guuqZDgq4Bgb0++OzMBZxMx5a9QPQZwjQXyTwSwCO+BRQTGq0deCDWTPw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Lh/mji7/cfSxo0ST1ghA9c/INwK0RnJgoScx8tA+bHfbUW4g86gVVTd8isCOQ42ZPCmMfesUdfEeTv+PQRpINA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Lx+4gPhvFdvWi1zqurn+fIBW1qwoj2fJ1xuf/03Ia7gWGSWw2UX/6oiGryWPJUaTZ05hDoUx7OkbXuu7luA3wg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\XMhy6tn2jsRMA5Mnt9rGXpQOm3snvLarQ9sTfi9/Q4Jyt+qXCLPTMDNgDVYGvNg0vbS09O327PdenlHQo0Edrg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\oN7FF1vPVFFcxkKPXPYqZjNDmc2C6Re4PO0df9T8CyVghEpkiZD9Y/PL2rWzQ6cIeC53z3xtFFGdfSe/siic9A==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\PEnbzWHipkx7TGO1l+TD2SxrYKOP+itkP1oUuyzNDJiKZRc1SAdFt98y+x6MWaL2RRhmDx66SDzGUK9QbURu5Q==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\ldsIkxWoqmOF3CV4dwKkFXHnr2qtwoyCs627dRRn+pBgnSCGf97EbM6PY/pF6z6iDyGIFqemX3pXyGjrMFTLXA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\aydcWfgDG1tKTl411kaUbOs+lqzb03hoHujJ1g9Z5ysvHQPqPXoHeRqdI9BbAbwG40Wx5xHGFu8bOf9+J88Y0Q==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Nr7sqxGYcaxwaPpJl6DiedE2xZzSCWH1A9BB+J5BMVsVO4qdYENbAi7Si/BH1fyTkbEY9cIDmBZVuXoJAzqwVw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\gRfm7D5SGXJTjIiJOLFJiCnD9TQbYBb6+VdkTSVlEM3Qdaf4fR3pC4yE7KhdHtoJd3pQTeNeOC/QxzdZ+2ndug==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\5/uO6PNye73N+AE4dD6Y/m5s+UuCx1xz/wOeX8n3vAEEURODb4aZTpkFiLAF6PpshkQdnHjYRT4aolbZaEm+Ug==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\2bbFMyB9r/UVMVYFjurdp/qxdwZMnBDZHC33uMkHOdi61flyTNz4Ra6rgJjlCULSPHyxBBR/vHGzZQW6AKpx8w==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\5MbgrJvcvJdHBWJ+zwpXC9bunSNqtpVhp3j9gHew2w5BaCm/WKjkkdw3zZi0c4Mv+kFL/KUcQrC/yBIDuTAD1w==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\o/ZiHbcXWC76DvuOD/3F+tKaD8jUTyZLVAE7ym1kWznwQr8XyYxg4v0KxKYPPws0tTtQpqpslfwh0KePDgfVZg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\h5yUAXpNZv/FrqixPJEeh1TUM0S4CUYVAqkL/oCaC8VbDe35CHcKl9esPADZzzueywuSdrSKud90YJi+QjOn0w==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\41QQR9gLvUDoeszN/cBu7+bx9JWiaJ5W064s6fBgUJpwpSwqAKVRrMO54D/PN2Gh6C8K7fk1ULUxSpJakchDGw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\4hhxdvZrPqGh16U+ExdXZy3pAK+uhwlNQhp8vBlH4ShBjpzk6Pxze/10kSC8YfgfcIAFaRWjGVmdOnD+aNPRqA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\mgpDqM/u1hQGXz8JcD2jmOTuXQ6s918K9Ws2nSBVjYmb4pAGZhEdzqF0uJYUU8Mf9SWHC58Q6V7BKVYTmWNQow==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\fJBpEfSExkqebV25eyOwITyT20pIe4axMJBUzWz6AcIq6ZxUOJRCiBSRN7qQLg5WrKWjgu8EilLXMevC/s4mqA==\\" +
                                " AND " +
                                "\\\\ENCRYPTED_KEY\\5QR7ELo5r8SBp42YTB24EmlXaUj2VfUYpmGVDdBVevmW9JFrBVVi8BbQvqNJpNVL3CITBgVXVD3mWOzt0xpB3w==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Peacr2BXY6auBK9TbfX0e/XvAAaGCO5AA2hFWw8vON0/uggBIqHCMcvYluzzFT45wnR7f53DC4R6uKwQIZu2qw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\ZBV/osNnmVcIHIef1Tb+BpB49C8AV39dKoULEHKOF/rRH1sZHpa3pmEWzhUkVhg5gzhwh7NnckiLuNvFf18xnA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\bRXoSlsmoaYseQ7l8Qzw0g9C7xN5OKSyekmHOOBH5M/gT/qFGxGI3IZjNxy8fD8il3t+HJBuGKyAqf4/JH1feg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\rCh4Vp63vQo1WRKUPtKU9POTRdL0lXk83rtajUcJiNJyBsh5PWkDcAgPR5yByxSL3yNRd2AhndAETBxCgrtKIw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\WkrM4sETE2n99Kyqj6UC33AoaJQmpr6vyzh0mSWhoSpUEZmJLR3Aq6ckmUvixQfL25F/3lvaDr46O9x3Mm4Mxg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Gdiwb3+EyPrq62cILQtTcoKHeDmRLP5hsDN7pl3AoTCXaR7bMwI8NyTTa4BzhjNte9xqHlvokz6GzCgAFBXzsg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\vmk+wH+9PmrWFE7dMq1fJM88Ce2Y5e6v+D9P82aTsDG0mvqFxbFFLQ3kKyJUbYQyAWWr8aPZuCbW7vQ0MA0YHA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\TuIViS0nN83clzhVgmk8koAbcQVt496N9d9qrfX0rA9mWqoQ2vwRcHPxQYzrMPQTLabItwxKQmPX+mqniMBGtg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\pTsywvX60l8K9OB2/frNzOyOrpxKKTi1+J7YK9reMIPJAOR44p7z1e1OMGzddAKPceTVTkZCYd2+ex3pHVxt1g==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Dfb1fjwCOirZIDsPWZRl3p1TpHi7L0TOFXlDWKy3Mv7WT0YUC7Nu7t2WStbjec25UXeigMj84JW79bsm+sOGuQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\3R5bDkQyG2TV7Ae+pElZS8T5yOdB4OPYyISMuhwj5VrWbq9C+1Ze6Dlda7giHp0MjzwzZDOrieA0deX9MNxhKQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\rbP8hzF22hq1LC67qEZjQJ/zpTy8lC5UiKVvUuNQuNpDp1lZ1qAeSRNIVtmuYf0waNqxcpJgjl6TPV7Yviu4mA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\5oE2zWlMXT5Wh1G+yTXI0fmKKXdFQB3Yt7jm00KSsdj0mj3mDmsShMMcGaq9+8S+xEL1VyLzakxmp+LUKZ9svQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Q7C/6FtBqfqxBDgquat9juJyMK+1fa3hU4tsqGzovNMnitGyd0hGMH8kjHt0X2YEvhPZh83Gc91Oqc/kcu/U5Q==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\JPLQkyVY2cmzM+ypf2qwieO3y0rr0bEUsJ05VV9xLFNSugPyXnb6dAU6E1OFA3tAU2+w2/MIYkBnQRYCKDKzTA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\4S/AKzCHAq9ak/ekP1RJqukzcnsu6gCLV+7O6yBUgCK554uBlwzrlbHMlm6T0NHS5jnugsOJswCD5jE17GoocA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\uhKHddj2VK8EXhS6EXX12ZKnpae8+OS+apj1b2XUUzl9oWh7BeGHThjduiDQkrED78Y8ybAqJG3SRPKi8UVo3Q==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\cPoDXT719zMiNBZlhaadjYTzopzOlXA0gpYJU8Wak0GBfceBaB23teTlC6bIqCDhDo8eJfN9hAeXe3ofKmuBHA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\FLoHMX6nCBSflJOf8SRPZ0nnqgPQ76jZmWmcD7UFIc41Jpy2GDSX2O71T+iw+X0NBHBORAqwBmHcFADXCMgNdA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\WAcnqYvx9LJCBurqbvO1f+fQoBhTM9i0c15YTHuHGiVv33VwZC0WTCCuKokngEDPR0/VCvG9rQ+67WtfLkAsfQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\UoFUOBelqpeswaM95OoXjJ0h/rPLzu9MBHMM19xUjAexQ/luRjdPQgTa4Huaq+qJx/O57i4O18G+baxHWz3bVA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\WKd1A4k9SunHNVeOjDTN16LlS6NpQuQTeE92tQBNp7SlOzvp+U3oyydtiuVMeinVHpxzy/rVD9YuT7UuVQoWqg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\+zY+CpTiWomTFYR5RKNjsD/jA1XeHuUCUzt1uS8VYWb3sqjj57uveI8FCGNoQjzjRo/uRsQ7tlsH6A7re0MeCg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\FUqk1EpQRVemkC2I6WFz6WbmkPNHNCv4LEN0suK/BkghU+r85CQqiK8qfJzhy2mLluR5v79xbKESEjU3ft+LzQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\DOY4mqbLKmJddIgZAFa7XC6xA5CgoR+qbMBnqVbXe0nXhATQvTquCd20RkPEdTZnhIwGzuwfKTYEEZ4BPUU9gQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\z21ae9N4XDQoCrm9izMHhaqymOeO5QqAw6zWNOf6WQD7JeIARTL/4Wb61/e4ounjgmVSTiTiYDMtkL2DFL0EYQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\krq1JIHHxOFTtSDBD+/Pp/oCwdoZTagXVmPALa1sWbcnT41tk+Z8ArMxPhmziApHuZdN5rjRrh0yx/ssGFEcxA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\DbGP6YceFIY41xkGyU7Hy6niqAM1cAB/Y7UXdOzoLILQ0/j8a29vEOfTgHDOblqPiNxTZr3+TvvryNb5jguRng==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\v87vfugI4D97WGW81+bKWQotlJRsKm9hE37MvSIppmfij6+kirlnOn7PnzgGQIDEnNQingw8mPXt1xsI00SwcA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\/uapHvgrqCMdUW39sP40ZhkWHsxFHTak8zAClTiwJ0WI83KrOPliq0gbPAFifvmHg8Bz7KH2uuxYloCCVNlYKw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\Sw1TfXrnVyqp8yrN1VnrJGo34qI3QPlgob7sODHCx6/Zl3ouA2fIApzHSJmXaC8BRnBTSzAoD5Tyxto77ppvGQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\4V3ekfxiqXwOuRd3wPp9N9+p4cmN4xvGyH1SFXLORyrSsULokSlOQ6PD2SYhg5hIxJ6Zch8xKhMj2o1DmN5nJg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\qFr3OwS38t4vOxII2k3Znq7CzHZO9Ywv+rbwdEbdJWPLJsYrn5v4d/n9lIYfU/Fx8a5RVOtuI/9BOyN0yiHM6A==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\MrvcG8Fw1jaWhV+oRmlUXDv1bGxcB9MWNqXzm7x7edtKqC8z4A6joj40iY7lQ4ltNv4WdafGmquDmJNLPT/9vw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\ZHUl87v8Eq8uROiGZVxxEhZ3wdYlOlZN4q2hCoBBMeoB7Q/537pvWjrRbK0s34dq0CTU6TUXWPeA5s0AtWfSRw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\/esXvxNOwfF+ykW9As/SoQqultRa5mD9ohPYmUIiRi1HMj9Gzc3jh/yJ0RlTp4m47cL96hyozT8gMQBwoYJd0A==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\tv+a8iveehOoUokmFH04TAzoTh2DLBc10WZkVrEL1O1lNhZ8ZZttjhSD3z0JY+E9cfriD+9sOJ1vEspK++xi5g==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\HXsDe2aFAmrSZaOufmkGxIvFYq0FvZ3UOZWVb6QHFK3C0ChF8loORsYDoLQPN5Tx+V79wxgrtTVdjbtfS8IsdQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\vtZoIlSlwbVUN8EfqlEIxK51Y870g6g8viYsXXkNjgD67/XzhhJrkoQkugVCEMUxGBxPjSuAKCejzzUzMofW6Q==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\tInHSBETc/DfD5kb3yR8E5oBTwJfh7Krds/YyZoCHI+fUl75oV+ex2TIdKC3E9SPk6n485hdkTFVtYnj+uil0g==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\riz59ciRBLaamnrATesMdFFmwuX9OxrTdfqcPvVp4dhJj89Q8mISZf5cWLQqcpJFSuMI+sd2CoTOLY7wjmwstA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\hfyDzlGQWuVuv9TBrdRSmtVSqeW1wvPU1lu/iuGCvxY2e5Vlrd3qxZRvGZ17wlNRfwKW+EWb66dxW4iXJvLSHg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\njjht9UkJibOvreXaW3JyJElvgzM0wyl+JdtalwHriLZnTbkFA8ne4olZGsEHd+e4GeebNTaXyKa0z0sf/BlxA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\YDEtvcSnwKPATK9nzTcqZ+5C2zSqS92PjLzigfAh5QqTWYXR4MLuEDAyAdfjrQbc04v4Rp9ZhoLbETCg0rCAZA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\u9Ipaky0unaHFPfsZtGOjFKYoGbuEylqtbiiZm1SRsGWFrhToI58k7nACZOd41gCEoTClKXESP76Ay15vKDdHg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\plBiRGaiqvoSmIIBVYizbAga12e8S/t3BBxpNRDsytfZynX1LfkyDywjlGydfBAQM1pyLBfSWbIAw4iOfFQSrA==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\M0XMSTEg2EiRD12Ttk9bUU5Jl5ro0EZtX7DR7M+cfT8K1B/R3uMoqCM9gq94JQu/6fh//8h+/Bapo+pLQtjJEw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\mvpLCkyUNUgI/ib1/Az9c0sJX2OYCpYo/n2bymRUUgyc/4QeaNa+2LKaT9Ksx4zDKqdHa8H6Rv3laj0HdSXbBg==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\hN1QLRdgrfcpJY1nBSi3RE8jw9JDCLuV/RCqTqtAw0eAxG8kQlFWMrCLA7cPi2+so7ZEnW4aaUJt+L7M8gYSYw==\\ OR " +
                                "\\\\ENCRYPTED_KEY\\NZZ0GEeA7u1UMaZcjXYkwuaknBW8wvyojQ7D+CSVj6ef/2k+X1MihVM3CDMCZwvpNe10qpofhtvaSlIPY/3rRA==\\";

            default:
                return null;
        }
    }

    private static String getQueryUseCase(int nb) {
        switch (nb) {

            case 100: // use-case medco-normal test
                return
                        "\\\\NON_SENSITIVE_CLEAR\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\NON_SENSITIVE_CLEAR\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +

                        //"\\\\SENSITIVE_TAGGED\\medco\\encrypted\\/98y5inj97O+26HXW8fJnbHDH0CCohmlCYNgMfgJ2mzufKVl8PBffruVGm1C05tqWxrXKPNF9AMghe8ELmNmzA==\\ AND " +
                        //"\\\\SENSITIVE_TAGGED\\medco\\encrypted\\JgHLqZ3PHOHOVXdywB30ALXmx8V/1Eb6jWOQyZweXcOZNOyGy/nak4Ds4JWjdn3lkqqNVFRHDK/9RoHvEGl01A==\\";
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\/98y5inj97O+26HXW8fJnbHDH0CCohmlCYNgMfgJ2mzufKVl8PBffruVGm1C05tqWxrXKPNF9AMghe8ELmNmzA==\\";

            case 101: // use-case 1 medco-normal encrypted
                return
                        // skin -> MEDCO_ENC:1	; cut melanoma: MEDCO_ENC:2
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ZJGanAVkwmYlFIA49fcj47udIoVzDNIGvnGL6C29dtlk/CEt2bqhFoe+fFQ35qiFnRZo6kice7GWm+A1y1IArA==\\" +
                        " AND " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\IF1ZzAX6xZnpPKBFCZGSriAXuY7PsKWbSkOnBwmDFj2lQZ3/wn+HmWcupHR0A7maSr4YJnGkyNXKnP3AvkpDwQ==\\" +
                        " AND " +

                        // variants ids for [Hugo_Symbol=BRAF AND Protein_Position=600/766]
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\odaeRfdWL+IRaCcZp5NMUtCUT8NW4ldAlHLRMWBl4gsO5dmlQ0xfy2ORNFuSLygRRymF7ld9XM87o8/ewy9LaA==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ZsM3nNP0zGWEmbhUcWmJAb0PWtGMRWJwHGI4RjBRyCht3rZjGDJWu5XYhkh1z3eSUOAVY/jwrdopn/220I5jOg==\\ OR " +
                        // 2 replacement variants for when the limit 50000 (desc sorted by concept_cd) are deleted
                        //"\\\\SENSITIVE_TAGGED\\medco\\encrypted\\5MbgrJvcvJdHBWJ+zwpXC9bunSNqtpVhp3j9gHew2w5BaCm/WKjkkdw3zZi0c4Mv+kFL/KUcQrC/yBIDuTAD1w==\\ OR " +
                        //"\\\\SENSITIVE_TAGGED\\medco\\encrypted\\o/ZiHbcXWC76DvuOD/3F+tKaD8jUTyZLVAE7ym1kWznwQr8XyYxg4v0KxKYPPws0tTtQpqpslfwh0KePDgfVZg==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Dwl+tH0TMDYWFQW5t2xJFQ6hADx7Z1R5U7stVTq68cpgGi8DfcP4Lo4/k7Cf0EwDmVrVMM1IPqVULT6dgDe9hQ==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\9Ngkb24BbgSccB1zb2fHYV3zR2SOHZpamGrFYkwyUjlzyLVA83i0aLDnl+1lhCLVULbQFZaNXODV5iFr6pYXPg==\\";

            case 102: // use-case 2 medco-normal encrypted
                return
                    // skin -> MEDCO_ENC:1	; cut melanoma: MEDCO_ENC:2
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ZJGanAVkwmYlFIA49fcj47udIoVzDNIGvnGL6C29dtlk/CEt2bqhFoe+fFQ35qiFnRZo6kice7GWm+A1y1IArA==\\" +
                    " AND " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\IF1ZzAX6xZnpPKBFCZGSriAXuY7PsKWbSkOnBwmDFj2lQZ3/wn+HmWcupHR0A7maSr4YJnGkyNXKnP3AvkpDwQ==\\" +
                    " AND " +
//["-7054948997223410688",
// "-7054948998062267136",
// "-7054968999892742144",
// "-7054948999337337856",
// "-7054948997064022784",
// "-7054953138544961536",
// "-7054948997064020736",
// "-7054923607457132544",
// "-7054904773018905600",
// "-7054898625779855360",
// "-7054948987408734208",
// "-7054923379857424384",
// "-7054917142457610240",
// "-7054861692282335232",
// "-7054948050082458624",
// "-7054922546600210432",
// "-7054949048695910400",
// "-7054923381098933248",
// "-7054861517262417920",
// "-7054918645662608384",
// "-7054905185268658176",
// "-7054904954414166016",
// "-7054898626853597184",
// "-7054904932905773056",
// "-7054861823278837760"]}
                    // variants ids for [Hugo_Symbol=BRAF AND (PTEN OR CDKN2A OR MAP2K1 OR MAP2K2)]
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\PLAnpWTz8C92IPKXf7vFNQVeBWUP6bH7XbhDNAYfqhPoxZwY02SRRLFO2QEIxF2v3YtyHD4RoHO8mHrZ23Usdg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ATWzLIq9+Q5P6foLqYK9UoUzWOuAzUZQz98WFER/cOS0rzVgD/fPLIg/R671gqm9+Ps1kGBgStRfSwSaYBIfhg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\WCC2LTY3Isd1hTL+USIcA4c8whnKY2aANdCipORs2Kh6XquUDXQXZFe5uX8JHgjG/rivMUS1FHR7p8m01RjPzw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\jutng4OJnR7wMheVo10DU8lR9LcISSmsYNc3vLE0anxV/Zp4qTgfOl+bHSeETh0KijSkgyOGcD54ANSukbIE1A==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\zMA8yk0AfuxhOrce5a3xp8jD2hjLSqr8Wg39TCs+F1V4hKSdUUuAK/2eAH8yhpnyeZuuKugMa4v0Oylcgu27NA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\PDFvuwlzyliqi5EN+7oxR4Ty93sO9AjHAlXrslzhJC5n22LzypScZ2z4cB978VIfFbcJ3R9yMI1t9nJuAHYgPw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Z6RWKoFkTps3QgnKGy7amEPligC06guuqZDgq4Bgb0++OzMBZxMx5a9QPQZwjQXyTwSwCO+BRQTGq0deCDWTPw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Lh/mji7/cfSxo0ST1ghA9c/INwK0RnJgoScx8tA+bHfbUW4g86gVVTd8isCOQ42ZPCmMfesUdfEeTv+PQRpINA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Lx+4gPhvFdvWi1zqurn+fIBW1qwoj2fJ1xuf/03Ia7gWGSWw2UX/6oiGryWPJUaTZ05hDoUx7OkbXuu7luA3wg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\XMhy6tn2jsRMA5Mnt9rGXpQOm3snvLarQ9sTfi9/Q4Jyt+qXCLPTMDNgDVYGvNg0vbS09O327PdenlHQo0Edrg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\oN7FF1vPVFFcxkKPXPYqZjNDmc2C6Re4PO0df9T8CyVghEpkiZD9Y/PL2rWzQ6cIeC53z3xtFFGdfSe/siic9A==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\PEnbzWHipkx7TGO1l+TD2SxrYKOP+itkP1oUuyzNDJiKZRc1SAdFt98y+x6MWaL2RRhmDx66SDzGUK9QbURu5Q==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ldsIkxWoqmOF3CV4dwKkFXHnr2qtwoyCs627dRRn+pBgnSCGf97EbM6PY/pF6z6iDyGIFqemX3pXyGjrMFTLXA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\aydcWfgDG1tKTl411kaUbOs+lqzb03hoHujJ1g9Z5ysvHQPqPXoHeRqdI9BbAbwG40Wx5xHGFu8bOf9+J88Y0Q==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Nr7sqxGYcaxwaPpJl6DiedE2xZzSCWH1A9BB+J5BMVsVO4qdYENbAi7Si/BH1fyTkbEY9cIDmBZVuXoJAzqwVw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\gRfm7D5SGXJTjIiJOLFJiCnD9TQbYBb6+VdkTSVlEM3Qdaf4fR3pC4yE7KhdHtoJd3pQTeNeOC/QxzdZ+2ndug==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\5/uO6PNye73N+AE4dD6Y/m5s+UuCx1xz/wOeX8n3vAEEURODb4aZTpkFiLAF6PpshkQdnHjYRT4aolbZaEm+Ug==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\2bbFMyB9r/UVMVYFjurdp/qxdwZMnBDZHC33uMkHOdi61flyTNz4Ra6rgJjlCULSPHyxBBR/vHGzZQW6AKpx8w==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\5MbgrJvcvJdHBWJ+zwpXC9bunSNqtpVhp3j9gHew2w5BaCm/WKjkkdw3zZi0c4Mv+kFL/KUcQrC/yBIDuTAD1w==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\o/ZiHbcXWC76DvuOD/3F+tKaD8jUTyZLVAE7ym1kWznwQr8XyYxg4v0KxKYPPws0tTtQpqpslfwh0KePDgfVZg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\h5yUAXpNZv/FrqixPJEeh1TUM0S4CUYVAqkL/oCaC8VbDe35CHcKl9esPADZzzueywuSdrSKud90YJi+QjOn0w==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\41QQR9gLvUDoeszN/cBu7+bx9JWiaJ5W064s6fBgUJpwpSwqAKVRrMO54D/PN2Gh6C8K7fk1ULUxSpJakchDGw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\4hhxdvZrPqGh16U+ExdXZy3pAK+uhwlNQhp8vBlH4ShBjpzk6Pxze/10kSC8YfgfcIAFaRWjGVmdOnD+aNPRqA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\mgpDqM/u1hQGXz8JcD2jmOTuXQ6s918K9Ws2nSBVjYmb4pAGZhEdzqF0uJYUU8Mf9SWHC58Q6V7BKVYTmWNQow==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\fJBpEfSExkqebV25eyOwITyT20pIe4axMJBUzWz6AcIq6ZxUOJRCiBSRN7qQLg5WrKWjgu8EilLXMevC/s4mqA==\\" +
                    " AND " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\5QR7ELo5r8SBp42YTB24EmlXaUj2VfUYpmGVDdBVevmW9JFrBVVi8BbQvqNJpNVL3CITBgVXVD3mWOzt0xpB3w==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Peacr2BXY6auBK9TbfX0e/XvAAaGCO5AA2hFWw8vON0/uggBIqHCMcvYluzzFT45wnR7f53DC4R6uKwQIZu2qw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ZBV/osNnmVcIHIef1Tb+BpB49C8AV39dKoULEHKOF/rRH1sZHpa3pmEWzhUkVhg5gzhwh7NnckiLuNvFf18xnA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\bRXoSlsmoaYseQ7l8Qzw0g9C7xN5OKSyekmHOOBH5M/gT/qFGxGI3IZjNxy8fD8il3t+HJBuGKyAqf4/JH1feg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\rCh4Vp63vQo1WRKUPtKU9POTRdL0lXk83rtajUcJiNJyBsh5PWkDcAgPR5yByxSL3yNRd2AhndAETBxCgrtKIw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\WkrM4sETE2n99Kyqj6UC33AoaJQmpr6vyzh0mSWhoSpUEZmJLR3Aq6ckmUvixQfL25F/3lvaDr46O9x3Mm4Mxg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Gdiwb3+EyPrq62cILQtTcoKHeDmRLP5hsDN7pl3AoTCXaR7bMwI8NyTTa4BzhjNte9xqHlvokz6GzCgAFBXzsg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\vmk+wH+9PmrWFE7dMq1fJM88Ce2Y5e6v+D9P82aTsDG0mvqFxbFFLQ3kKyJUbYQyAWWr8aPZuCbW7vQ0MA0YHA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\TuIViS0nN83clzhVgmk8koAbcQVt496N9d9qrfX0rA9mWqoQ2vwRcHPxQYzrMPQTLabItwxKQmPX+mqniMBGtg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\pTsywvX60l8K9OB2/frNzOyOrpxKKTi1+J7YK9reMIPJAOR44p7z1e1OMGzddAKPceTVTkZCYd2+ex3pHVxt1g==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Dfb1fjwCOirZIDsPWZRl3p1TpHi7L0TOFXlDWKy3Mv7WT0YUC7Nu7t2WStbjec25UXeigMj84JW79bsm+sOGuQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\3R5bDkQyG2TV7Ae+pElZS8T5yOdB4OPYyISMuhwj5VrWbq9C+1Ze6Dlda7giHp0MjzwzZDOrieA0deX9MNxhKQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\rbP8hzF22hq1LC67qEZjQJ/zpTy8lC5UiKVvUuNQuNpDp1lZ1qAeSRNIVtmuYf0waNqxcpJgjl6TPV7Yviu4mA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\5oE2zWlMXT5Wh1G+yTXI0fmKKXdFQB3Yt7jm00KSsdj0mj3mDmsShMMcGaq9+8S+xEL1VyLzakxmp+LUKZ9svQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Q7C/6FtBqfqxBDgquat9juJyMK+1fa3hU4tsqGzovNMnitGyd0hGMH8kjHt0X2YEvhPZh83Gc91Oqc/kcu/U5Q==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\JPLQkyVY2cmzM+ypf2qwieO3y0rr0bEUsJ05VV9xLFNSugPyXnb6dAU6E1OFA3tAU2+w2/MIYkBnQRYCKDKzTA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\4S/AKzCHAq9ak/ekP1RJqukzcnsu6gCLV+7O6yBUgCK554uBlwzrlbHMlm6T0NHS5jnugsOJswCD5jE17GoocA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uhKHddj2VK8EXhS6EXX12ZKnpae8+OS+apj1b2XUUzl9oWh7BeGHThjduiDQkrED78Y8ybAqJG3SRPKi8UVo3Q==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\cPoDXT719zMiNBZlhaadjYTzopzOlXA0gpYJU8Wak0GBfceBaB23teTlC6bIqCDhDo8eJfN9hAeXe3ofKmuBHA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\FLoHMX6nCBSflJOf8SRPZ0nnqgPQ76jZmWmcD7UFIc41Jpy2GDSX2O71T+iw+X0NBHBORAqwBmHcFADXCMgNdA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\WAcnqYvx9LJCBurqbvO1f+fQoBhTM9i0c15YTHuHGiVv33VwZC0WTCCuKokngEDPR0/VCvG9rQ+67WtfLkAsfQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\UoFUOBelqpeswaM95OoXjJ0h/rPLzu9MBHMM19xUjAexQ/luRjdPQgTa4Huaq+qJx/O57i4O18G+baxHWz3bVA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\WKd1A4k9SunHNVeOjDTN16LlS6NpQuQTeE92tQBNp7SlOzvp+U3oyydtiuVMeinVHpxzy/rVD9YuT7UuVQoWqg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\+zY+CpTiWomTFYR5RKNjsD/jA1XeHuUCUzt1uS8VYWb3sqjj57uveI8FCGNoQjzjRo/uRsQ7tlsH6A7re0MeCg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\FUqk1EpQRVemkC2I6WFz6WbmkPNHNCv4LEN0suK/BkghU+r85CQqiK8qfJzhy2mLluR5v79xbKESEjU3ft+LzQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\DOY4mqbLKmJddIgZAFa7XC6xA5CgoR+qbMBnqVbXe0nXhATQvTquCd20RkPEdTZnhIwGzuwfKTYEEZ4BPUU9gQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\z21ae9N4XDQoCrm9izMHhaqymOeO5QqAw6zWNOf6WQD7JeIARTL/4Wb61/e4ounjgmVSTiTiYDMtkL2DFL0EYQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\krq1JIHHxOFTtSDBD+/Pp/oCwdoZTagXVmPALa1sWbcnT41tk+Z8ArMxPhmziApHuZdN5rjRrh0yx/ssGFEcxA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\DbGP6YceFIY41xkGyU7Hy6niqAM1cAB/Y7UXdOzoLILQ0/j8a29vEOfTgHDOblqPiNxTZr3+TvvryNb5jguRng==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\v87vfugI4D97WGW81+bKWQotlJRsKm9hE37MvSIppmfij6+kirlnOn7PnzgGQIDEnNQingw8mPXt1xsI00SwcA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\/uapHvgrqCMdUW39sP40ZhkWHsxFHTak8zAClTiwJ0WI83KrOPliq0gbPAFifvmHg8Bz7KH2uuxYloCCVNlYKw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Sw1TfXrnVyqp8yrN1VnrJGo34qI3QPlgob7sODHCx6/Zl3ouA2fIApzHSJmXaC8BRnBTSzAoD5Tyxto77ppvGQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\4V3ekfxiqXwOuRd3wPp9N9+p4cmN4xvGyH1SFXLORyrSsULokSlOQ6PD2SYhg5hIxJ6Zch8xKhMj2o1DmN5nJg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\qFr3OwS38t4vOxII2k3Znq7CzHZO9Ywv+rbwdEbdJWPLJsYrn5v4d/n9lIYfU/Fx8a5RVOtuI/9BOyN0yiHM6A==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\MrvcG8Fw1jaWhV+oRmlUXDv1bGxcB9MWNqXzm7x7edtKqC8z4A6joj40iY7lQ4ltNv4WdafGmquDmJNLPT/9vw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\ZHUl87v8Eq8uROiGZVxxEhZ3wdYlOlZN4q2hCoBBMeoB7Q/537pvWjrRbK0s34dq0CTU6TUXWPeA5s0AtWfSRw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\/esXvxNOwfF+ykW9As/SoQqultRa5mD9ohPYmUIiRi1HMj9Gzc3jh/yJ0RlTp4m47cL96hyozT8gMQBwoYJd0A==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\tv+a8iveehOoUokmFH04TAzoTh2DLBc10WZkVrEL1O1lNhZ8ZZttjhSD3z0JY+E9cfriD+9sOJ1vEspK++xi5g==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\HXsDe2aFAmrSZaOufmkGxIvFYq0FvZ3UOZWVb6QHFK3C0ChF8loORsYDoLQPN5Tx+V79wxgrtTVdjbtfS8IsdQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\vtZoIlSlwbVUN8EfqlEIxK51Y870g6g8viYsXXkNjgD67/XzhhJrkoQkugVCEMUxGBxPjSuAKCejzzUzMofW6Q==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\tInHSBETc/DfD5kb3yR8E5oBTwJfh7Krds/YyZoCHI+fUl75oV+ex2TIdKC3E9SPk6n485hdkTFVtYnj+uil0g==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\riz59ciRBLaamnrATesMdFFmwuX9OxrTdfqcPvVp4dhJj89Q8mISZf5cWLQqcpJFSuMI+sd2CoTOLY7wjmwstA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\hfyDzlGQWuVuv9TBrdRSmtVSqeW1wvPU1lu/iuGCvxY2e5Vlrd3qxZRvGZ17wlNRfwKW+EWb66dxW4iXJvLSHg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\njjht9UkJibOvreXaW3JyJElvgzM0wyl+JdtalwHriLZnTbkFA8ne4olZGsEHd+e4GeebNTaXyKa0z0sf/BlxA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\YDEtvcSnwKPATK9nzTcqZ+5C2zSqS92PjLzigfAh5QqTWYXR4MLuEDAyAdfjrQbc04v4Rp9ZhoLbETCg0rCAZA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\u9Ipaky0unaHFPfsZtGOjFKYoGbuEylqtbiiZm1SRsGWFrhToI58k7nACZOd41gCEoTClKXESP76Ay15vKDdHg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\plBiRGaiqvoSmIIBVYizbAga12e8S/t3BBxpNRDsytfZynX1LfkyDywjlGydfBAQM1pyLBfSWbIAw4iOfFQSrA==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\M0XMSTEg2EiRD12Ttk9bUU5Jl5ro0EZtX7DR7M+cfT8K1B/R3uMoqCM9gq94JQu/6fh//8h+/Bapo+pLQtjJEw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\mvpLCkyUNUgI/ib1/Az9c0sJX2OYCpYo/n2bymRUUgyc/4QeaNa+2LKaT9Ksx4zDKqdHa8H6Rv3laj0HdSXbBg==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\hN1QLRdgrfcpJY1nBSi3RE8jw9JDCLuV/RCqTqtAw0eAxG8kQlFWMrCLA7cPi2+so7ZEnW4aaUJt+L7M8gYSYw==\\ OR " +
                    "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\NZZ0GEeA7u1UMaZcjXYkwuaknBW8wvyojQ7D+CSVj6ef/2k+X1MihVM3CDMCZwvpNe10qpofhtvaSlIPY/3rRA==\\";

            case 111: // use-case 1 medco-normal encrypted -- 10 nodes
                return
                        // skin -> MEDCO_ENC:1	; cut melanoma: MEDCO_ENC:2
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\dJWUn8IJi2DJ5eYxdr82wLsXuxLd4tRv93Nw2Piu4X6LuSsIq0XQ5dOpOvStyvzxDitBmUhepno45yNYEQd+Ow==\\" +
                        " AND " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\CGsUVuAvWipLis00vna97BRtJ4r0TT0U3Mu3RgALGmoQTENbDHlGY4UgRkiDjSAINyjw2Mw/48InKf71nTshmA==\\" +
                        " AND " +

                        // variants ids for [Hugo_Symbol=BRAF AND Protein_Position=600/766]
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\zAV8bTevHSOfvO5F4fE0iMZhyeVbX6YiLBncafB5sWWpSv5LPx0EYHC44jTnwF2sxGAMVoXieT4omqCb+9YYEA==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\87Q2axk2L89m8hL64/7mSw07H4EVuC1eherFZRVv6IGjoCwiAqfQ0h1QDyhCIO4XO93bnSj2WjT4wgr8Uv2cNA==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\37RmaqcFFYPCWKKpiN7A5J8bBi+OpzMJe2rAudzrw9NOzLckv+PsB8gtJGwJ35qdReOcXViStf0r1UAq27i6Rg==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\QybSoEXh67GRdNSm03r2ajetO2y8Ui0PlXtpkdYpiLmRAAH8MJhjcieGrbEc7uyTpnyATv17LA0gFtSYJuu9wg==\\";

            case 121: // use-case 1 medco-normal encrypted -- 9 nodes
                return
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Pjma6l7y9rocVQPnMMDXaYqnYpbVK1bBNRpiQeh9EOPm3zeWkQ28poTrkN+whQlg6iFn7YQCk8R6QoNFqt89WQ==\\" +
                        " AND " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\wctPB0cCnCXhQ65NbWjVoh9ovtPXkbHvWnwKRJ5AUXmdrZnOSMJxzD/S5ACPZRa8VjTlgtJRB36pZHsh6M1j4w==\\" +
                        " AND " +

                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Hz6NctbmWWpJh69UG9n8KvVxjXnaC2IGPt5bDOOMTWQBtb6ZrWZBBpMg+8rxcJMCps8qYFJZkk5/M8tZv98wJA==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\VsylQv5kHugpcRQl0jGPkM2HY07rtntwIsII19twG5Nj+nLdJCIBqI1oegugWrNoPehWOU37kSy9DlIHtof8vg==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\Y7a1hHxcPvee+p1AmKsosmUHlrDKMr8KD3Bex7Xm2M/FLGDIXuiomkSMlZD16Mu2Qzv61YuEW5ZX9PjISiC8gQ==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\wDR0uOCA1zSu/sTkiDBd8whh005juU0hvZ7SVoGeOOkX2He9+iKodhj7s2/6uiVVcOAFTuAQR+jDki0M1sPH7Q==\\";

            case 131: //use-case 1 medco-normal encrypted -- 6 nodes
                return
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\N5A8mzhL8RMTtNTNZnkHIAgqxCK2DPoUqg+nbYWFBaZVoWex4a8TIlNFMVctrWFN10lVeo7u1vC1Duvls1H0MA==\\" +
                        " AND " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\3GzXHEmwnqtxdRP+A52E+knJFtc2OiHZhe4BiZWvJ6SJttrsvAwBbeoaTmUU6lIgkyG6K1NHzjjQ8yZHI+7sAA==\\" +
                        " AND " +

                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\v6TCwh1okevHKd73WvCDyTbJeT1Z3GiiyB3LPoe86che9pl2McTS5s/jSAUR9nN1ZeqN50EpGKLHVEikldtS4g==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\qxyNUzDp2JSeYCPlbLTyc1B4WanqdZQ5BIictOEeXtQooJl8RosP6t11SVU1pbK8PzTD5Nk7SkvkuOtacI+G2A==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\zhKlBduQO4DtUee7HLHLs2Nd1NkRbEv872Ucm+93C2PthsEUCfyFpVoCZ1QDWm5gZTJaQKW1HTRYQKk3/YovwA==\\ OR " +
                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\jwP0jM3HMzEz9u+WlVJVwp8afMxUhWwn8vWXPHgx8oW4igbdey/uFb4dNsjXstPpp0K1T0hhNbD7n5HKP9YAAw==\\";

            case 1105: // 5 query terms
                StringWriter sw1105 = new StringWriter();
                for (int i = 0 ; i < 4 ; i++) {
                    sw1105.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\ OR ");
                }
                sw1105.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\");
                return sw1105.toString();

            case 1110: // 10 query terms
                StringWriter sw11010 = new StringWriter();
                for (int i = 0 ; i < 9 ; i++) {
                    sw11010.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\ OR ");
                }
                sw11010.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\");
                return sw11010.toString();

            case 11100: // 100 query terms
                StringWriter sw110100 = new StringWriter();
                for (int i = 0 ; i < 99 ; i++) {
                    sw110100.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\ OR ");
                }
                sw110100.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\");
                return sw110100.toString();

            case 11500: // 500 query terms
                StringWriter sw11500 = new StringWriter();
                for (int i = 0 ; i < 499 ; i++) {
                    sw11500.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\ OR ");
                }
                sw11500.append("\\\\SENSITIVE_TAGGED\\medco\\encrypted\\uf3fFf4TDrCvWVdD4KAVWDRFGXHMNT5QqY4tkMXjzITEvXKO49tfPIrXrL6YrNJdYQhDKsK1h8EiQ6HQlBwEnQ==\\");
                return sw11500.toString();

            case 1: // use-case 1 encrypted encrypted
                return
                        // clear query terms to retrieve all dataset
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +

                        // skin -> MEDCO_ENC:4	; cut melanoma: MEDCO_ENC:6
                        "MEDCO_ENC:jGKcRJrrJSnDmCprGdelcW/rmD8YuhK8C1E/GDTkkboLzZgA6FSuyL+LkZi1Is0wXmPrA5UAhjJ8DwPBKA6ZeA==" +
                        " AND " +
                        "MEDCO_ENC:vs+rMPIpUv3v998nHZPfuhNo7RfD6a5WJH2VIg8ETZLnap1k5B5oCrV7tZelOUK2RmtpBEk9GvJSKBKo4hSZ/A==" +
                        " AND " +

                        // variants ids for [Hugo_Symbol=BRAF AND Protein_Position=600/766]
                        "MEDCO_GEN:ZnMBnoLz8TfFfSoZyV0pvz2rIkbaVMauxnGkZBsUMEUDz83Q4gWV5oxdU18ubelJfaqFlX7e6yqf7Eo7dfnQaw== OR " +
                        "MEDCO_GEN:0TprAeIFodXk9jJN+BC4+MzAP+XRsv3BflcrgO5b3WBj81ajoLxyHmuPP05MTUmve2A0DS4/hKvOKb76rjTiMA== OR " +
                        "MEDCO_GEN:3si3xWkTxIv7TnIdPVUG7GBDZXtGQSrsR8DC7ND/aTpCEaE/QPaW5qykPFreFDVF5hzGGBFbYKrosM3z0jQwew== OR " +
                        "MEDCO_GEN:QvT5VI9Jk9u3kMIZDEp+Yzl5TzEEIgQ1mWjJn9kQMlP+d/tLI0nkjaq7fBSBnP3h45rsijtD1IzqTT5fPBc50A==";

            case 2: // use-case 2 encrypted encrypted
                return
                        // clear query terms to retrieve all dataset
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +

                        // skin -> MEDCO_ENC:4	; cut melanoma: MEDCO_ENC:6
                        "MEDCO_ENC:a23H2wyfoA30NJXIcSybAotf6b+ApUo2v0YJpqHz21ZjOpaswVvQgqfJL105BDExHWuQlkPFEuQ3lvgYALryeg==" +
                        " AND " +
                        "MEDCO_ENC:xe0zFWnw5x1VmMfltC+QOU09Ecnm35V8QS8/kDSISimdi4LQ8o1ZNh1Ic4W2rH2e8aIkCMZJtzTR7GxyreGIAg==" +
                        " AND " +

                        // variants ids for [Hugo_Symbol=BRAF] UPDATE ME
                        "MEDCO_GEN:D44NedbMM08x5dc2r1DzyT1rAd4JkLW3d1S5RVs746pm28W/vemwlABDAtuCp4gevhIzMqnmh18vMYhHzr1zxw== OR " +
                        "MEDCO_GEN:cdKFuPfwe5TgRc1UNesh2lrkhpkDlJH2/zRqIR0KvLnirK5k3gPANs+5coCAmyu1bz9v9V8K4akLbnIQ0kzwvg== OR " +
                        "MEDCO_GEN:LwMtilcXN9GUVqV+QClI901NJZIXPe+nQov7CiN0yLBmU1Fy2zZ5zsN4g/0srGnMT1IZxE4OmNcMA6P4S3FLVg== OR " +
                        "MEDCO_GEN:ZXxNMgjtIZT7nDAnGYyw9oZRKSROn/q+k3n2c32QJnr+/SymYMQJkfNpp05BpIsyigV55J16LWOarrwittCRQQ== OR " +
                        "MEDCO_GEN:hnPltOs9qCGLSzDamuM49IU+Zu8JvOxQ8Z9m3bBH8yNb63z6TYbzL0C/Xm+2bNLHs6gR1tf9pJpYMbWJmfoQ0g== OR " +
                        "MEDCO_GEN:uHoRSX2jTv+KkOymknNgCk+IHjyJNfGJSEjpSHYuLW5+fSTYZKznwnx4HgjmOzI+p5zcUg1sIkty3SJXGZB/HQ== OR " +
                        "MEDCO_GEN:pxYjFNgUMXwZfpnnaxmZHQ3ImXYyJ5HmkD4ipNW2Psph6r2AOYZWZAQ90vSENaOKB67pXd8rmPizUaf32cy+Og== OR " +
                        "MEDCO_GEN:f5coQKWJIWwNsb+/D/LF1PyNh4doqsbwyRgKbAX23K0VpDr7BQQlo+NDhm7TC7RSauz2m1r9DzGdMgGoREQyfg== OR " +
                        "MEDCO_GEN:aOgmh0MjCaw4unBcB3y3bS6BdLxPiiY5S2CChGkWn49yxUm8jHZtOChWaYHmkgOYfK8sNlULVEL+7hq98g+u+g== OR " +
                        "MEDCO_GEN:EjMWyWOEt6dcGpqBHNLPokJHTDwVGlmLLCNJrIvhnX86KEbmbV1hL2H4MxBRan+aKS1/CNY+z7+VSGrHj35dvA== OR " +
                        "MEDCO_GEN:p/xNDZxug7nZ3DendKeaIPYfH64Y7OF25hM83l64Wz6v+4iNXg39ZjOIAZJCp21SJcZjFzQMdf5bxaWy9Z5zxw== OR " +
                        "MEDCO_GEN:wdEJNZOFEM51BKYyTs6SOJCMCvCu6wV9WxYRkAfPq4tCGCklQdiT+qWMClFDlcn9gIMm1VchPQCWlIRDqob8/g== OR " +
                        "MEDCO_GEN:JhS0oP28wbDiW/w9PQ1ZFC0eLiHJHx6n16fIWFHzdD8AzeYjs9xDvbRkR77sTakF4qUjb9H560fZWhAp42miyQ== OR " +
                        "MEDCO_GEN:DCmwF6wL93kvgAwI1CH4YD9p25DPkiOFDgB+Wz1CdrFlqFLYLPtiHUjCgRNdGmRZ6TG8vIcqR87IcHtIxfoOTQ== OR " +
                        "MEDCO_GEN:s37uFhzZDq9Z+9qKQu3SC17jIzMtLRhq3OaJlrvZwrfscdJNR+ddQWsLINWTnamBID7S4P4ziJsrn3ghx+TMnA== OR " +
                        "MEDCO_GEN:e3YQ/riWgdK54UDKyWystehgF4ln59lT+wecIQinA0qS1dpnkfoqAYIVx3yIp9S4Wb7M24GcAwimLTuu+RxNaw== OR " +
                        "MEDCO_GEN:LTUxRns5SeEqruXDhIVhk8T4U7AyDtEmMiLbJnJ81FvfkeWHBg5KUYvbBuaGTzf4ndZQ7W7OH8rRB3vibZDeMw== OR " +
                        "MEDCO_GEN:hxhUEb80v7qeWjCGT5rCZ1DYCP8y89bX13lVhifDNOdi/d/VsB+qu93y0fQJEVFk5VCU6he5jXkEZLoef5L3uw== OR " +
                        "MEDCO_GEN:dYrlZfdk8wlJqxsB32IoyPdjwXqkbsi3Co48GlBwS795J62+BqD1xXVLuiOylGnnjtxiMtfPP18TjIVZuFF3aA== OR " +
                        "MEDCO_GEN:hF2A4LW4nL521qn10yNObIEqvFYinm3XyoFRDXSkKSWgi9eHykdVywm461FecDZZC/3f1Y+pFZ++1PHRB1Qftw== OR " +
                        "MEDCO_GEN:2QXAN9BRmiOL0q2eRo1JbqMQSXMNGnOFqt8mgrCuPXBJn2C/aVx1UAMBF2DboRDIfdx7YjKC8mbjUKvexzGYow== OR " +
                        "MEDCO_GEN:n0hJayzg6aQqSyeUf85WHjG5AlKbIqeu/VWQFVvHLNbK5Svq/Q6duaP3Wn/9iwBTWjA/nrPz0vXdOzIQToV0GQ== OR " +
                        "MEDCO_GEN:rk7d9KnFyY3+y8t3C54yApN2T4MMwtEGEhydLV9kALwbw6XvGsSlM/B52GzVUuSkwjuYVg5wMxFXcKXk99NUSw== OR " +
                        "MEDCO_GEN:rccYa/K6+AC6tJ8y+Py+aX4TJRBx0eKVWQhvGQEhLfdINqjRxkAFG6QbIBifCSqFWAKr1eI2QOs286SdFX/btA== OR " +
                        "MEDCO_GEN:hUkpNGCHdMjE5/NLRl6QvJJLCQQ7PV4epnKPYbko4fgi/NyR27cmAt/oIy19oCOqp3hnxe1aHZXVn40Idq7+sA==" +
                        " AND " +
                        "MEDCO_GEN:hsI74szdwDidAOwHJiTy8HHBozqmuij52L+veJ+IhqyO2wwRBNRUQxxzH4VyEUPOxJ0Zg0enQwt+0/oX4X1cAA== OR " +
                        "MEDCO_GEN:bYC1en7ce/yDbFK0NEXcINxscePn3tzOop0Ntzue1LZpR0RM7OKi46AUiZS8lT0pJBobyowuqsXg1EciXyS9Wg== OR " +
                        "MEDCO_GEN:WD3hdnirdqqqMVzKI+XarDB5BgM5lzyYGoGogagghI1/LIHLTbw3uxNrGb+3d9Udj0lWXl3b8xlWuJesTcV9nA== OR " +
                        "MEDCO_GEN:eRX3hSgsitWpwCa29/3u1nso+NyE20HwisNRmaFNAGp3AFfr4BjJMVJ1jZ9U6s8f22a8Vesub7XvXL76shmbIg== OR " +
                        "MEDCO_GEN:CSeUe8thcSVS6SZt15C5D81VArJ7UNBIvOhsx9Ex2kJ4cwEOYzOnexTAKw4i8zwkz+xXAtSzIyH/KQfqzfwDvQ== OR " +
                        "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                        "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                        "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR " +
                        "MEDCO_GEN:HjY+NM9pr5ch6dUmmwJRMzEMFThtgT8nt91A98KDMm+lG10qILx7YcYFFMUwutQJQ9GVoFDRnd09GwynpIGB2A== OR " +
                        "MEDCO_GEN:E3hzRYM/x12Ed89lI0TT5fQUo6xseTTp4Pu3++Nd+n3H2zsWlnqg6dpNSMZtud/YtzVgCQ7zRZWfUzZYU+u5jw== OR " +
                        "MEDCO_GEN:uYMtnHnG+oc9hrGRuRwzDyX58ONnG1mCRfH7+suvw7DWQ4hSwxLSBk/2DjNMd57vYIFU224xw2cjbxx1kyOPbw== OR " +
                        "MEDCO_GEN:+sNzExEMRWy7DI8c6aUzExs+H2qv5MXqCHAPcOf9DGrpTX1c5L8e5buyGJG2tIf0rQWnsDNMwRDpJc7Gpip/PA== OR " +
                        "MEDCO_GEN:c9vzOssmq5msrUbWHU/zk2gtjDRPowUra2NNlrKbqbyV5N23eTR/gUoVzk674bgsiO6zL5fAWZiDJbD3NQ3CJA== OR " +
                        "MEDCO_GEN:Qu+iZEppFtco3VtrOGC3dkxpNh57bTRdA8IQ/q/9YXScXXCVu5F9yqbz3xnk041T1OGFR4cPZBEN/pNAtMgzKw== OR " +
                        "MEDCO_GEN:HFOBzYw3aPS1q804wKKioQNwCyRVl7MGu0dHGe6WdNhhsujWF5yYy9Db1Cmi3xfqeOGfEJcVbrq5+7eok5rxLw== OR " +
                        "MEDCO_GEN:hntXZLRcTCX1daL9N73eINjIxrVJyNPSgVAPuZc2ts/w1G81PkpR09d6YRriX7pJdlXXx+gtIJb/DS7moyHcqQ== OR " +
                        "MEDCO_GEN:KHveMaISTYiYTdNSPIOMafz7Ddk1kd7vtyICgvjcaFQwF16SUxUcxCsmE9ylSjaNiCmec0TOR5NOINnEHGm+3Q== OR " +
                        "MEDCO_GEN:adxS2akG/2X93CbnPv0ifMnhjv/gAFKTD0sRhXe/wdmukPiJltlxZm9Gq5po/J6LopxNCPXb//tePx+WbCGq1w== OR " +
                        "MEDCO_GEN:Y5Q9i2RWtx/xUAN8Mat4Gvv7vFxgxjJHuiPkPtYaM+iK7d209jFxNSQU2YpiQmK2fk23w/mymR+QhsMy1fIxFw== OR " +
                        "MEDCO_GEN:/8lX/KIhRuXZ4mtZh+/4Cmgmat+PbEPP93fxs1OCOa1dVlKZZ7Yji/E2bAXzFhhXUYaQidAq0LTR9XP6RuAA/g== OR " +
                        "MEDCO_GEN:QvJbptudB9Y6GVL72gv2LNVmP/AfxEzens6o+Kv4hPDnlaSx6rw2N/Zb0UNB5lIAqjVVnlvIIX9ScqMlLy7H6Q== OR " +
                        "MEDCO_GEN:cMrETOs1qwdQ/ayz+jVWbryQLDJbcJMymDo/fHq/Kyw96XsBqdWuTDN98a9czLhxKnZqWQKaEvQsItlEk3vtsA== OR " +
                        "MEDCO_GEN:nIBSBZq6+RMVOF1/azmILuiyP5yXCoiwB0hbnu0+RXbAtKMXBbufoSggtw8BUOSmEAkkicpScMjw04RUkBTbpA== OR " +
                        "MEDCO_GEN:9Fb9EuGOgJuXjEMZtOreJdQW90U7wS6P07TC5h+LQis34WIqZVTqYB4B+p112GS3I58hIUqCa9k3G8PjeiRZTg== OR " +
                        "MEDCO_GEN:xnOouQ/1cwcIYkkqdq80zHBmG/pOnk7dT9fYWWxCkVDsLDbjslQp+Dk//KNEBgC+7YPXEEY90LxpUdDXXzHG4g== OR " +
                        "MEDCO_GEN:N0bGEVQAVb/AM8R/XKhz52Rlfe0U41DQBtqgTo9/VHp/jNwOjVI9G5Cw+31HgQv/Di+Tz38XXaBjirC3eRbWWA== OR " +
                        "MEDCO_GEN:T9sTYaf1NmbXRKIc+Zi72ESQzlKSDy481B4hq4WtpQ5NYmNr0E0k/ePaLzye8BBJxHFy6bY+aSEWyukJgJxf5Q== OR " +
                        "MEDCO_GEN:gAE6wiUw4cxTo88dVnfJuO6vhUsVBU7TyE5Vvnajz9uJAE2PwmY9GyalVuOvKtDb49VRLS6x205+PFOvDXJsag== OR " +
                        "MEDCO_GEN:pVAuBBBJqNsHYetWBcjQa5x/ZISP+LTSu3lQConTCh3Jt/PohQnlkLe/Kr+xCDWCGVo/jfgz61ouXcj6ixgZsQ== OR " +
                        "MEDCO_GEN:uESdfo/ULrcav5efi4Pq/WV0wM9/xKaAGHm2Oui4v3xcFT5ilUYvtwizmKCq+rdVwE5pvA4Fso3DJt8hZzBSuA== OR " +
                        "MEDCO_GEN:tr8zvS4UC9uzSv6YmonwE6dUJifZAQzurPr/Bpwyc2+rkC8NeE8wR6SxG1WJ939vRkIAlRUdiUDwDFYbx3ZpIA== OR " +
                        "MEDCO_GEN:hMzOJZ6Yvlu/II7IvZf9dcqJvBSdvHY+I+JEQvvTc5S7zAJ9BkD+lnQPbLUASOJ6kI2pGPhkC/JXyTLSg5K0Eg== OR " +
                        "MEDCO_GEN:mOciI5Tc+eXsmNHFhxo/LXTyPN2gUeYHKcbYz3EHhuR6/Tb18WM3d/cUyJBzrBjUEyINyNX4TEP5lVxRr92+ZA== OR " +
                        "MEDCO_GEN:mN/ss8CHskOgizEjTl8RdUEhdP1w+SRHUMP9RPCMOyCO3TIPyBSUNLGNDCDGx599dNqvjVRyP/QD4JxjL/7F9Q== OR " +
                        "MEDCO_GEN:ZFjzyMBPvyS+89bZW4BMmmlb/3yVYN0OtbHKAx74J5O45dnQ2DHNEJfUIrX/1bWUfhDZ3uH1ZKaq3xt6tPDmfg== OR " +
                        "MEDCO_GEN:qyPFS8DdLNU0AvSI5VjH2O0FHrCaUAe8TPSbWiu9+aeGHuhqMHLQwiGCx7b7yII7FGsCE5ToWO7n5r2uUhOk5w== OR " +
                        "MEDCO_GEN:GssT+XY8vNmpWew/FpyAvtP9JHCUt7KyYAQxqsf2scc5XgxZ9rDePLnn05AUbdURex1wyP/03AmUIY2IAB8cZg== OR " +
                        "MEDCO_GEN:8DzmmlUEgYe69zYNrHSpRf7QBfAUjJHwv9x6H1jvifQeimZmQqIbQy9u6zTNQ84meMBr/dKQdI1UxbBtDEgLxA== OR " +
                        "MEDCO_GEN:Vj1F94YSanP6TgY7WJr/OFAA3gxLfyARagxkw0Szr4DmqCRR0a3gfczdFSDnR1BkdVkiGSkMcR+lL7YZi9yOQg== OR " +
                        "MEDCO_GEN:FNGWJv3hlyD+D/bHuT8jlrKanPYH5kAFCDvvJVSiYM2PiRzZ5YpndbNQ9nbwiCuCQEGXMqugiH06cJgkyiPoYw== OR " +
                        "MEDCO_GEN:E9WpRiE/woD+jAhPN1EfiTgyFV1edXm2Pr858JO/4WIhT7BiErYW+lfy5TEFNq0lypFISUOaZPw/jHaiqQwPqw== OR " +
                        "MEDCO_GEN:e+ToN0Z665neUv5RmPK2wQW14bD9oXqoucqtgIkHOg7GR4ULrOsQh8qnzd/IN5mmQY/YdGz1hoc5wrai4ebNaQ== OR " +
                        "MEDCO_GEN:buxxNxhfLZuIZh15RVAvCMBtXCNqPIxozOkyiOM/4Bu0FvhpnQeE8gR+uwg0qtN1OiMXHliRhKNoqcXK90eOGQ== OR " +
                        "MEDCO_GEN:sWIJpNie8wFBxvSmbelIQmCH5Ky66h92xprHFi46N83K2mgdb5TZD4lmVojj3jWTyzoUMJoq5jRzdr4NrRIwxw== OR " +
                        "MEDCO_GEN:rHumWgTO53thX0syMAPtd98clhxV2nqUgHoqOOkzqOHZg0GB6JK3EhaGD/PWyQhFyRfhXohD8ISMFUPfNe2Teg== OR " +
                        "MEDCO_GEN:TexkrOStADoEc//+tazhkXAy3WXvKmzX1mwKtsUK3YR8UHLA8IsO6XTRSZcceD2l7CeCq09q915x/TkjA7T5XQ== OR " +
                        "MEDCO_GEN:TGwWbEnnl5SKR0g53EMcSJW+Ae0jCAuHd4rH5+4ZKGO2Wt4YNCPhzVuB8TT7340eQKuPxmtW/j0C+w1dC2HJxQ== OR " +
                        "MEDCO_GEN:LHCYuvig8PYpfrzCrS8nnruWPDNLUGHHAqjC/U5L7L2mOh+b8UVXegG0tVadmddccvv0tdfHLPUys2qfvTqMSQ== OR " +
                        "MEDCO_GEN:zzKtD2/ywSQ5f28ikVIOh2Ir7yZR2UfBUY0jW5VLyXcj0VAa1WpgihNrDmSWqeaRvOk4Csvj61CfgJ6AQmrqEQ== OR " +
                        "MEDCO_GEN:MEBPXmSKjcnwkNYgywNQVMMmWnrysh2v5Ig1h/KCPZgAOQfpjVsaAqOHTrGZ6OL/Fxf95lAGubfhtdVB51qclA== OR " +
                        "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                        "MEDCO_GEN:qM8KzlNN+oA+TfqShe5oqaDuHphZfw/n6p+xr2bmsMFGA7J+Gwax0ZZcFgyEIiL3FrfMPNTGsz0WxJe8iLhTCQ==";

            case 3:
                return  // one genomic term test
                        // clear query terms to retrieve all dataset
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +
                        "MEDCO_GEN:k2EKXqzZ9c672FIoD/2YJZt9x3NvC2I0/A056DMJYrdkF8MJURvsGhIf8KxaccuJj+ApPlLYpn2tV/gIODGDjg==";

            case 4: // use-case 1 in clear
                return "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948997223410688\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948997064022784\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948997064020736\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948998062267136\\";

            case 5: //use-case 2 in clear
                return "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054904932905773056\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054898625779855360\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054898626853597184\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054968999892742144\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054922546600210432\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054904954414166016\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054923379857424384\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054923381098933248\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054918645662608384\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054905185268658176\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054923607457132544\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054861517262417920\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948987408734208\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948997223410688\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948997064022784\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948997064020736\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948998062267136\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948999337337856\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054861823278837760\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054953138544961536\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054904773018905600\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054917142457610240\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054949048695910400\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054861692282335232\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054948050082458624\\" +
                        " AND " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244731450050998272\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244761148709661696\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828257969198066688\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742591331743035392\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244734719527742464\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244761165822422016\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828266574613577728\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244734599302213632\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244734615609652992\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828258079793473536\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742581279372079104\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828214681296432128\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742593391179853824\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244731379150482432\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707619996790784\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707607111888896\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707450345581568\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707451419324416\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742593229011282944\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707438500867072\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742581136564416512\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828266483970731008\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742593253707344896\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828266501150601216\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828266502224342016\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707399913269248\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707503026037760\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707487054123520\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707493295259648\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244803225329855488\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244835122005142528\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-3742595392634613760\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828266596713621504\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707304132149248\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244761290443582464\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-4828218249340513280\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244761261385445376\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707269956960256\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244761257090477056\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244761331212216320\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703514008055808\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707363372498944\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560164864\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560165888\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519225765120\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519376764928\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707347467692032\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707351594886144\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340823916544\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340672917504\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244803244478955520\\";

            case 6: // i2b2 clear: 5 attributes
                return "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054904932905773056\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054898625779855360\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054898626853597184\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054968999892742144\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-7054922546600210432\\";

            case 7: // i2b2 clear:10 attributes
                return  "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560164864\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560165888\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519225765120\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519376764928\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707347467692032\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707351594886144\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340823916544\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340672917504\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6244803244478955520\\";

            case 8: // i2b2 clear: 100 attributes
                StringWriter sw = new StringWriter();
                for (int i = 0 ; i < 10 ; i++) {
                    sw.append(
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560164864\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560165888\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519225765120\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519376764928\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707347467692032\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707351594886144\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340823916544\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340672917504\\ OR ");
                }
                sw.append(
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560164864\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560165888\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519225765120\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519376764928\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707347467692032\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707351594886144\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340823916544\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340672917504\\");
                return sw.toString();

            case 9: // i2b2 clear: 500 attributes
                StringWriter sw2 = new StringWriter();
                for (int i = 0 ; i < 55 ; i++) {
                    sw2.append(
                            "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560164864\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707366560165888\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519225765120\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605703519376764928\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707347467692032\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707351594886144\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340823916544\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340672917504\\ OR ");
                }
                sw2.append(
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707347467692032\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707351594886144\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340823916544\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707339750174720\\ OR " +
                                "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\-6605707340672917504\\");
                return sw2.toString();

            case 10: // medco: 5 attributes
                return  "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +
                        "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                        "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                        "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                        "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR " +
                        "MEDCO_GEN:HjY+NM9pr5ch6dUmmwJRMzEMFThtgT8nt91A98KDMm+lG10qILx7YcYFFMUwutQJQ9GVoFDRnd09GwynpIGB2A==";

            case 11: // medco:10 attributes
                return  "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +
                        "MEDCO_GEN:hsI74szdwDidAOwHJiTy8HHBozqmuij52L+veJ+IhqyO2wwRBNRUQxxzH4VyEUPOxJ0Zg0enQwt+0/oX4X1cAA== OR " +
                        "MEDCO_GEN:bYC1en7ce/yDbFK0NEXcINxscePn3tzOop0Ntzue1LZpR0RM7OKi46AUiZS8lT0pJBobyowuqsXg1EciXyS9Wg== OR " +
                        "MEDCO_GEN:WD3hdnirdqqqMVzKI+XarDB5BgM5lzyYGoGogagghI1/LIHLTbw3uxNrGb+3d9Udj0lWXl3b8xlWuJesTcV9nA== OR " +
                        "MEDCO_GEN:eRX3hSgsitWpwCa29/3u1nso+NyE20HwisNRmaFNAGp3AFfr4BjJMVJ1jZ9U6s8f22a8Vesub7XvXL76shmbIg== OR " +
                        "MEDCO_GEN:CSeUe8thcSVS6SZt15C5D81VArJ7UNBIvOhsx9Ex2kJ4cwEOYzOnexTAKw4i8zwkz+xXAtSzIyH/KQfqzfwDvQ== OR " +
                        "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                        "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                        "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                        "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR " +
                        "MEDCO_GEN:HjY+NM9pr5ch6dUmmwJRMzEMFThtgT8nt91A98KDMm+lG10qILx7YcYFFMUwutQJQ9GVoFDRnd09GwynpIGB2A==";

            case 12: // medco: 100 attributes
                StringWriter sw3 = new StringWriter();
            sw3.append("\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                    " AND ");
                for (int i = 0 ; i < 10 ; i++) {
                    sw3.append(
                            "MEDCO_GEN:hsI74szdwDidAOwHJiTy8HHBozqmuij52L+veJ+IhqyO2wwRBNRUQxxzH4VyEUPOxJ0Zg0enQwt+0/oX4X1cAA== OR " +
                                    "MEDCO_GEN:bYC1en7ce/yDbFK0NEXcINxscePn3tzOop0Ntzue1LZpR0RM7OKi46AUiZS8lT0pJBobyowuqsXg1EciXyS9Wg== OR " +
                                    "MEDCO_GEN:WD3hdnirdqqqMVzKI+XarDB5BgM5lzyYGoGogagghI1/LIHLTbw3uxNrGb+3d9Udj0lWXl3b8xlWuJesTcV9nA== OR " +
                                    "MEDCO_GEN:eRX3hSgsitWpwCa29/3u1nso+NyE20HwisNRmaFNAGp3AFfr4BjJMVJ1jZ9U6s8f22a8Vesub7XvXL76shmbIg== OR " +
                                    "MEDCO_GEN:CSeUe8thcSVS6SZt15C5D81VArJ7UNBIvOhsx9Ex2kJ4cwEOYzOnexTAKw4i8zwkz+xXAtSzIyH/KQfqzfwDvQ== OR " +
                                    "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                                    "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                                    "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                                    "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR ");
                }
                sw3.append(
                        "MEDCO_GEN:hsI74szdwDidAOwHJiTy8HHBozqmuij52L+veJ+IhqyO2wwRBNRUQxxzH4VyEUPOxJ0Zg0enQwt+0/oX4X1cAA== OR " +
                                "MEDCO_GEN:bYC1en7ce/yDbFK0NEXcINxscePn3tzOop0Ntzue1LZpR0RM7OKi46AUiZS8lT0pJBobyowuqsXg1EciXyS9Wg== OR " +
                                "MEDCO_GEN:WD3hdnirdqqqMVzKI+XarDB5BgM5lzyYGoGogagghI1/LIHLTbw3uxNrGb+3d9Udj0lWXl3b8xlWuJesTcV9nA== OR " +
                                "MEDCO_GEN:eRX3hSgsitWpwCa29/3u1nso+NyE20HwisNRmaFNAGp3AFfr4BjJMVJ1jZ9U6s8f22a8Vesub7XvXL76shmbIg== OR " +
                                "MEDCO_GEN:CSeUe8thcSVS6SZt15C5D81VArJ7UNBIvOhsx9Ex2kJ4cwEOYzOnexTAKw4i8zwkz+xXAtSzIyH/KQfqzfwDvQ== OR " +
                                "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                                "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                                "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                                "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR " +
                                "MEDCO_GEN:HjY+NM9pr5ch6dUmmwJRMzEMFThtgT8nt91A98KDMm+lG10qILx7YcYFFMUwutQJQ9GVoFDRnd09GwynpIGB2A==");
                return sw3.toString();

            case 13: // medco: 500 attributes
                StringWriter sw4 = new StringWriter();
            sw4.append("\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                    "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                    " AND ");
                for (int i = 0 ; i < 55 ; i++) {
                    sw4.append(
                            "MEDCO_GEN:hsI74szdwDidAOwHJiTy8HHBozqmuij52L+veJ+IhqyO2wwRBNRUQxxzH4VyEUPOxJ0Zg0enQwt+0/oX4X1cAA== OR " +
                                    "MEDCO_GEN:bYC1en7ce/yDbFK0NEXcINxscePn3tzOop0Ntzue1LZpR0RM7OKi46AUiZS8lT0pJBobyowuqsXg1EciXyS9Wg== OR " +
                                    "MEDCO_GEN:WD3hdnirdqqqMVzKI+XarDB5BgM5lzyYGoGogagghI1/LIHLTbw3uxNrGb+3d9Udj0lWXl3b8xlWuJesTcV9nA== OR " +
                                    "MEDCO_GEN:eRX3hSgsitWpwCa29/3u1nso+NyE20HwisNRmaFNAGp3AFfr4BjJMVJ1jZ9U6s8f22a8Vesub7XvXL76shmbIg== OR " +
                                    "MEDCO_GEN:CSeUe8thcSVS6SZt15C5D81VArJ7UNBIvOhsx9Ex2kJ4cwEOYzOnexTAKw4i8zwkz+xXAtSzIyH/KQfqzfwDvQ== OR " +
                                    "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                                    "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                                    "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                                    "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR ");
                }
                sw4.append(
                        "MEDCO_GEN:NETvU3/IYHvBL5NOzW2h7lpi8AGlPMSsMSKUNvwnhdM9bmOmRVsd4Hpywg/tR3tLHygKBTEW5XeaM2G6l/0SfQ== OR " +
                                "MEDCO_GEN:MkcVUYjRD71jgWnu8/kfPiac+Ezmh5hL5wzP8IANdDZUs9gy9tyCVbnKVFVLOTQnaBTWZYtUnQwTfQwmOsHdgw== OR " +
                                "MEDCO_GEN:MviI3uTGsPlKw2aZsNkmHHVv9XY/ycD6p9bArjlSMvjdcbwCA620/PMtI9MrpQEXVOe6pw2auGMahdNarn/WuQ== OR " +
                                "MEDCO_GEN:NkGR6ZYbrxArjdG6REfxmo9qzN4JKew7lykKLI7VGb7wWky7dptR3eY2HeUPlK5l8JLOZ2iuX+nRHF7/X7bNEw== OR " +
                                "MEDCO_GEN:HjY+NM9pr5ch6dUmmwJRMzEMFThtgT8nt91A98KDMm+lG10qILx7YcYFFMUwutQJQ9GVoFDRnd09GwynpIGB2A==");
                return sw4.toString();

            case 14: // medco 10 servers
                return "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +
                        "MEDCO_ENC:LDPgWORjARoU1afmLsEU59o970xQspOpSb1nB49F3FuFMzZGI0siGWbYyROKDYjORurNmAQteEbYpDggANQWFg==" +
                        " AND " +
                        "MEDCO_ENC:zaIfZW3VMrSURkMmYQ3g7VZu58Of9K/XDHT3ln3UEhLrbVrM+tHpLDs4Hdpv3hQpOzThrlomyh6Hg68HnduxQg==" +
                        " AND " +
                        "MEDCO_GEN:36qhWlgIKzLgR+bW+bQadf6YtG0geyqkkDO/dgtAjYUTBSWEwfEJzt54fyqfiSdzmE9wD4L4X7SUBFefNIkIgw== OR " +
                        "MEDCO_GEN:BuOMhbWOZ9ISr+FMosyPj3IW1u0yzSbzwMjgv52qr9RAF3ti/CRF5UN/ELJq5FoUgnfTsjvuTEdsT46cMYC1pA== OR " +
                        "MEDCO_GEN:LCd6f8ykhyIrPh2R9fpetTmxlkGsAeKxpKP8n7szIHPkCS4VfcHl5OG7mYW6RH+O1UbnEsKO5fV0sBuor8PpYg== OR " +
                        "MEDCO_GEN:qtvSnylwP7PlT4KTB1MBKXDLhUF9CdNkzfEKAjvnLstoqU29HfYBARZQDRxrgQvaLaOZSfSZB/KamqvmNniO0w==";

            case 15: // medco 6 servers
                return "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +
                        "MEDCO_ENC:FzXxSbBn86gMmF7WT6a4kHDcHrOg3SEkaojcPm7U3qsQp0bhzaLZLYenL/+yNS5j39TFcLU1uSUE5I8tD3Qryw==" +
                        " AND " +
                        "MEDCO_ENC:66xaTIbPcE8V/4u9cE7UWFjgBPpu7yBMSfLSsNrDeTssfy57z5DfTAI+ynrVMzosOapo2SqQxRrrKFSWIljEbw==" +
                        " AND " +
                        "MEDCO_GEN:ZwneoQQyvDUckDcxlOvS+1IvDckXgw7n13IpznyAcHaU6r3uHuSZOXFHUHxhqINh0q6PFj9htw4Ogrt0TR7b5w== OR " +
                        "MEDCO_GEN:auWQ7K+BBDlyRZdSlyIHUDh6d+z60Gsop7aanz4kyYRMrCNNm9jUOqHmbpDawm05fygUZ5jZLEcRJoQuP2Ao9g== OR " +
                        "MEDCO_GEN:LnTrW21XUj7DTSzYdhilO39xBqFNFmkL72q1m8xoSwyI8IGHgQGmw9rDno7iGYKxoVCBarwLHrqykVHji4+tQw== OR " +
                        "MEDCO_GEN:2zO57byps/7sa0cXQbIE3fQ7niglT0td5ew21ZH214lUGdLl3Bq94bEHnEhiSHn5FrV9SesmrnI0NuWQQQHe2w==";

            case 16: // medco 9 servers
                return "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +
                        "MEDCO_ENC:ucrIw7mP2vVfFivx0ENbVqiVNJA/rUpfYPpPijeavMl2qa0etxn7uhSJXVtPSfM6Hg5fHyX+v2mjvU+zHdTRpA==" +
                        " AND " +
                        "MEDCO_ENC:Un8OlW0bfCM/z3UFY2kP3CiDDTMZn0lemyFNyda68uHGjpPY2lEB8wOmn+lq9lxBxiZnD0BuUNxqzousBh6AYA==" +
                        " AND " +
                        "MEDCO_GEN:QoYNiucM/dRnN3L569tTNK4ETW2zhMYM2LqtWf3hoIzvXchr7VpjSrMVnssAr4SGv7n2PcGcdoB5hNJ47fyvYQ== OR " +
                        "MEDCO_GEN:/gQIdPCUVw3SXMBs4WozPbco6V9s/glvH+E/oqeBVWO/+dCMT2SYlDaNnkFzYoIUciBFsLhB4ffmGihurYGscA== OR " +
                        "MEDCO_GEN:sPWZvOLnVNltU8vqaum9gm4aMUhcyNb5kGi/iXATVGyyWapHYJ2auq6xEb80LdrDh8Sc3zJxPI5ncLCkMYTILw== OR " +
                        "MEDCO_GEN:IsRF1+L3xp7j0t+MK694nDCman52R7TifUgXk739jIlAq4Ri70ovhXjNKoK2y6MW9g2LlWwKkP7g+s7Rd6M3Zg==";
            default:
                return null;
        }
    }
}
