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

import ch.epfl.lca1.medco.StandardQuery;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.i2b2message.*;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;


import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import edu.harvard.i2b2.common.exception.I2B2Exception;

import java.io.PrintWriter;
import java.io.StringWriter;


/**
 * Setfinder query request delegate class $Id: QueryRequestDelegate.java,v 1.17
 * 2008/05/08 15:13:45 rk903 Exp $
 *
 * @author rkuttan
 */
// todo: no authentication done for now, implement!
public class MedCoQueryRequestDelegate extends RequestHandlerDelegate {
	protected static edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory i2b2OF =
			new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();

    protected static MedCoUtil medCoUtil = MedCoUtil.getInstance();



    // todo: hardcoded, should be taken from the PM
	public static final String clientPubKey = "eQviK90cvJ2lRx8ox6GgQKFmOtbgoG9RXa7UnmemtRA=";
	public static final String clientSeckey = "iqLQz3zMlRjCyBrg4+303hsxL7F5vDtIaBxO0oc7gQA=";
	/**

	 */
	public String handleRequest(String requestString) throws I2B2Exception {

        Logger.info("Handling new MedCo query request");

        try {
			I2B2QueryRequest request = new I2B2QueryRequest(requestString);
			
			//I2B2Cell.authenticate(xx); // returns objects with infos about request

            StandardQuery medcoQuery = new StandardQuery(request, medCoUtil.getUnlynxBinPath(), medCoUtil.getUnlynxGroupFilePath(),
                    medCoUtil.getUnlynxDebugLevel(), medCoUtil.getUnlynxEntryPointIdx(), medCoUtil.getUnlynxProofsFlag(),
                    medCoUtil.getI2b2Waittimems(), medCoUtil.getDataRepositoryCellUrl(), medCoUtil.getProjectManagementCellUrl(),
                    null);
			int resultMode = 0;
			int timeoutSeconds = MedCoUtil.getInstance().getI2b2Waittimems();//todo: from configuration add entry specific to unlynx


			I2B2QueryResponse queryAnswer = medcoQuery.executeQuery();

			StringWriter strWriter = new StringWriter();
			MedCoUtil.getMsgUtil().marshallerWithCDATA(i2b2OF.createResponse(queryAnswer), strWriter,
					new String[]{"observation_blob","patient_blob","observer_blob","concept_blob","event_blob"});
			return strWriter.toString();

		} catch (MedCoException | I2B2Exception | JAXBUtilException e) {
            Logger.error("Error during query", e);
            ResponseMessageType errorResponse = i2b2OF.createResponseMessageType();
            ResponseHeaderType respHeader = i2b2OF.createResponseHeaderType();
            errorResponse.setResponseHeader(respHeader);
            InfoType info = i2b2OF.createInfoType();
            respHeader.setInfo(info);

            // detailed error message
            StringWriter infoMessage = new StringWriter();
            PrintWriter exceptionPrint = new PrintWriter(infoMessage);
            infoMessage.append("Exception:" + e.getMessage() + "\n");
            e.printStackTrace(exceptionPrint);
            infoMessage.append("Caused by: " + (e.getCause() == null ? "no cause" : e.getCause().getMessage()) + "\n");
            e.getCause().printStackTrace(exceptionPrint);
            info.setValue(infoMessage.toString());

            try {
                StringWriter strWriter = new StringWriter();
                MedCoUtil.getMsgUtil().marshallerWithCDATA(i2b2OF.createResponse(errorResponse), strWriter,
                        new String[]{"observation_blob", "patient_blob", "observer_blob", "concept_blob", "event_blob"});
                return strWriter.toString();
            } catch (Throwable e1) {
                Logger.error("Error while generating error message", e);
            }
        }

        // all hope lost at this point
		return null;
	}
}
