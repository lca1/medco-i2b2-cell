package ch.epfl.lca1.medco.unlynx;

import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.XMLUtil;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Allows to encrypt data, using the public key of the collective authority represented by the .toml specified in
 * the configuration. Achieves the encryption by calling the Unlynx binary via a system call.
 */
public class UnlynxEncrypt {

    /**
     * Process builder that is reused to invoke a new call to the unlynx binary.
     */
    private ProcessBuilder processBuilder;

    /**
     * Index of the value to be encrypted in the list of tokens representing the command to execute.
     */
    private int commandValueIdx;

    /**
     * Default timeout value for the execution of unlynx binary.
     */
    private static int processTimeoutSeconds = 5;

	private static MedCoUtil util = MedCoUtil.getInstance();

    // name of XML element (definition)
    private static String EL_NAME_ROOT = "encrypted";

    // XML tags
    private static String XML_START_TAG = "<" + EL_NAME_ROOT + ">";
    private static String XML_END_TAG = "</" + EL_NAME_ROOT + ">";

	/**
	 * Construct new UnlynxEncrypt.
	 */
	public UnlynxEncrypt() {
	    this.processBuilder = new ProcessBuilder(constructSystemCall());
	    this.commandValueIdx = processBuilder.command().size() - 1;
	}
	
	/**
	 * Construct the binary system call of the Unlynx encrypt command.
	 * 
	 * @return array of tokens for system call to the Unlynx encrypt command
	 */
	private String[] constructSystemCall() {
		ArrayList<String> arr = new ArrayList<>();
		
		arr.add(util.getUnlynxBinPath());

		// unlynx configuration
		arr.add("-d");
		arr.add(util.getUnlynxDebugLevel() + "");

		arr.add("encrypt");
        arr.add("-f");
		arr.add(util.getUnlynxGroupFilePath());

		arr.add("--");
        arr.add("dummy_place_holder");

		Logger.info("Unlynx binary call is: " + arr.toString());
		return arr.toArray(new String[arr.size()]);
	}

    /**
     * Call the Unlynx encrypt binary to encrypt the integer with the key of the collective authority.
     *
     * @param intToEncrypt value to encrypt
     * @return encrypted value, encoded in base64
     */
	public String encryptInt(long intToEncrypt) throws IOException {
        Logger.debug("Calling Unlynx encrypt");
        processBuilder.command().set(commandValueIdx, intToEncrypt + "");
        Logger.debug("Unlynx binary call is: " + processBuilder.command());

        Process p = null;
        try {
            p = processBuilder.start();
            boolean hasTimedOut = !p.waitFor(processTimeoutSeconds, TimeUnit.SECONDS);

            if (hasTimedOut) {
                throw Logger.error(new IOException("Encryption timed out"));

            } else if (p.exitValue() != 0) {
                BufferedReader buffStdErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                for (Iterator<String> it = buffStdErr.lines().iterator(); it.hasNext() ;) {
                    Logger.error("Unlynx stderr:" + it.next());
                }
                throw Logger.error(new IOException("Encrypt finished with error"));

            } else {
                String encryptedVal = XMLUtil.xmlStringFromStream(
                        p.getInputStream(), XML_START_TAG, XML_END_TAG, true);

                Logger.debug("Encryption success");
                return encryptedVal;
            }

        } catch (I2B2XMLException | InterruptedException e) {
            throw Logger.error(new IOException(e));
        } finally {
            if (p != null && p.isAlive()) {
                Logger.warn("Unlynx encrypt process seems to be still alive, attempting to kill it...");
                p.destroyForcibly();
            }
        }
    }
}
