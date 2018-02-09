package ch.epfl.lca1.medco.util.exceptions;

/**
 * Generic MedCo exception.
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
