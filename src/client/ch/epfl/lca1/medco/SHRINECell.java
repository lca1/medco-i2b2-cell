package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.i2b2.I2B2Cell;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.i2b2message.RequestMessageType;
import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseMessageType;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.impl.llom.OMNodeImpl;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.io.StringWriter;

// todo: harmonize error handling (i2b2status + exceptions)
public class SHRINECell extends I2B2Cell {

    private static final String URL_PATH_SHRINE_REQ = "/request"; //URL--


    public SHRINECell(String shrineCellUrl, MedCoI2b2MessageHeader auth) {
        super(shrineCellUrl, auth);
    }

    /**
     *
     * @param shrineRequest
     * @return
     * @throws I2B2Exception
     */
    public I2B2QueryResponse shrineQuery(I2B2QueryRequest shrineRequest) throws I2B2Exception {

        // make query request (from query definition) with a patient set result output
        I2B2QueryResponse parsedResp;
        try {
            // TODO: create custom service client that is used by all cells

            Logger.info("New request to cell " + cellURL);
            RequestMessageType newReqMessage = createRequestMessage(shrineRequest.getMessageBody());
            OMElement reqMessageOM = buildOMElementShrineCustom(newReqMessage);
            String answerMessage = CustomServiceClient.sendRESTCustomTimeout(cellURL + URL_PATH_SHRINE_REQ, reqMessageOM);
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


    /**
     * Build OM element from an i2b2 request message.
     *
     * @param reqMessage the i2b2 request message
     * @return OM element wrapping the i2b2 request message
     *
     * @throws I2B2XMLException if there is an error while generating the element
     */
    public OMElement buildOMElementShrineCustom(RequestMessageType reqMessage) throws I2B2XMLException {

        // init objects
        StringWriter strWriter = new StringWriter();
        XMLInputFactory xif = XMLInputFactory.newInstance();
        edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory hiveObjectFactory =
                new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();

        // generate element
        try {
            msgUtil.marshaller(hiveObjectFactory.createRequest(reqMessage), strWriter);

            // insert shrine stuff
            String orig = strWriter.toString();

            String update1 =
                    orig.substring(0, orig.indexOf("</query_definition>")) +
                    "<use_shrine>1</use_shrine>\n" +
                    orig.substring(orig.indexOf("</query_definition>"));

            String update2 =
                    update1.substring(0, update1.indexOf("</message_body>")) +
                    //"<shrine><queryTopicID>1</queryTopicID></shrine>\n" + todo: disabled to avoid topic match error
                    update1.substring(update1.indexOf("</message_body>"));

            System.out.println("XXXXXXX");
            System.out.println(update2);

            StringReader strReader = new StringReader(update2);
            XMLStreamReader reader = xif.createXMLStreamReader(strReader);
            StAXOMBuilder builder = new StAXOMBuilder(reader);
            OMElement request = builder.getDocumentElement();

            return request;

        } catch (JAXBUtilException | XMLStreamException e) {
            throw Logger.error(new I2B2XMLException("Error while generating OM element. ", e));
        }
    }
}
