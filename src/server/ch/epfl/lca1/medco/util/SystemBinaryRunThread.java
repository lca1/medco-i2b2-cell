package ch.epfl.lca1.medco.util;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class SystemBinaryRunThread extends Thread {

    public enum RunState {
        NOT_STARTED, STARTED, TIMEDOUT, ERROR_INTERRUPTED, ERROR_EXIT_CODE, ERROR_IO, COMPLETED
    }

    private String[] systemCall;

    private String stdOut;

    private long timeoutSeconds;

    private RunState runState;
    private String stdIn;

    private Process process;

    public SystemBinaryRunThread(String[] systemCall, String stdOut, long timeoutSeconds) {
        this.systemCall = systemCall;
        this.stdOut = stdOut;
        this.timeoutSeconds = timeoutSeconds;

        runState = RunState.NOT_STARTED;
    }

    @Override
    public void run() {
        try {

            // start client
            Logger.info("System call: " + Arrays.toString(systemCall));
            ProcessBuilder pb = new ProcessBuilder(systemCall).redirectErrorStream(true);
            process = pb.start();
            if (process.isAlive()) {
                runState = RunState.STARTED;
            } else {
                throw new IOException("Process not alive after start (wrong binary path?)");
            }

            if (stdOut != null) {
                Logger.debug("Writing stdout: " + stdOut);
                BufferedWriter buffStdOut = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
                buffStdOut.write(stdOut);
                buffStdOut.close();
                process.getOutputStream().close();
                Logger.debug("Closed stdout");
            }

            Logger.info("Waiting for process completion...");
            boolean hasTimedOut = waitForProcess();

            // handle result
            if (hasTimedOut) {
                Logger.warn("Process timeout");
                runState = RunState.TIMEDOUT;
                process.destroy();

            } else if (process.exitValue() != 0) {
                Logger.error("Process finished with error: " + process.exitValue());
                runState = RunState.ERROR_EXIT_CODE;

            } else {
                Logger.info("Process completed successfully");
                runState = RunState.COMPLETED;
            }

        } catch (IOException e) {
            Logger.error("Process I/O error: " + e.getMessage());
            runState = RunState.ERROR_IO;
            if (process != null) {
                process.destroy();
            }

        } catch (InterruptedException e) {
            Logger.error("Process interrupted: " + e.getMessage());
            runState = RunState.ERROR_INTERRUPTED;
            process.destroy();

        } finally {
            if (process != null) {
                process.destroy();
                if (process.isAlive()) {
                    Logger.warn("Process seems to be still alive, attempting to kill it...");
                    process.destroyForcibly();
                }

                process = null;
            }
        }
    }

    /**
     * Augmented version of Process.waitFor that is accumulating output.
     *
     * @return true if the process has timed out, false otherwise
     */
    private boolean waitForProcess() throws InterruptedException {
        StringBuilder accumulatedOutput = new StringBuilder();
        long startTime = System.nanoTime();
        TimeUnit unit = TimeUnit.SECONDS;
        long rem = unit.toNanos(timeoutSeconds);

        BufferedReader buffStdIn = new BufferedReader(new InputStreamReader(process.getInputStream()));

        do {
            try {
                process.exitValue();
                readAllLines(buffStdIn, accumulatedOutput);
                stdIn = accumulatedOutput.toString();
                return false;
            } catch(IllegalThreadStateException ex) {
                if (rem > 0)
                    Thread.sleep(
                            Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
            }
            rem = unit.toNanos(timeoutSeconds) - (System.nanoTime() - startTime);
            readAllLines(buffStdIn, accumulatedOutput);
        } while (rem > 0);

        readAllLines(buffStdIn, accumulatedOutput);
        stdIn = accumulatedOutput.toString();
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
     * @return the run state
     */
    public RunState getRunState() {
        return runState;
    }

    public String getStdIn() {
        return stdIn;
    }
}
