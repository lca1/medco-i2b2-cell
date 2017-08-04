package ch.epfl.lca1.medco.i2b2.pm;

import edu.harvard.i2b2.crc.datavo.i2b2message.RequestMessageType;
import edu.harvard.i2b2.crc.datavo.pm.ProjectType;
import org.apache.axiom.om.OMElement;

import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.i2b2.I2B2Cell;
import edu.harvard.i2b2.common.exception.I2B2Exception;
//import edu.harvard.i2b2.crc.datavo.CRCJAXBUtil;
//import edu.harvard.i2b2.crc.datavo.i2b2message.BodyType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.FacilityType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.MessageHeaderType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.RequestHeaderType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.RequestMessageType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseHeaderType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseMessageType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.SecurityType;
//import edu.harvard.i2b2.crc.datavo.i2b2message.StatusType;
//import edu.harvard.i2b2.crc.datavo.pm.ConfigureType;
//import edu.harvard.i2b2.crc.datavo.pm.GetUserConfigurationType;
//import edu.harvard.i2b2.crc.datavo.pm.ObjectFactory;
//import edu.harvard.i2b2.crc.datavo.pm.ProjectType;
//import edu.harvard.i2b2.crc.datavo.pm.UserType;
//import edu.harvard.i2b2.crc.util.QueryProcessorUtil;


public class I2B2PMCell extends I2B2Cell {	
	
	// take from crc query request delegate
	// fmethod check validuser does nothing escept call the callpmutil
	
	
	

//	public static String callUserResponse(SecurityType securityType,  String projectId ) throws AxisFault, I2B2Exception {
//		RequestMessageType requestMessageType = getI2B2RequestMessage(securityType, projectId);
//		OMElement requestElement = null;
//		String response =  null;
//		try {
//			requestElement = buildOMElement(requestMessageType);
//			log.debug("CRC PM call's request xml " + requestElement);
//			 response = ServiceClient.sendREST(QueryProcessorUtil.getInstance()
//					.getProjectManagementCellUrl(), requestElement);
//			log.debug("Got Response");
//		} catch (XMLStreamException e) {
//			e.printStackTrace();
//			throw new I2B2Exception("" + StackTraceUtil.getStackTrace(e));
//		} catch (Exception e) {
//			e.printStackTrace();
//			throw new I2B2Exception("" + StackTraceUtil.getStackTrace(e));
//		} 
//
//		log.debug("Returning ProjectType");
//		return response;
//	}

	protected I2B2PMCell() {
		super(medCoUtil.getProjectManagementCellUrl(), null); // get url
	}

	/**
	 * Returns the Project part of the authentication with the PM.
	 * Contains TODO...
 	 * 
 	 * @return 
 	 * 
 	 * @throws I2B2Exception if the original request message is missing required parts
	 */
	public UserAuthentication doAuthentication(RequestMessageType origReqMessage) {
		
		if (origReqMessage == null || origReqMessage.getMessageHeader() == null || 
				origReqMessage.getMessageHeader().getSecurity() == null) {
			
			Logger.warn("Authentication failed because some parts of the original request message is missing.");
			return null;// new UserAuthentication(origReqMessage, false);
		}
		
		// construct authentication request
		//RequestMessageType authReqMessage = newReqMessageFromReqMessage(origReqMessage);
		
		
		OMElement requestElement = null;
		ProjectType projectType = null;
		
		//TODO change: have a object connecting to PM
		
		
		return null;
	}
	/*
	public static ProjectType callUserProject(SecurityType securityType,  String projectId ) throws AxisFault, I2B2Exception {
		RequestMessageType requestMessageType = getI2B2RequestMessage(securityType, projectId);
		OMElement requestElement = null;
		ProjectType projectType = null;
		try {
			requestElement = buildOMElement(requestMessageType);
			log.debug("CRC PM call's request xml " + requestElement);
			String response = ServiceClient.sendREST(QueryProcessorUtil.getInstance()
					.getProjectManagementCellUrl(), requestElement);
			log.debug("Got Response");
			projectType = getUserProjectFromResponse(response, securityType, projectId);
			log.debug("Parsed Projcet Type: " + projectType.getName());
		} catch (XMLStreamException e) {
			e.printStackTrace();
			throw new I2B2Exception("" + StackTraceUtil.getStackTrace(e));
		} catch (Exception e) {
			e.printStackTrace();
			throw new I2B2Exception("" + StackTraceUtil.getStackTrace(e));
		} 

		log.debug("Returning ProjectType");
		return projectType;
	}

	public static ProjectType getUserProjectFromResponse(String responseXml, SecurityType securityType,  String projectId)
			throws JAXBUtilException, I2B2Exception {
		JAXBElement responseJaxb = msgUtil.unMashallFromString(responseXml);

		//CRCJAXBUtil.getJAXBUtil().unMashallFromString(responseXml);
		ResponseMessageType pmRespMessageType = (ResponseMessageType) responseJaxb
				.getValue();
		log.debug("CRC's PM call response xml" + responseXml);

		ResponseHeaderType responseHeader = pmRespMessageType
				.getResponseHeader();
		StatusType status = responseHeader.getResultStatus().getStatus();
		String procStatus = status.getType();
		String procMessage = status.getValue();

		if (procStatus.equals("ERROR")) {
			log.info("PM Error reported by CRC web Service " + procMessage);
			throw new I2B2Exception("PM Error reported by CRC web Service "
					+ procMessage);
		} else if (procStatus.equals("WARNING")) {
			log.info("PM Warning reported by CRC web Service" + procMessage);
			throw new I2B2Exception("PM Warning reported by CRC web Service"
					+ procMessage);
		}

		JAXBUnWrapHelper helper = new JAXBUnWrapHelper();
		ConfigureType configureType = (ConfigureType) helper.getObjectByClass(
				pmRespMessageType.getMessageBody().getAny(),
				ConfigureType.class);
		UserType userType = configureType.getUser();
		List<ProjectType> projectTypeList = userType.getProject();

		ProjectType projectType = null;
		if (projectTypeList != null && projectTypeList.size() > 0) {
			for (ProjectType pType : projectTypeList) {
				if (pType.getId().equalsIgnoreCase(projectId)) {
					projectType = pType;

					break;
				}
			}
			if (projectType == null) {
				throw new I2B2Exception("User not registered to the project["
						+ projectId + "]");
			}
		}

		return projectType;
	}*/

	





}
