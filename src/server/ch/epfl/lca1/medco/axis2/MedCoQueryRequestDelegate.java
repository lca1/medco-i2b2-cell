package ch.epfl.lca1.medco.axis2;

import ch.epfl.lca1.medco.StandardQuery;
import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.i2b2message.*;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import edu.harvard.i2b2.common.exception.I2B2Exception;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Delegate that executes a query for the MedCo cell.
 * Authentication is not performed at this step.
 */
public class MedCoQueryRequestDelegate {
    // todo: from configuration add timeout entry specific to unlynx

    /**
     * XML object factory.
     */
    private static edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory i2b2OF =
			new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();

    /**
     * MedCo utility & configuration.
     */
    private static MedCoUtil medCoUtil = MedCoUtil.getInstance();

	/**
     * Handles the MedCo request by calling the class implementing the workflow.
	 */
	String handleRequest(String requestString) throws I2B2Exception {

        Logger.info("Handling new MedCo query request");

        try {
			I2B2QueryRequest request = new I2B2QueryRequest(requestString);
            I2B2QueryResponse queryAnswer;

            // get a query response
			if (request.shouldForwardToI2b2Crc()) {
                I2B2CRCCell crcCell = new I2B2CRCCell(medCoUtil.getDataRepositoryCellUrl(), request.getMessageHeader());
                queryAnswer = crcCell.queryRequest(request);

            } else {
                StandardQuery medcoQuery = new StandardQuery(request, medCoUtil.getUnlynxBinPath(), medCoUtil.getUnlynxGroupFilePath(),
                        medCoUtil.getUnlynxDebugLevel(), medCoUtil.getUnlynxEntryPointIdx(), medCoUtil.getUnlynxProofsFlag(),
                        medCoUtil.getI2b2Waittimems(), medCoUtil.getDataRepositoryCellUrl(), medCoUtil.getProjectManagementCellUrl());

                queryAnswer = medcoQuery.executeQuery();
            }
			
            // send back the response
			StringWriter strWriter = new StringWriter();
			MedCoUtil.getMsgUtil().marshallerWithCDATA(i2b2OF.createResponse(queryAnswer), strWriter,
					new String[]{"observation_blob","patient_blob","observer_blob","concept_blob","event_blob"});
			return strWriter.toString();

		} catch (MedCoException | I2B2Exception | JAXBUtilException e) {
            Logger.error("Error during query", e);
            ResponseMessageType errorResponse = i2b2OF.createResponseMessageType();
            ResponseHeaderType respHeader = i2b2OF.createResponseHeaderType();
            errorResponse.setResponseHeader(respHeader);
            respHeader.setInfo(i2b2OF.createInfoType());

            // detailed error message
            StringWriter infoMessage = new StringWriter();
            PrintWriter exceptionPrint = new PrintWriter(infoMessage);
            infoMessage.append("Exception:" + e.getMessage() + "\n");
            e.printStackTrace(exceptionPrint);
            infoMessage.append("Caused by: " + (e.getCause() == null ? "no cause" : e.getCause().getMessage()) + "\n");
            e.getCause().printStackTrace(exceptionPrint);
            respHeader.getInfo().setValue(infoMessage.toString());

            try {
                StringWriter strWriter = new StringWriter();
                MedCoUtil.getMsgUtil().marshallerWithCDATA(i2b2OF.createResponse(errorResponse), strWriter,
                        new String[]{"observation_blob", "patient_blob", "observer_blob", "concept_blob", "event_blob"});
                return strWriter.toString();
            } catch (JAXBUtilException e1) {
                throw Logger.error(new I2B2XMLException("Error  generating error message.", e));
            }
        }
	}
}
