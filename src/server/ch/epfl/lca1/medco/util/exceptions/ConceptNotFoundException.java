package ch.epfl.lca1.medco.util.exceptions;

/**
 * Created by misbach on 17.06.17.
 */
public class ConceptNotFoundException extends UnlynxException {

    public ConceptNotFoundException() {
        super();
    }

    public ConceptNotFoundException(String message, Exception e) {
        super(message, e);
    }

    public ConceptNotFoundException(String message) {
        super(message);
    }
}
