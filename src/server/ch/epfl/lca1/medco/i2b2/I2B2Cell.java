package ch.epfl.lca1.medco.i2b2;

import javax.xml.bind.JAXBElement;

import edu.harvard.i2b2.crc.datavo.i2b2message.BodyType;
import edu.harvard.i2b2.crc.datavo.i2b2message.RequestHeaderType;
import edu.harvard.i2b2.crc.datavo.i2b2message.RequestMessageType;
import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseMessageType;
import ch.epfl.lca1.medco.i2b2.pm.UserAuthentication;
import org.apache.axiom.om.OMElement;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import edu.harvard.i2b2.common.util.axis2.ServiceClient;
import org.javatuples.Pair;

/**
 * keep internal the translations string <> OMelement <> JAXB
 * TODO: check if it makes to refactor and have abstratec object to manipulate those // maybe a medco jaxbutil ?
 * @author misbach
 *
 */
public abstract class I2B2Cell {
	
	// TODO: send message method + construction class here
		
	/**
	 * URL of the i2b2 cell this object connects to.
	 */
	protected final String cellURL;

	/**
	 * JAXB utility class to manipulate XML.
	 */
	protected static MessagesUtil msgUtil = MedCoUtil.getMsgUtil();
	
	/**
	 * General usage utility class.
	 */
	protected static MedCoUtil medCoUtil = MedCoUtil.getInstance();

	protected UserAuthentication auth;

	// todo: centralize object factories in 1 point (msgutil)
	// JAXB object factories
	protected static edu.harvard.i2b2.crc.loader.datavo.loader.query.ObjectFactory loaderCrcOF =
			new edu.harvard.i2b2.crc.loader.datavo.loader.query.ObjectFactory();
	protected static edu.harvard.i2b2.crc.loader.datavo.fr.ObjectFactory loaderFrOF =
			new edu.harvard.i2b2.crc.loader.datavo.fr.ObjectFactory();
	protected static edu.harvard.i2b2.crc.datavo.ontology.ObjectFactory ontOF =
			new edu.harvard.i2b2.crc.datavo.ontology.ObjectFactory();
	protected static edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory i2b2OF =
			new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();
	protected static edu.harvard.i2b2.crc.datavo.pdo.ObjectFactory pdoOF =
			new edu.harvard.i2b2.crc.datavo.pdo.ObjectFactory();
	protected static edu.harvard.i2b2.crc.datavo.pdo.query.ObjectFactory queryPdoOF =
			new edu.harvard.i2b2.crc.datavo.pdo.query.ObjectFactory();
    protected static edu.harvard.i2b2.crc.datavo.setfinder.query.ObjectFactory querySetFinderOF =
            new edu.harvard.i2b2.crc.datavo.setfinder.query.ObjectFactory();

	protected I2B2Cell(String cellURL, UserAuthentication auth) {
		this.cellURL = cellURL;
		this.auth = auth;
	}
	

	
	/**
	 * Sends a request to the cell, and get back an answer.
	 * extract the body to make new request!
	 * 
	 * @param reqMessage
	 * @return
	 */
	protected ResponseMessageType requestToCell(String urlPath, RequestMessageType reqMessage) throws Exception {
		
		Logger.info("New request to cell " + cellURL);
		RequestMessageType newReqMessage = createRequestMessage(reqMessage.getMessageBody());
        OMElement reqMessageOM = msgUtil.buildOMElement(newReqMessage);
        String answerMessage = ServiceClient.sendREST(cellURL + urlPath, reqMessageOM);
        Logger.debug("Cell " + cellURL + " answered: " + answerMessage);

		// extract response
        JAXBElement responseJaxb = msgUtil.unMashallFromString(answerMessage);
        return (ResponseMessageType) responseJaxb.getValue();
	}

    protected Pair<I2b2Status, BodyType> requestToCell(String urlPath, BodyType reqBody) throws Exception {

	    // encapsulate and send request
        Logger.info("New request to cell " + cellURL);
        RequestMessageType reqMessage = createRequestMessage(reqBody);
        OMElement reqMessageOM = msgUtil.buildOMElement(reqMessage);
        String answerMessage = ServiceClient.sendREST(cellURL + urlPath, reqMessageOM);
        Logger.debug("Cell " + cellURL + " answered: " + answerMessage);

        return msgUtil.parseI2b2Response(answerMessage);
	}


	protected RequestMessageType createRequestMessage(BodyType body) {

        // setup request header
        RequestHeaderType requestHeader = i2b2OF.createRequestHeaderType();
        requestHeader.setResultWaittimeMs(MedCoUtil.getInstance().getI2b2Waittimems());

        // pack in request message
        RequestMessageType requestMessage = i2b2OF.createRequestMessageType();
        requestMessage.setMessageBody(body);
        requestMessage.setMessageHeader(auth);
        requestMessage.setRequestHeader(requestHeader);

        Logger.debug("Correctly created new i2b2 request message" + requestMessage);
        return requestMessage;
    }
	
	/**
	 * 
	 * @return the cell URL
	 */
	protected String getCellUrl() {
		return cellURL;
	}
}
