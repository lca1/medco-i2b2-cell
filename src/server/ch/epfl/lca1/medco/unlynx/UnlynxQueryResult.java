package ch.epfl.lca1.medco.unlynx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import ch.epfl.lca1.medco.util.XMLUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;

/**
 * Container class for an Unlynx query result. Given XML input extract the information.
 *
 * Example of such a file:
 * {@code
 * <medco_query_result>
 *      <id>query ID</id>
 * 		<result_mode> result mode (0 or 1)</result_mode>
 * 		<enc_result>encrypted result</enc_result>
 * 		<error>a message error (only if error, the enc_result will be empty)</error>
 * 	    <times_ms>taks1: 44; task2: 23</times_ms>
 * </medco_query_result>
 * }
 */
public class UnlynxQueryResult {	

    /** The ID of the query. */
	private String queryId;

	/** The result of the query, encrypted with the querier's key, and encoded in base64. */
	private String encResultB64;

	/** The result mode used for the query (0 or 1). */
	private int resultMode;

	/** The error message if something went wrong. */
	private String errorMessage;

    /** The recorded execution times. */
    private String timesMs;
	
	// name of XML elements (definition)
	private static String
            EL_NAME_ROOT = "medco_query_result",
            EL_NAME_ID = "id",
            EL_NAME_RESULT_MODE = "result_mode",
	        EL_NAME_ENC_RESULT = "enc_result",
            EL_NAME_ERROR = "error",
            EL_NAME_TIMES = "times_ms";


    // XML tags
	private static String XML_START_TAG = "<" + EL_NAME_ROOT + ">";
	private static String XML_END_TAG = "</" + EL_NAME_ROOT + ">";
	
	/**
	 * Query result constructor. Parses the query and set default values of some fields.
	 * todo: wrong doc
	 * @param stdinString the XML as string to parse
	 * @throws I2B2XMLException in case of invalid XML
	 */
	public UnlynxQueryResult(String stdinString) throws I2B2XMLException {
		// XXX: a bit hackish
		InputStream stdin = new ByteArrayInputStream(stdinString.getBytes(StandardCharsets.UTF_8));
		String resultXMLString = XMLUtil.xmlStringFromStream(stdin, XML_START_TAG, XML_END_TAG, false);

		Logger.info("Parsing unlynx XML query result");
		Logger.debug("XML string: " + resultXMLString);
		
		SAXBuilder sxb = new SAXBuilder();
		try {
			parseDocument(sxb.build(new ByteArrayInputStream(resultXMLString.getBytes("UTF-8"))));
      	} catch(IOException | JDOMException e) {
      		throw Logger.error(new I2B2XMLException("XML parsing error", e));
      	}
	}
	
	/**
	 * Extract from a XML Document the result data.
	 * 
	 * @param doc XML document
	 * @throws I2B2XMLException in case of invalid XML
	 */
	private void parseDocument(Document doc) throws I2B2XMLException {
		Element root = doc.getRootElement();
		
		// sanity check
		if (!root.getName().equals(EL_NAME_ROOT)) {
			throw Logger.error(new I2B2XMLException("XML not properly formed."));
		}
		
		// extract 
		queryId = root.getChildText(EL_NAME_ID);
		encResultB64 = root.getChildText(EL_NAME_ENC_RESULT);
		errorMessage = root.getChildText(EL_NAME_ERROR);
		timesMs = root.getChildText(EL_NAME_TIMES);
		
		String resultModeString = root.getChildText(EL_NAME_RESULT_MODE);
		if (resultModeString != null) {
			try {
				resultMode = Integer.parseInt(root.getChildText(EL_NAME_RESULT_MODE));
			} catch (NumberFormatException e) {
				Logger.warn(e);
				resultMode = -1;
			}
		} else {
			resultMode = -1;
		}
		
		// consistency check
		if (errorMessage == null && encResultB64 == null) {
			throw Logger.error(new I2B2XMLException("XML message inconsistent: enc result and error are null"));
		}
	}



    /**
     * @return the query ID
     */
	public String getQueryId() {
		return queryId;
	}

    /**
     * @return the result encrypted with the querier's key, encoded in base64
     */
	public String getEncResultB64() {
		return encResultB64;
	}

    /**
     * @return the result mode of the query
     */
	public int getResultMode() {
		return resultMode;
	}

    /**
     * @return the error message
     */
	public String getErrorMessage() {
		return errorMessage;
	}

	public String getTimes() {
	    return timesMs;
    }
}