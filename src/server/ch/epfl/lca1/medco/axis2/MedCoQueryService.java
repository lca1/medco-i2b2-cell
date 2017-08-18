/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the i2b2 Software License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Rajesh Kuttan
 *     Wayne Chan
 */
package ch.epfl.lca1.medco.axis2;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import ch.epfl.lca1.medco.util.Logger;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;

import edu.harvard.i2b2.common.exception.I2B2Exception;

/**
 * <b>Axis2's service class<b>
 *
 * <p>
 * This class implements methods related to webservice operation.
 * <li>For example http://localhost:8080/axis2/services/crc/serfinderrequest
 * http://localhost:8080/axis2/services/crc/pdorequest
 *
 * $Id: QueryService.java,v 1.14 2009/09/10 19:32:06 rk903 Exp $
 *
 * @author rkuttan

 */
public class MedCoQueryService {

	/** enum to identify the request type **/
	private enum RequestType {MEDCO_QUERY}

	/**
	 * Webservice function to handle setfinder request
	 *
	 * @param omElement
	 *            request message wrapped in OMElement
	 * @return response message in wrapped inside OMElement
	 */
	public OMElement request(OMElement omElement) {

		OMElement response = handleRequest(RequestType.MEDCO_QUERY, omElement);

		//StopWatch.overall.stop(); stopped just before generating reports
		return response;
	}


	// --------------------------------------------
	// Creates delegate based on the request type
	// --------------------------------------------
	private OMElement handleRequest(RequestType requestType, OMElement request) {

		// get delegate coresponding to request
		RequestHandlerDelegate requestHandlerDelegate = null;
		switch (requestType) {
			case MEDCO_QUERY:
				requestHandlerDelegate = new MedCoQueryRequestDelegate();
				break;
			default:
				throw new IllegalArgumentException("Illegal requestType");
		}

		// execute the delegate
		OMElement returnElement = null;
		try {
			String response = requestHandlerDelegate.handleRequest(request.toString());
			Logger.debug("Response in service: " + response);

			returnElement = buildOMElementFromString(response);

		} catch (Throwable e) {
		    Logger.error(e);
		}
		return returnElement;
	}

	/**
	 * Function constructs OMElement for the given String
	 *
	 * @param xmlString
	 * @return OMElement
	 * @throws XMLStreamException
	 */
	private OMElement buildOMElementFromString(String xmlString) throws XMLStreamException {
		XMLInputFactory xif = XMLInputFactory.newInstance();
		StringReader strReader = new StringReader(xmlString);
		XMLStreamReader reader = xif.createXMLStreamReader(strReader);
		StAXOMBuilder builder = new StAXOMBuilder(reader);
		OMElement element = builder.getDocumentElement();
		return element;
	}
}
