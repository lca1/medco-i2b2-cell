package ch.epfl.lca1.medco.util.exceptions;

import edu.harvard.i2b2.common.exception.I2B2Exception;

/**
 * Exception relating to the XML handling of I2B2 messages.
 */
public class I2B2XMLException extends I2B2Exception {

	private static final long serialVersionUID = -6882724847107866225L;
	
	public I2B2XMLException() {
	}

    public I2B2XMLException(String message, Exception e) {
    	super(message, e);
    }

    public I2B2XMLException(String message) {
        super(message);
    }
}
