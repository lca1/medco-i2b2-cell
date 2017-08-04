package ch.epfl.lca1.medco.i2b2;

/**
 * Created by misbach on 12.06.17.
 */
public enum I2b2Status {

    DONE,
    PENDING,
    ERROR;

    private String statusMessage;

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
}
