package ch.epfl.lca1.medco.i2b2.fr;

import edu.harvard.i2b2.crc.datavo.i2b2message.BodyType;
import edu.harvard.i2b2.crc.datavo.i2b2message.RequestMessageType;
import edu.harvard.i2b2.crc.datavo.pdo.PatientDataType;
import ch.epfl.lca1.medco.i2b2.I2B2Cell;
import ch.epfl.lca1.medco.i2b2.I2b2Status;
import ch.epfl.lca1.medco.i2b2.pm.UserAuthentication;
import edu.harvard.i2b2.crc.loader.datavo.fr.File;
import edu.harvard.i2b2.crc.loader.datavo.fr.SendfileRequestType;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.axis2.ServiceClient;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.OperationClient;
import org.apache.axis2.client.Options;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.wsdl.WSDLConstants;
import org.w3c.dom.Document;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.bind.JAXBElement;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;

// todo: harmonize error handling (i2b2status + exceptions)
public class I2B2FRCell extends I2B2Cell {


    private static final long UPLOAD_TIMEOUT_MS = 10000L;
    private static final String AXIS2_ACTION_SENDFILE = "urn:sendfileRequest",
                                AXIS2_TMP_DIR = "temp",
                                AXIS2_FILE_SIZE_THRESHOLD = "4000";


    public I2B2FRCell(UserAuthentication auth) {
        super(medCoUtil.getFileRepositoryCellUrl(), auth); // get url
    }

    /**
     * Wrapper around uploadFile to easily upload a Patient Data Object.
     *
     * @param pdo the patient data object
     * @param uploadName the name the file should take on the File Repository
     * @throws I2B2Exception in case of failure or error
     */
    public void uploadPDO(PatientDataType pdo, String uploadName) throws I2B2Exception {

        JAXBElement wrappedPdo = pdoOF.createPatientData(pdo);
        Document doc = MedCoUtil.getMsgUtil().documentFromJAXBElement(wrappedPdo);

        try {
            // create the temp file
            Path tmpFilePath = Files.createTempFile(null, null);

            // write XML to temp file
            DOMSource source = new DOMSource(doc);
            FileWriter writer = new FileWriter(tmpFilePath.toFile());
            StreamResult result = new StreamResult(writer);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(source, result);

            // make the request
            uploadFile(tmpFilePath.toAbsolutePath().toString(), uploadName);

            // delete temp file
            //Files.delete(tmpFilePath);
            //todo: temporarly disabled
            System.out.println(tmpFilePath.toAbsolutePath());
            Files.copy(tmpFilePath, Paths.get("/home/misbach/pdo-temp.xml"), StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException | TransformerException e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    /**
     * Upload a file into the file repository.
     * Succeed if no exception is thrown.
     *
     * @param filePath local path of the file to upload
     * @param uploadName name used on the file repository
     * @throws I2B2Exception in case of failure or error
     */
    public void uploadFile(String filePath, String uploadName) throws I2B2Exception {

        // generate axis2 options
        Options options = new Options();
        options.setTo(new EndpointReference(this.getCellUrl()));
        options.setAction(AXIS2_ACTION_SENDFILE);
        options.setSoapVersionURI(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
        options.setTimeOutInMilliSeconds(UPLOAD_TIMEOUT_MS);
        options.setProperty(Constants.Configuration.ENABLE_SWA, Constants.VALUE_TRUE);
        options.setProperty(Constants.Configuration.CACHE_ATTACHMENTS, Constants.VALUE_TRUE);
        options.setProperty(Constants.Configuration.ATTACHMENT_TEMP_DIR, AXIS2_TMP_DIR);
        options.setProperty(Constants.Configuration.FILE_SIZE_THRESHOLD, AXIS2_FILE_SIZE_THRESHOLD);

        // generate i2b2 request message
        SendfileRequestType sendfileRequest = loaderFrOF.createSendfileRequestType();
        File file = loaderFrOF.createFile();
        file.setName(uploadName);
        file.setOverwrite("true");
        sendfileRequest.setUploadFile(file);
        BodyType bodyType = i2b2OF.createBodyType();
        bodyType.getAny().add(loaderFrOF.createSendfileRequest(sendfileRequest));

        RequestMessageType requestMessage = createRequestMessage(bodyType);
        OMElement requestElement = msgUtil.buildOMElement(requestMessage);

        // request
        org.apache.axis2.client.ServiceClient sender = null;
        try {
            sender = ServiceClient.getServiceClient();
            sender.setOptions(options);
            OperationClient mepClient = sender.createClient(org.apache.axis2.client.ServiceClient.ANON_OUT_IN_OP);

            // message context and envelope
            MessageContext mc = new MessageContext();
            mc.addAttachment(uploadName, new DataHandler(new FileDataSource(filePath)));
            mc.setDoingSwA(true);

            SOAPEnvelope env = OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope();
            env.getBody().addChild(requestElement);
            mc.setEnvelope(env);
            mepClient.addMessageContext(mc);
            Logger.debug("Ready to send query: " + requestElement);

            // send request and interpret answer
            mepClient.execute(true);
            MessageContext response = mepClient.getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
            OMElement frResponse = (OMElement) response.getEnvelope().getBody().getFirstOMChild();
            Logger.debug("Received answer to query: " + frResponse);

            if (msgUtil.parseStatus(frResponse) != I2b2Status.DONE) {
                throw Logger.error(new I2B2Exception("Request failed."));
            }

            Logger.info("Successfully uploaded file.");
        } catch (AxisFault e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));

        } finally {
            if (sender != null) {
                try{
                    sender.cleanupTransport();
                    sender.cleanup();
                } catch (AxisFault e) {
                    Logger.warn("Error cleaning up", e);
                }
            }
        }
    }
}
