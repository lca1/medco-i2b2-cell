package ch.epfl.lca1.medco.util;

import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.commons.logging.Log;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;

/**
 * Static log class, supports logging of the following severities:
 * fatal, error, warn, info, debug, trace
 * 
 * Use a {@link SecurityManager} to automatically get the caller class.
 *
 * Should be configured via a side channel (e.g. log4j.properties file server-side, or programmatically client-side)
 * 
 * @author Mickael Misbach
 */
public class Logger {
	
	/**
	 * Internal class used to access the caller class.
	 * 
	 * @see <a href="http://blog.vogella.com/2014/10/22/accessing-the-caller-information-of-a-method-in-java-code/comment-page-1/#comment-48908">Inspiration</a>
	 */
	private static class CallerProvider extends SecurityManager {
		
		/**
		 * Depth in the stack to get the caller class.
		 * 0: CallerProvider#getClassContext, 
		 * 1: CallerProvider#getCallerClass, 
		 * 2: Logger (one of the logging methods),
		 * 3: the outside caller class
		 */
		private static final int STACK_DEPTH = 3;

		private Class<?> getCallerClass() {
			return getClassContext()[STACK_DEPTH];
		}
	}
	
	/**
	 * Static class, cannot be instanced.
	 */
	private Logger() { }
	
	/**
	 * Instance of the log factory used to generate the {@link Log} instances.
	 */
	private static LogFactory logFactory = LogFactory.getFactory();

	/**
	 * Instance of the caller provider class.
	 */
	private static CallerProvider callerProvider = new CallerProvider();


    // ----------------------------------------------------
    // level fatal
    // ----------------------------------------------------

    /**
	 * Log a fatal message.
	 * 
	 * @param msg the log message
	 */
	public static void fatal(String msg) {
		logFactory.getInstance(callerProvider.getCallerClass()).fatal(msg);
	}
	
	/**
	 * Log a fatal throwable.
	 * 
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T fatal(T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).fatal(t.getMessage(), t);
		return t;
	}

    /**
     * Log a fatal message and throwable.
     *
     * @param msg the log message
     * @param t the log throwable
     * @return the throwable
     */
    public static <T extends Throwable> T fatal(String msg, T t) {
        logFactory.getInstance(callerProvider.getCallerClass()).fatal(msg, t);
        return t;
    }

    // ----------------------------------------------------
    // level error
    // ----------------------------------------------------

    /**
	 * Log an error message.
	 * 
	 * @param msg the log message
	 */
	public static void error(String msg) {
		logFactory.getInstance(callerProvider.getCallerClass()).error(msg);
	}
	
	/**
	 * Log an error throwable.
	 * 
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T error(T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).error(t.getMessage(), t);
		return t;
	}

    /**
     * Log an error message and throwable.
     *
     * @param msg the log message
     * @param t the log throwable
     * @return the throwable
     */
    public static <T extends Throwable> T error(String msg, T t) {
        logFactory.getInstance(callerProvider.getCallerClass()).error(msg, t);
        return t;
    }

    // ----------------------------------------------------
    // level warn
    // ----------------------------------------------------

	/**
	 * Log a warn message.
	 * 
	 * @param msg the log message
	 */
	public static void warn(String msg) {
		logFactory.getInstance(callerProvider.getCallerClass()).warn(msg);
	}
	
	/**
	 * Log a warn throwable.
	 * 
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T warn(T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).warn(t.getMessage(), t);
		return t;
	}

    /**
     * Log a warn message and throwable.
     *
     * @param msg the log message
     * @param t the log throwable
     * @return the throwable
     */
    public static <T extends Throwable> T warn(String msg, T t) {
        logFactory.getInstance(callerProvider.getCallerClass()).warn(msg, t);
        return t;
    }

    // ----------------------------------------------------
    // level info
    // ----------------------------------------------------

    /**
	 * Log an info message.
	 * 
	 * @param msg the log message
	 */
	public static void info(String msg) {
		logFactory.getInstance(callerProvider.getCallerClass()).info(msg);
	}
	
	/**
	 * Log an info throwable.
	 * 
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T info(T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).info(t.getMessage(), t);
		return t;
	}

    /**
     * Log an info message and throwable.
     *
     * @param msg the log message
     * @param t the log throwable
     * @return the throwable
     */
    public static <T extends Throwable> T info(String msg, T t) {
        logFactory.getInstance(callerProvider.getCallerClass()).info(msg, t);
        return t;
    }

	// ----------------------------------------------------
	// level debug
	// ----------------------------------------------------

	/**
	 * Log a debug message.
	 * 
	 * @param msg the log message
	 */
	public static void debug(String msg) {
		logFactory.getInstance(callerProvider.getCallerClass()).debug(msg);
	}
	
	/**
	 * Log a debug throwable.
	 * 
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T debug(T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).debug(t.getMessage(), t);
		return t;
	}

	/**
	 * Log a debug message and throwable.
	 *
	 * @param msg the log message
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T debug(String msg, T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).debug(msg, t);
		return t;
	}

	// ----------------------------------------------------
	// level trace
	// ----------------------------------------------------

	/**
	 * Log a trace message.
	 * 
	 * @param msg the log message
	 */
	public static void trace(String msg) {
		logFactory.getInstance(callerProvider.getCallerClass()).trace(msg);
	}
	
	/**
	 * Log a trace throwable.
	 * 
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T trace(T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).trace(t.getMessage(), t);
		return t;
	}

	/**
	 * Log a trace message and throwable.
	 *
	 * @param msg the log message
	 * @param t the log throwable
	 * @return the throwable
	 */
	public static <T extends Throwable> T trace(String msg, T t) {
		logFactory.getInstance(callerProvider.getCallerClass()).trace(msg, t);
		return t;
	}
}
