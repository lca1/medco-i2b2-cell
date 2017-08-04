package ch.epfl.lca1.medco.util.exceptions;

/**
 * Application critical error thrown in case of an unrecoverable error.
 * Should not attempt to catch.
 */
public class MedCoException extends Exception {

	private static final long serialVersionUID = -2049383438782097960L;

	public MedCoException() {
		super();
	}

	public MedCoException(String msg) {
		super(msg);
	}

	public MedCoException(String msg, Throwable t) {
		super(msg, t);
	}
}
