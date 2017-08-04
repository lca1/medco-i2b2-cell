package ch.epfl.lca1.medco.i2b2;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXBElement;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import edu.harvard.i2b2.crc.datavo.i2b2message.*;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import edu.harvard.i2b2.common.util.jaxb.JAXBUnWrapHelper;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;

import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtil;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.w3c.dom.Document;


/**
 * JAXB utils
 * 
 * @author misbach
 *
 */
public class MessagesUtil extends JAXBUtil {
	// method OMElement -> JAXB (provide class or not)
	// invese
	// enough?

    private static JAXBUnWrapHelper unwrapHelper = new JAXBUnWrapHelper();
    private static MedCoUtil medCoUtil = MedCoUtil.getInstance();


    public MessagesUtil(String[] packageName) {
	    super(packageName);
    }

    public static JAXBUnWrapHelper getUnwrapHelper() {
        return unwrapHelper;
    }

	/**
	 * Build OM element from an i2b2 request message.
	 * 
	 * @param reqMessage the i2b2 request message
	 * @return OM element wrapping the i2b2 request message
	 * 
	 * @throws I2B2XMLException if there is an error while generating the element
	 */
	public OMElement buildOMElement(RequestMessageType reqMessage) throws I2B2XMLException {
		
		// init objects
		StringWriter strWriter = new StringWriter();
		XMLInputFactory xif = XMLInputFactory.newInstance();
        edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory hiveObjectFactory =
				new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();
		
		// generate element
		try {
			this.marshaller(hiveObjectFactory.createRequest(reqMessage), strWriter);
			StringReader strReader = new StringReader(strWriter.toString());
			XMLStreamReader reader = xif.createXMLStreamReader(strReader);
			StAXOMBuilder builder = new StAXOMBuilder(reader);
			OMElement request = builder.getDocumentElement();
			
			return request;
			
		} catch (JAXBUtilException | XMLStreamException e) {
			throw Logger.error(new I2B2XMLException("Error while generating OM element. ", e));
		}
	}

    @SuppressWarnings("unchecked")
    public <RESP> RESP getBodyFromRespMessage(ResponseMessageType respMessage, Class<RESP> bodyType) throws I2B2XMLException {
        Logger.debug("Extracting body (: " + bodyType.getName() + "), resp: " + respMessage);
        RESP body;
        try {
            body = (RESP) unwrapHelper.getObjectByClass(
                    respMessage.getMessageBody().getAny(), bodyType);
        } catch (JAXBUtilException e) {
            throw Logger.error(new I2B2XMLException("Could not extract body.", e));
        }
        return body;
    }

        /**
         * Construct a new I2B2 request message, reusing the information of another such a message.
         * TODO: hardcoded parameters (timeout, facility name, message header tag)
         * TODO: unit test
         *
         * @param origReqMessage the original request message from which take the security info and the project id
         * @return a new I2B2 request message, with security and project same as the provided request message
         *
         * @throws I2B2XMLException if the message is not correct
         *
         */
    public RequestMessageType newReqMessageFromReqMessage(RequestMessageType origReqMessage) {
        SecurityType security = origReqMessage.getMessageHeader().getSecurity();
        String projectId = origReqMessage.getMessageHeader().getProjectId();

        return newReqMessageFromSecurity(security, projectId);
    }

    public RequestMessageType newReqMessageFromSecurity(SecurityType security, String projectId) {
        return newReqMessage(security.getDomain(), projectId, security.getUsername(), security.getPassword().isIsToken(),
                security.getPassword().getTokenMsTimeout(), security.getPassword().getValue());
    }


    public RequestMessageType newReqMessage(String domainId, String projectId, String username,
                                                   boolean passwordIsToken, int tokenTimeoutMs, String passwordValue) {

        SecurityType security = new SecurityType();
        security.setDomain(domainId);
        security.setUsername(username);

        PasswordType password = new PasswordType();
        password.setIsToken(passwordIsToken);
        password.setTokenMsTimeout(tokenTimeoutMs);
        password.setValue(passwordValue);
        security.setPassword(password);

        // setup message header
        MessageHeaderType messageHeader = null;//TODO (MessageHeaderType) medCoUtil.getSpringBeanFactory().getBean("message_header");
        messageHeader.setSecurity(security);
        messageHeader.setProjectId(projectId);
        messageHeader.setReceivingApplication(messageHeader.getSendingApplication());
        FacilityType facility = new FacilityType();
        facility.setFacilityName("sample");
        messageHeader.setSendingFacility(facility);
        messageHeader.setReceivingFacility(facility);

        // setup message body
        //GetUserConfigurationType userConfig = new GetUserConfigurationType();
        //userConfig.getProject().add(projectId);
        //edu.harvard.i2b2.crc.datavo.pm.ObjectFactory of = new edu.harvard.i2b2.crc.datavo.pm.ObjectFactory();
        BodyType body = new BodyType();
        //body.getAny().add(of.createGetUserConfiguration(userConfig));

        // setup request header
        RequestHeaderType requestHeader = new RequestHeaderType();
        requestHeader.setResultWaittimeMs(180000); //TODO: hardcoded value

        // pack in request message
        RequestMessageType requestMessage = new RequestMessageType();
        requestMessage.setMessageBody(body);
        requestMessage.setMessageHeader(messageHeader);
        requestMessage.setRequestHeader(requestHeader);

        Logger.debug("Correctly created new i2b2 request message" + requestMessage);
        return requestMessage;
    }


    public RequestMessageType parseRequestMessage(String reqMsgString) throws I2B2XMLException {
        try {
            JAXBElement reqJaxb = unMashallFromString(reqMsgString);
            RequestMessageType r = (RequestMessageType) reqJaxb.getValue();
            return r;
        } catch (JAXBUtilException e) {
            throw Logger.error(new I2B2XMLException("Request not parsable.", e));
        }
    }



    /**
     * Converts a JAXB Element to a W3C Document.
     *
     * @param el the JAXB Element
     * @return the corresponding W3C Document.
     *
     * @throws I2B2XMLException if the translation fails.
     */
    public Document documentFromJAXBElement(JAXBElement el) throws I2B2XMLException {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            marshaller(el, document);
            return document;
        } catch (JAXBUtilException | ParserConfigurationException e) {
            throw Logger.error(new I2B2XMLException("Could not translate jaxbelement -> document", e));
        }
    }

    public I2b2Status parseStatus(ResponseMessageType resp) throws I2B2XMLException {
        try {
            I2b2Status status = I2b2Status.valueOf(resp.getResponseHeader().getResultStatus().getStatus().getType());
            status.setStatusMessage(resp.getResponseHeader().getResultStatus().getStatus().getValue());
            return status;
        } catch (IllegalArgumentException e) {
            throw Logger.error(new I2B2XMLException("Status not parsable.", e));
        }
    }

    public I2b2Status parseStatus(OMElement omElement) throws I2B2XMLException {
        try {
            JAXBElement responseJaxb = unMashallFromString(omElement.toString());
            ResponseMessageType r = (ResponseMessageType) responseJaxb.getValue();
            return parseStatus(r);
        } catch (JAXBUtilException e) {
            throw Logger.error(new I2B2XMLException("Status not parsable.", e));
        }

    }

    public Pair<I2b2Status, BodyType> parseI2b2Response(String response) throws I2B2XMLException {
        try {
            JAXBElement responseJaxb = unMashallFromString(response);
            ResponseMessageType resp = (ResponseMessageType) responseJaxb.getValue();
            return new Pair<>(parseStatus(resp), resp.getMessageBody());
        } catch (JAXBUtilException e) {
            throw Logger.error(new I2B2XMLException("unmashalling exception", e));
        }
    }

    public RequestMessageType parseI2b2Request(String req) throws I2B2XMLException {
        try {
            JAXBElement responseJaxb = unMashallFromString(req);
            return (RequestMessageType) responseJaxb.getValue();
        } catch (JAXBUtilException e) {
            throw Logger.error(new I2B2XMLException("unmashalling exception", e));
        }
    }
}
