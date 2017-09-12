package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.i2b2.I2B2Cell;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import ch.epfl.lca1.medco.util.Logger;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.crc.datavo.i2b2message.RequestMessageType;
import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseMessageType;
import org.apache.axiom.om.OMElement;

import javax.xml.bind.JAXBElement;

// todo: harmonize error handling (i2b2status + exceptions)
public class I2B2MedCoCell extends I2B2Cell {

    private static final String URL_PATH_MEDCO_REQ = "/request";


    public I2B2MedCoCell(String medcoCellUrl, MedCoI2b2MessageHeader auth) {
        super(medcoCellUrl, auth);
    }

    /**
     *
     * @param medcoRequest
     * @return
     * @throws I2B2Exception
     */
    public I2B2QueryResponse medcoQuery(I2B2QueryRequest medcoRequest) throws I2B2Exception {

        // make query request (from query definition) with a patient set result output
        I2B2QueryResponse parsedResp;
        try {
            // TODO: create custom service client that is used by all cells

            Logger.info("New request to cell " + cellURL);
            RequestMessageType newReqMessage = createRequestMessage(medcoRequest.getMessageBody());
            OMElement reqMessageOM = msgUtil.buildOMElement(newReqMessage);
            String answerMessage = CustomServiceClient.sendRESTCustomTimeout(cellURL + URL_PATH_MEDCO_REQ, reqMessageOM);
            Logger.debug("Cell " + cellURL + " answered: " + answerMessage);

            // extract response
            JAXBElement responseJaxb = msgUtil.unMashallFromString(answerMessage);
            ResponseMessageType resp = (ResponseMessageType) responseJaxb.getValue();

            parsedResp = new I2B2QueryResponse(resp);
            Logger.info("crc query request result: " + parsedResp.getI2b2Status());

        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }

        return parsedResp;
    }



}
