package ch.epfl.lca1.medco.util.exceptions;

/**
 * Created by misbach on 14.06.17.
 */
public class UnlynxException extends MedCoException {

    public UnlynxException() {
        super();
    }

    public UnlynxException(String message, Exception e) {
        super(message, e);
    }

    public UnlynxException(String message) {
        super(message);
    }
}
