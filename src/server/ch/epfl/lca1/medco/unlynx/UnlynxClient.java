package ch.epfl.lca1.medco.unlynx;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;

/**
 * Manages connection to the local Unlynx client.
 * It runs in a separate to execute the binary specified by the configuration.
 *
 * Start query by using executeQuery() and then join() to wait for the process to end.
 * Next check with getQueryState() the state of the query and finally get the end result with getQueryResult().
 */
public class UnlynxClient extends Thread {

    /** Query that will be executed. */
	private UnlynxQuery query;

	/** Result of the query after the execution. */
	private UnlynxQueryResult queryResult;
	
	private static MedCoUtil util = MedCoUtil.getInstance();

	/** Enum describing the state of the query. */
	public enum QueryState {
		NOT_STARTED, STARTED, TIMEDOUT, ERROR_INTERRUPTED, ERROR_UNLYNX, ERROR_COMM_UNLYNX, ERROR_INVALID_RESULT, COMPLETED
	}

	/** State of the query. */
	private QueryState queryState;

	/**
	 * Private constructor to allow initialization only through startQuery().
	 * 
	 * @param query the unlynx query to execute
	 */
	private UnlynxClient(UnlynxQuery query) {
		this.query = query;
		this.queryState = QueryState.NOT_STARTED;
	}
	
	/**
	 * Execute the query by starting the thread.
     * Use UnlynxClient.join() to wait for query completion.
     *
     * @param query the query to execute
     * @return the (probably) running client
	 */
	public static UnlynxClient executeQuery(UnlynxQuery query) {
		if (query == null) {
			throw Logger.error(new IllegalArgumentException("query can not be null"));
		}
		
		UnlynxClient client = new UnlynxClient(query);
		client.start();
		return client;
	}
	
	/**
	 * Construct the binary system call of the Unlynx client.
	 * 
	 * @return array of tokens for system call to the Unlynx client
	 */
	private String[] constructSystemCall() {
		ArrayList<String> arr = new ArrayList<>();
		
		arr.add(util.getUnlynxBinPath());

		// unlynx configuration
		arr.add("-d");
		arr.add(util.getUnlynxDebugLevel() + "");

		arr.add("run");
        arr.add("-f");
		arr.add(util.getUnlynxGroupFilePath());
		arr.add("--entryPointIdx");
		arr.add(util.getUnlynxEntryPointIdx() + "");
		arr.add("--proofs");
		arr.add(util.getUnlynxProofsFlag() + "");

		Logger.info("Unlynx binary call is: " + arr.toString());
		return arr.toArray(new String[arr.size()]);
	}
	
	/**
	 * Run thread to execute query to Unlynx.
	 * Execute the unlynxI2b2 client binary and send the query via stdin, get the result via stdout.
     * Must be used through executeQuery().
	 */
	@Override
	public void run() {
		Process p = null;
        try {
        	
        	// start client
        	Logger.info("Calling Unlynx client (query " + query.getQueryID() + ")");
        	Logger.debug("Query: " + query);
        	ProcessBuilder pb = new ProcessBuilder(constructSystemCall()).redirectErrorStream(true);
        	p = pb.start();
        	if (p.isAlive()) {
        		queryState = QueryState.STARTED;
        	} else {
        		throw new IOException("Query process not alive after start (wrong binary path?)");
        	}
            
            Logger.debug("Writing query to input of process as UTF-8 string.");
            BufferedWriter buffStdOut = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), "UTF-8"));
            buffStdOut.write(query.toUnlynxI2b2XML());
			Logger.debug("Query written, closing the streams to flush");
			buffStdOut.close();
			Logger.debug("buffStdOut flushed");
			p.getOutputStream().close();
			Logger.debug("p.getOutputStream flushed");

			Logger.info("Waiting for query completion (query " + query.getQueryID() + ")");
			StringBuilder accumulatedOutput = new StringBuilder();
        	boolean hasTimedOut = waitForProcess(p, query.getTimeoutSeconds(), accumulatedOutput);
            
            // handle result
        	if (hasTimedOut) {
        		Logger.warn("Query timeout (query " + query.getQueryID() + ")");
				queryResult = new UnlynxQueryResult(accumulatedOutput.toString());
				Logger.debug("Query result parsing successful (query " + query.getQueryID() + ")");
				queryState = QueryState.TIMEDOUT;
        		p.destroy();
        		
        	} else if (p.exitValue() != 0) { 
    			Logger.error("Query finished with error (query " + query.getQueryID() + ")");
				queryResult = new UnlynxQueryResult(accumulatedOutput.toString());
				Logger.debug("Query result parsing successful (query " + query.getQueryID() + ")");
				queryState = QueryState.ERROR_UNLYNX;
        		
    		} else {
        		Logger.info("Query completed successfully (query " + query.getQueryID() + ")");
        		queryResult = new UnlynxQueryResult(accumulatedOutput.toString());
        		Logger.debug("Query result parsing successful (query " + query.getQueryID() + ")");
        		queryState = QueryState.COMPLETED;
    		}
        	
        } catch (IOException e) {
    		Logger.error("Query error while communicating with Unlynx (query " + query.getQueryID() + "): " + e.getMessage());
    		queryState = QueryState.ERROR_COMM_UNLYNX;
    		if (p != null) {
                p.destroy();
            }
            
        } catch (InterruptedException e) {
    		Logger.error("Query interrupted (query " + query.getQueryID() + "): " + e.getMessage());
    		queryState = QueryState.ERROR_INTERRUPTED;
        	p.destroy();
        	
        } catch (I2B2XMLException e) {
			Logger.error("Query result could not be parsed (query " + query.getQueryID() + "): " + e.getMessage());
    		queryState = QueryState.ERROR_INVALID_RESULT;

		} finally {
        	if (p != null && p.isAlive()) {
        		Logger.warn("Unlynx client process seems to be still alive, attempting to kill it...");
        		p.destroyForcibly();
        	}
        }
	}

    /**
     * Augmented version of Process.waitFor.
     *
     * @return true if the process has timed out, false otherwise
     */
	private boolean waitForProcess(Process p, long timeoutSec, StringBuilder accumulatedOutput) throws InterruptedException {
		long startTime = System.nanoTime();
		TimeUnit unit = TimeUnit.SECONDS;
        long rem = unit.toNanos(timeoutSec);

        BufferedReader buffStdIn = new BufferedReader(new InputStreamReader(p.getInputStream()));

		do {
			try {
				p.exitValue();
				readAllLines(buffStdIn, accumulatedOutput);
				return false;
			} catch(IllegalThreadStateException ex) {
				if (rem > 0)
					Thread.sleep(
							Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
			}
			rem = unit.toNanos(timeoutSec) - (System.nanoTime() - startTime);
			readAllLines(buffStdIn, accumulatedOutput);
		} while (rem > 0);

		readAllLines(buffStdIn, accumulatedOutput);
		return true;
	}

    /**
     * Read all available lines of the {@link BufferedReader} and accumulate then in the {@link StringBuilder}.
     * In case of failure, fails silently.
     *
     * @param buffStdIn
     * @param accumulatedOutput
     */
	private void readAllLines(BufferedReader buffStdIn, StringBuilder accumulatedOutput) {
	    try {
            if (buffStdIn.ready()) {
                while (buffStdIn.ready()) {
                    String line = buffStdIn.readLine();
                    if (line != null) {
                        accumulatedOutput.append(line);
                        Logger.debug("stdin line: " + line);
                    }
                }
            }
        } catch (IOException e) {
	        Logger.warn("readAllLines got IOException", e);
        }
    }

	public void waitForCompletion() {
		try {
			this.join();
		} catch (InterruptedException e) {
			Logger.warn(e);
		}
	}
    /**
     * @return the query state
     */
	public QueryState getQueryState() {
	    return queryState;
	}

    /**
     * @return the query result if the query has a query result, null if not
     */
    public UnlynxQueryResult getQueryResult() {
        switch (queryState) {
            case COMPLETED:
            case TIMEDOUT:
            case ERROR_UNLYNX:
                return queryResult;

            default:
                return null;
        }
    }
}
