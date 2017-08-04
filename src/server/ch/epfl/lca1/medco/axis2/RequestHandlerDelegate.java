/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the i2b2 Software License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Rajesh Kuttan
 */
package ch.epfl.lca1.medco.axis2;
// TODO: have that in orig CRC? NO

import java.util.List;

//import org.jboss.cache.Cache;
//import org.jboss.cache.Node;

import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.crc.datavo.pm.ParamType;


/**
 * Class to delegate i2b2 requests to appropriate {@link } Class
 * unwraps i2b2 request message and based on the request type, the request will
 * be delegated to appropriate request handler by calling execute function. The
 * return value from execute function is just passed back to the client $Id:
 * RequestHandlerDelegate.java,v 1.6 2007/10/15 17:29:19 rk903 Exp $
 *
 * @author rkuttan
 */
public abstract class RequestHandlerDelegate {
	/** log **/
	//protected final Log log = LogFactory.getLog(getClass());

	public static final String ERROR_TYPE = "ERROR";
	public static final String DONE_TYPE = "DONE";

	/**
	 * Function to delegate request to appropriate request handler class and
	 * passes back the response message output back to client
	 *
	 * @param
	 * @return response message xml
	 * @throws I2B2Exception
	 */
	public abstract String handleRequest(String requestString)
			throws I2B2Exception;

	/**
	 * Function to unmarshall i2b2 request message type
	 *
	 * @param requestXml
	 * @return RequestMessageType
	 * @throws JAXBUtilException
	 */
	/*protected RequestMessageType getI2B2RequestMessageType(String requestXml)
			throws JAXBUtilException {
		JAXBUtil msgUtil = CRCJAXBUtil.getJAXBUtil();
		JAXBElement jaxbElement = msgUtil.unMashallFromString(requestXml);
		RequestMessageType requestMessageType = (RequestMessageType) jaxbElement
				.getValue();

		return requestMessageType;
	}*/

	/**
	 * Function marshall i2b2 response message type
	 *
	 * @param
	 * @return
	 *//*
	protected String getResponseString(ResponseMessageType responseMessageType) {
		StringWriter strWriter = new StringWriter();

		try {
			edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory of = new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();
			JAXBUtil msgUtil = CRCJAXBUtil.getJAXBUtil();
			msgUtil.marshaller(of.createResponse(responseMessageType),
					strWriter);
		} catch (JAXBUtilException e) {
			log.error("Error while generating response message"
					+ e.getMessage());
		}

		return strWriter.toString();
	}*/

	protected void putRoles(String projectId, String userId, String domainId,
			List<String> roles) {
		// get cache
		try {
			//TODO removed cache
			//Cache cache = CacheUtil.getCache();
			//Node rootNode = cache.getRoot();
			String roleTree = domainId + "/" + projectId + "/" + userId;
			//rootNode.put(roleTree, roles);
			//CacheUtil.put(roleTree, roles);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void putPocessTiming(String projectId, String userId, String domainId,
			ParamType paramType) {
		// get cache
		try {
			//TODO removed cache
			//Cache cache = CacheUtil.getCache();
			//Node rootNode = cache.getRoot();
			String roleTree = domainId + "/" + projectId + "/" + userId + "/" + paramType.getName();
			//rootNode.put(roleTree, paramType.getValue());
			//CacheUtil.put(roleTree,  paramType.getValue());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
