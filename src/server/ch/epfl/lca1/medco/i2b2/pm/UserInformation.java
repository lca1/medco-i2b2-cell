package ch.epfl.lca1.medco.i2b2.pm;

import edu.harvard.i2b2.crc.datavo.pm.RoleType;
import edu.harvard.i2b2.crc.datavo.pm.RolesType;

// todo: implement me
public class UserInformation {

    private MedCoI2b2MessageHeader auth;
    private String userPublicKey;


    public MedCoI2b2MessageHeader getAuth() {
        return auth;
    }

    public double getPrivacyBudget() {
        return Double.MAX_VALUE;
    }

    public boolean isAuthenticated() {
        // todo: implement me
        return true;
    }

    public RolesType getRoles() {
        // todo: implement me
        RolesType tmpRoles = new RolesType();
        RoleType role = new RoleType();
        role.setProjectId("Demo");
        role.setRole("DATA_AGG");
        role.setUserName("demo");
        tmpRoles.getRole().add(role);
        return tmpRoles;
    }

    public String getUsername() {
        // todo: implement me
        return "Jean Ca";
    }

    public String getUserPublicKey() {
        return this.userPublicKey;
    }

    public void setUserPublicKey(String userPublicKey) {
        this.userPublicKey = userPublicKey;
    }

}
