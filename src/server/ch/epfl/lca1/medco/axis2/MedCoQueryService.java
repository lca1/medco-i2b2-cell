package ch.epfl.lca1.medco.axis2;

import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.XMLUtil;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.apache.axiom.om.OMElement;

/**
 * Apache Axis2 service class for MedCo.
 * This is the entry point for any call to the MedCo cell.
 *
 * Defines the base URL: http://host:port/i2b2/MedCoQueryService/
 */
public class MedCoQueryService {
	// todo: what about URLs other than /request? --> may need to add here and in service file

	/**
     * Identify the type of the incoming request.
     */
	private enum RequestType {MEDCO_QUERY}

	/**
	 * MedCo generic query request.
     * Defines the URL: http://host:port/i2b2/MedCoQueryService/request
	 *
	 * @param omElement Axis2-formatted request message
	 * @return Axis2-formatted response message
	 */
	public OMElement request(OMElement omElement) {
		return handleRequest(RequestType.MEDCO_QUERY, omElement);
	}

    /**
     * Handle incoming request and execute the right deleguate to generate the response.
     *
     * @param requestType type of the request
     * @param request the request
     *
     * @return the response
     */
	private OMElement handleRequest(RequestType requestType, OMElement request) {

	    // execute request code
		MedCoQueryRequestDelegate delegate;
		switch (requestType) {
			case MEDCO_QUERY:
				delegate = new MedCoQueryRequestDelegate();
				break;
			default:
				throw Logger.error(new IllegalArgumentException("Illegal requestType"));
		}

		// encapsulate response
		OMElement returnElement = null;
		try {
			String response = delegate.handleRequest(request.toString());
			Logger.debug("Generated response: " + response);
			returnElement = XMLUtil.OMElementFromString(response);

		} catch (I2B2Exception e) {
		    Logger.error(e);
		}

		return returnElement;
	}
}
