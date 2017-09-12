package ch.epfl.lca1.medco.unlynx;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ch.epfl.lca1.medco.util.*;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * Manages connection to the local Unlynx client.
 * It runs in a separate to execute the binary specified by the configuration.
 *
 * Start query by using executeQuery() and then join() to wait for the process to end.
 * Next check with getQueryState() the state of the query and finally get the end result with getQueryResult().
 */
public class UnlynxClient {

	private String binPath;
	private String groupFilePath;
	private int debugLevel;
	private int entryPointIdx;
	private int computeProofsFlag;
	private long timeoutSeconds;

	private String lastTimingMeasurements;

	public UnlynxClient(String binPath, String groupFilePath, int debugLevel, int entryPointIdx, int computeProofsFlag, long timeoutSeconds) {
        this.binPath = binPath;
        this.groupFilePath = groupFilePath;
        this.debugLevel = debugLevel;
        this.entryPointIdx = entryPointIdx;
        this.computeProofsFlag = computeProofsFlag;
        this.timeoutSeconds = timeoutSeconds;
	}

	public String getLastTimingMeasurements() {
	    return lastTimingMeasurements;
    }

	public List<String> computeDistributedDetTags(String queryId, List<String> encryptedQueryItems) throws UnlynxException, I2B2XMLException {

	    if (encryptedQueryItems.size() == 0) {
	        return new ArrayList<>();
        }

	    // generate input stdout
        StringBuilder sb = new StringBuilder();

        sb.append(Constants.DDT_REQ_XML_START_TAG + "\n");
            sb.append("<id>");
            sb.append(queryId);
            sb.append("</id>\n");
            sb.append("<enc_values>\n");
            for (String encValue : encryptedQueryItems) {
                sb.append("<enc_value>");
                sb.append(encValue);
                sb.append("</enc_value>\n");
            }
            sb.append("</enc_values>\n");
        sb.append(Constants.DDT_REQ_XML_END_TAG + "\n");

        // run unlynx
        SystemBinaryRunThread process = new SystemBinaryRunThread(getUnlynxRunCall(), sb.toString(), timeoutSeconds);
        process.start();
        process.waitForCompletion();

        // process result
        if (process.getRunState() == SystemBinaryRunThread.RunState.COMPLETED) {
            Logger.info("Unlynx DDT request successfully completed");
            return parseDistributedDetTagsCallResult(process.getStdIn());
        } else {
            throw Logger.error(new UnlynxException("Unlynx DDT request failed, run state is: " + process.getRunState().toString()));
        }
    }

    public String aggregateData(String queryId, String clientPubKey, List<String> encDummyFlags) throws UnlynxException, I2B2XMLException {

        // generate input stdout
        StringBuilder sb = new StringBuilder();

        sb.append(Constants.AGG_REQ_XML_START_TAG + "\n");
        sb.append("<id>");
        sb.append(queryId);
        sb.append("</id>\n");

        sb.append("<client_public_key>");
        sb.append(clientPubKey);
        sb.append("</client_public_key>");

        sb.append("<enc_dummy_flags>\n");
        for (String encFlag : encDummyFlags) {
            sb.append("<enc_dummy_flag>");
            sb.append(encFlag);
            sb.append("</enc_dummy_flag>\n");
        }
        sb.append("</enc_dummy_flags>\n");
        sb.append(Constants.AGG_REQ_XML_END_TAG + "\n");

        // run unlynx
        SystemBinaryRunThread process = new SystemBinaryRunThread(getUnlynxRunCall(), sb.toString(), timeoutSeconds);
        process.start();
        process.waitForCompletion();

        // process result
        if (process.getRunState() == SystemBinaryRunThread.RunState.COMPLETED) {
            Logger.info("Unlynx DDT request successfully completed");
            return parseAggregateCallResult(process.getStdIn());
        } else {
            throw Logger.error(new UnlynxException("Unlynx DDT request failed, run state is: " + process.getRunState().toString()));
        }
    }
	
	/**
	 * Construct the binary system call of the Unlynx client.
	 * 
	 * @return array of tokens for system call to the Unlynx client
	 */
	private String[] getUnlynxRunCall() {
		ArrayList<String> arr = new ArrayList<>();
		
		arr.add(binPath);

		arr.add("-d");
		arr.add(debugLevel + "");

		arr.add("run");
        arr.add("-f");
		arr.add(groupFilePath);
		arr.add("--entryPointIdx");
		arr.add(entryPointIdx + "");
		arr.add("--proofs");
		arr.add(computeProofsFlag + "");

		return arr.toArray(new String[arr.size()]);
	}

    private List<String> parseDistributedDetTagsCallResult(String stdinString) throws UnlynxException, I2B2XMLException {

	    // XXX: hackish
        InputStream stdin = new ByteArrayInputStream(stdinString.getBytes(StandardCharsets.UTF_8));
        String resultXMLString = XMLUtil.xmlStringFromStream(stdin, Constants.DDT_RESP_XML_START_TAG, Constants.DDT_RESP_XML_END_TAG, false);

        SAXBuilder sxb = new SAXBuilder();
        try {
            Document doc = sxb.build(new ByteArrayInputStream(resultXMLString.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getRootElement();

            // sanity check
            if (!root.getName().equals(Constants.DDT_RESP_XML_EL)) {
                throw Logger.error(new I2B2XMLException("XML not properly formed."));
            }

            // error check, exception if yes
            String errorMsg = root.getChildTextNormalize("error");
            if (errorMsg != null && !errorMsg.trim().isEmpty()) {
                throw Logger.error(new UnlynxException(errorMsg));
            }

            // extract tagged values
            List encValuesXml = root.getChild("tagged_values").getChildren("tagged_value");
            List<String> encValues = new ArrayList<>(encValuesXml.size());
            for (Object anEncValuesXml : encValuesXml) {
                encValues.add(((Element) anEncValuesXml).getValue());
            }

            // extract times
            lastTimingMeasurements = root.getChildText("times");

            return encValues;
        } catch(IOException | JDOMException e) {
            throw Logger.error(new I2B2XMLException("XML parsing error", e));
        }
    }

    private String parseAggregateCallResult(String stdinString) throws UnlynxException, I2B2XMLException {

        // XXX: hackish
        InputStream stdin = new ByteArrayInputStream(stdinString.getBytes(StandardCharsets.UTF_8));
        String resultXMLString = XMLUtil.xmlStringFromStream(stdin, Constants.AGG_RESP_XML_START_TAG, Constants.AGG_RESP_XML_END_TAG, false);

        SAXBuilder sxb = new SAXBuilder();
        try {
            Document doc = sxb.build(new ByteArrayInputStream(resultXMLString.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getRootElement();

            // sanity check
            if (!root.getName().equals(Constants.AGG_RESP_XML_EL)) {
                throw Logger.error(new I2B2XMLException("XML not properly formed."));
            }

            // error check, exception if yes
            String errorMsg = root.getChildTextNormalize("error");
            if (errorMsg != null && !errorMsg.trim().isEmpty()) {
                throw Logger.error(new UnlynxException(errorMsg));
            }

            // extract times
            lastTimingMeasurements = root.getChildText("times");

            // extract aggregated value
            return root.getChildText("aggregate");

        } catch(IOException | JDOMException e) {
            throw Logger.error(new I2B2XMLException("XML parsing error", e));
        }
    }
}
