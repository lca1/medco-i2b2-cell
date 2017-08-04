package ch.epfl.lca1.medco.i2b2.pm;

import edu.harvard.i2b2.crc.datavo.i2b2message.*;
import ch.epfl.lca1.medco.util.MedCoUtil;

/**
 * Simple wrapper around the {@link MessageHeaderType} class that contains the I2B2 credentials.
 */
public class UserAuthentication extends MessageHeaderType {

    private static MedCoUtil util = MedCoUtil.getInstance();

    private static final String SENDING_FACILITY_NAME = "i2b2_medco",
                                RECEIVING_FACILITY_NAME = "i2b2",
                                RECEIVING_APPLICATION = "i2b2 cell",
                                RECEIVING_APPLICATION_VERSION = "1.7";

	public UserAuthentication(String domainId, String projectId, String username,
							  boolean passwordIsToken, int tokenTimeoutMs, String passwordValue) {
        // XXX message control id [messagenum + sessionid] not implemented

		SecurityType security = new SecurityType();
		security.setDomain(domainId);
		security.setUsername(username);

		PasswordType password = new PasswordType();
		password.setIsToken(passwordIsToken);
		password.setTokenMsTimeout(tokenTimeoutMs);
		password.setValue(passwordValue);
		security.setPassword(password);
		setSecurity(security);

		ApplicationType sendingApp = new ApplicationType();
		sendingApp.setApplicationName(util.getApplicationName()); //"MedCo cell"
		sendingApp.setApplicationVersion(util.getApplicationVersion()); // "0.01"
		setSendingApplication(sendingApp);

		ApplicationType receivingApp = new ApplicationType();
		receivingApp.setApplicationName(RECEIVING_APPLICATION);
		receivingApp.setApplicationVersion(RECEIVING_APPLICATION_VERSION);
		setReceivingApplication(receivingApp);

		FacilityType sendingFacility = new FacilityType();
        sendingFacility.setFacilityName(SENDING_FACILITY_NAME);
		setSendingFacility(sendingFacility);

        FacilityType recvFacility = new FacilityType();
        recvFacility.setFacilityName(RECEIVING_FACILITY_NAME);
		setReceivingFacility(recvFacility);

		setProjectId(projectId);
	}

	public UserAuthentication(MessageHeaderType messageHeader) {
		this(messageHeader.getSecurity().getDomain(),
                messageHeader.getProjectId(),
                messageHeader.getSecurity().getUsername(),
                messageHeader.getSecurity().getPassword().isIsToken(),
                messageHeader.getSecurity().getPassword().getTokenMsTimeout() == null ?
                        0 : messageHeader.getSecurity().getPassword().getTokenMsTimeout(),
                messageHeader.getSecurity().getPassword().getValue());
	}
}
