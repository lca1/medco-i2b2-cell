package ch.epfl.lca1.medco.util.exceptions;

/**
 * Application critical error thrown in case of an unrecoverable error.
 * Should not attempt to catch.
 */
public class MedCoError extends Error {

	private static final long serialVersionUID = -2049383438782097960L;
	
	public MedCoError() {
		super();
	}
	
	public MedCoError(String msg) {
		super(msg);
	}
	
	public MedCoError(String msg, Throwable t) {
		super(msg, t);
	}
}
