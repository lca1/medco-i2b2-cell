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
import java.io.PrintWriter;

import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by misbach on 15.07.17.
 */
public class MedCoQueryClient {

    /**
     * Output on stderr information of the query, output on stdout the times
     *
     * @param args
     */
    public static void main(String[] args) throws InterruptedException {

        // disable all logging (to control all output)
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.DEBUG);

        // get parameters from command-line and set the client configuration
        CommandLine cmd = parseCli(args);
        String[] serversUrl = cmd.getOptionValues("server");
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
            List<List<String>> parsedQuery = parseQuery(Integer.parseInt(queryId));
            I2B2QueryRequest request = new I2B2QueryRequest(auth);
            request.setQueryDefinition(queryName, parsedQuery);

            // make request to every specified servers in a thread
            final UnlynxDecrypt decrypt = new UnlynxDecrypt();
            Thread[] queryThreads = new Thread[serversUrl.length];

            for (int i = 0; i < serversUrl.length; i++) {
                final int i_cpy = i;
                queryThreads[i] = new Thread(() -> {
                    try {
                        I2B2MedCoCell medCoCell = new I2B2MedCoCell(serversUrl[i_cpy], auth);
                        I2B2QueryResponse response = medCoCell.medcoQuery(request);
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
            double tmp1, tmp2, tmp3, tmp4;
            tmp1 = tmp2 = tmp3 = tmp4 = 0;
            for (Map.Entry<Integer, String> result : timesJsonOutput.entrySet()) {
                System.out.println("{\"" + result.getKey() + "\":" + result.getValue() + "}");

                JsonObject jsonResult = Json.parse(result.getValue()).asObject();

                ts.tt += jsonResult.getInt("Overall (axis2 in/out)", 0);
                ts.qpi2b2 += jsonResult.getInt("Query parsing/splitting", 0);
                ts.clearqueryi2b2 += jsonResult.getInt("Clear query: i2b2 query", 0);
                ts.psretrieval += jsonResult.getInt("Clear query: patient set retrieval", 0);
                ts.eqp += jsonResult.getInt("Patient set encrypted data retrieval", 0);
                ts.utt += jsonResult.getInt("Unlynx query", 0);
                ts.uet += jsonResult.getInt("Unlynx execution time", 0);
                ts.uct += jsonResult.getInt("Unlynx communication time", 0);

                ts.qpunlynx += jsonResult.getInt("Parsing time", 0);
                ts.broadcast += jsonResult.getInt("Broadcasting time", 0);

                ts.ddtqet += jsonResult.getInt("DDT Query execution time", 0);
                if (jsonResult.getInt("DDT Query communication time", 0) > tmp1)
                    tmp1 = jsonResult.getInt("DDT Query communication time", 0);

                ts.ddtdet += jsonResult.getInt("DDT Data execution time", 0);
                if (jsonResult.getInt("DDT Data communication time", 0) > tmp2)
                    tmp2 = jsonResult.getInt("DDT Data communication time", 0);

                ts.aet += jsonResult.getInt("Aggregation time", 0);

                ts.set += jsonResult.getInt("Shuffling execution time", 0);
                if (jsonResult.getInt("Shuffling communication time", 0) > tmp3)
                    tmp3 = jsonResult.getInt("Shuffling communication time", 0);

                ts.kset += jsonResult.getInt("Key Switching execution time", 0);
                if (jsonResult.getInt("Key Switching communication time", 0) > tmp4)
                    tmp4 = jsonResult.getInt("Key Switching communication time", 0);

            }

            ts.ddtqct += tmp1;
            ts.ddtdct += tmp2;
            ts.sct += tmp3;
            ts.ksct += tmp4;

            System.out.flush();

        }

        // 1000 is to convert ms to secs
        ts.Divide(timesJsonOutput.size(), 1000*numRepetitions);

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
            writer.println("QUERY: ");
            writer.println("Overall time: " + Double.toString(ts.tt));

            writer.println("");

            writer.println("Query parsing i2b2: " + Double.toString(ts.qpi2b2));
            writer.println("Clear query i2b2: " + Double.toString(ts.clearqueryi2b2));
            writer.println("Patient set retrieval: " + Double.toString(ts.psretrieval));

            writer.println("");

            writer.println("Encrypted Query preparation: " + Double.toString(ts.eqp));

            writer.println("");

            writer.println("Unlynx overall time: " + Double.toString(ts.utt));
            writer.println("Unlynx total execution time: " + Double.toString(ts.uet));
            writer.println("Unlynx total communication time: " + Double.toString(ts.uct));

            writer.println("");

            writer.println("Query/Data parsing Unlynx: " + Double.toString(ts.qpunlynx));
            writer.println("Total broadcast time (unlynx client -> unlynx server): " + Double.toString(ts.broadcast));

            writer.println("");

            writer.println("DDT Query (execution time): " + Double.toString(ts.ddtqet));
            writer.println("DDT Query (communication time): " + Double.toString(ts.ddtqct));

            writer.println("");

            writer.println("DDT Data (execution time): " + Double.toString(ts.ddtdet));
            writer.println("DDT Data (communication time): " + Double.toString(ts.ddtdct));

            writer.println("");

            writer.println("Aggregation (execution time): " + Double.toString(ts.aet));

            writer.println("");

            writer.println("Shuffling (execution time): " + Double.toString(ts.set));
            writer.println("Shuffling (communication time): " + Double.toString(ts.sct));

            writer.println("");

            writer.println("Key Switching (execution time): " + Double.toString(ts.kset));
            writer.println("Key Switching (communication time): " + Double.toString(ts.ksct));
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * parse query, ex: X OR Y AND F AND Z OR H ==> (X OR Y) AND (F) AND (Z OR H)
     */
    private static List<List<String>> parseQuery(int queryId) {
        List<List<String>> parsedQuery = new ArrayList<>();
        String[] orTerms = getQueryUseCase(queryId).split(" AND ");

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

    private static String getQueryUseCase(int nb) {
        switch (nb) {

            case 100: // use-case medco-normal
                return
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Male\\ OR " +
                        "\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\GENDER\\Female\\" +
                        " AND " +

                        "\\\\SENSITIVE_TAGGED\\medco\\encrypted\\/98y5inj97O+26HXW8fJnbHDH0CCohmlCYNgMfgJ2mzufKVl8PBffruVGm1C05tqWxrXKPNF9AMghe8ELmNmzA==\\";

            case 1: // use-case 2 encrypted encrypted
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
