/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the i2b2 Software License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Rajesh Kuttan
 */
package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.i2b2.pm.I2B2PMCell;
import ch.epfl.lca1.medco.i2b2.pm.UserInformation;
import ch.epfl.lca1.medco.unlynx.UnlynxClient;
import ch.epfl.lca1.medco.util.Constants;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.Timers;
import ch.epfl.lca1.medco.util.exceptions.MedCoError;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.crc.datavo.setfinder.query.PanelType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryDefinitionType;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

//

//todo doc: https://github.com/chb/shrine/tree/master/doc

/**
 * Represents a query to MedCo.
 * From the XML query (in CRC format), parse to extract the sensitive attributes, 
 * make query to CRC for non-sensitive attributes, get the patient set from CRC,
 * query the cothority with the patient sets and sensitive attributes and answer.
 *
 *
 * everything under that sohuld not use the config!!
 */
public class StandardQuery {

    private I2B2QueryRequest queryRequest;
    private I2B2CRCCell crcCell;
    private I2B2PMCell pmCell;
    private UnlynxClient unlynxClient;
    private Timers timers;



    //int resultMode, String clientPubKey, long timoutSeconds
	public StandardQuery(I2B2QueryRequest request,
                         String unlynxBinPath, String unlynxGroupFilePath, int unlynxDebugLevel, int unlynxEntryPointIdx,
                         int unlynxProofsFlag, long unlynxTimeoutSeconds,
                         String crcCellUrl, String pmCellUrl) throws I2B2Exception {
		this.queryRequest = request;
		unlynxClient = new UnlynxClient(unlynxBinPath, unlynxGroupFilePath, unlynxDebugLevel, unlynxEntryPointIdx, unlynxProofsFlag, unlynxTimeoutSeconds);
		crcCell = new I2B2CRCCell(crcCellUrl, queryRequest.getMessageHeader());
		pmCell = new I2B2PMCell(pmCellUrl, queryRequest.getMessageHeader());
		timers = new Timers();
	}
	
	/**
	 * Implements the high-level step-by-step logic of the MedCo query.
     *todo
	 * @return the query answer in CRC XML format.
     * @throws MedCoException
     * @throws I2B2Exception
	 */
	public I2B2QueryResponse executeQuery() throws MedCoException, I2B2Exception {
	    timers.resetTimers();
	    timers.get("overall").start();

	    // get user information (auth., privacy budget, authorizations, public key)
        // todo: get and check budget query / user
        // todo: get user permissions
        timers.get("steps").start("User information retrieval");
        UserInformation user = pmCell.getUserInformation(queryRequest.getMessageHeader());
        if (!user.isAuthenticated()) {
            Logger.warn("Authentication failed for user " + user.getUsername());
            // todo: proper auth failed response
            return null;
        }
        QueryType queryType = QueryType.resolveUserPermission(user.getRoles());
        timers.get("steps").stop();

        // retrieve the encrypted query terms
        timers.get("steps").start("Query parsing/splitting");
        List<String> encryptedQueryItems = getEncryptedQueryTerms();
        timers.get("steps").stop();

        // intercept test query from SHRINE and bypass unlynx
        if (encryptedQueryItems.contains(Constants.CONCEPT_NAME_TEST_FLAG)) {
            Logger.info("Intercepted SHRINE status query (" + queryRequest.getQueryName() + ").");
            replaceEncryptedQueryTerms(encryptedQueryItems);
            return crcCell.queryRequest(queryRequest);
        }

        // query unlynx to tag the query terms
        timers.get("steps").start("Query tagging");
        List<String> taggedItems = unlynxClient.computeDistributedDetTags(queryRequest.getQueryName(), encryptedQueryItems);
        timers.addAdditionalTimes(unlynxClient.getLastTimingMeasurements());
        timers.get("steps").stop();

        // replace the query terms, query i2b2 with the original clear query terms + the tagged ones
        timers.get("steps").start("i2b2 query");
        replaceEncryptedQueryTerms(taggedItems);
        queryRequest.setOutputTypes(new String[]{"PATIENTSET", "PATIENT_COUNT_XML"});
        I2B2QueryResponse i2b2Response = crcCell.queryRequest(queryRequest);
        timers.get("steps").stop();

        // retrieve the patient set, including the encrypted dummy flags
        timers.get("steps").start("i2b2 patient set retrieval");
        Pair<List<String>, List<String>> patientSet = crcCell.queryForPatientSet(i2b2Response.getPatientSetId(), true);
        timers.get("steps").stop();

        String aggResult;
        switch (queryType) {

            case AGGREGATED_PER_SITE:
                aggResult = unlynxClient.aggregateData(queryRequest.getQueryName(), user.getUserPublicKey(), patientSet.getValue1());
                timers.addAdditionalTimes(unlynxClient.getLastTimingMeasurements());

                break;

            case OBFUSCATED_PER_SITE:
            case AGGREGATED_TOTAL:
            default:
                throw new MedCoError("Query type not supported yet.");

        }

        i2b2Response.resetResultInstanceListToEncryptedCountOnly();
        timers.get("overall").stop();
        i2b2Response.setQueryResults(user.getUserPublicKey(), aggResult, timers.generateFullReport());

        Logger.info("MedCo query successful (" + queryRequest.getQueryName() + ").");
        return i2b2Response;
		
	}


    /**
     * Replace within the queryRequest, in top-bottom order, the encrypted query terms with their tagged equivalent.
     *
     * @param taggedQueryTerms the tagged query terms to use for replacement.
     */
    private void replaceEncryptedQueryTerms(List<String> taggedQueryTerms) {
        QueryDefinitionType qd = queryRequest.getQueryDefinition();
        int encTermCount = 0;

        for (int p = 0; p < qd.getPanel().size(); p++) {
            PanelType panel = qd.getPanel().get(p);

            int nbItems = panel.getItem().size();
            for (int i = 0; i < nbItems; i++) {

                // replace encrypted item with its tagged version
                Matcher medcoKeyMatcher = Constants.REGEX_QUERY_KEY_ENC.matcher(panel.getItem().get(i).getItemKey());
                if (medcoKeyMatcher.matches()) {
                    Logger.debug("Replacing " + panel.getItem().get(i).getItemKey() + " by " + taggedQueryTerms.get(encTermCount) +
                            " on (panel=" + p + ", item=" + i + ")" );

                    panel.getItem().get(i).setItemKey(Constants.CONCEPT_PATH_TAGGED_PREFIX + taggedQueryTerms.get(encTermCount++) + "\\");
                }
            }
        }

        // check the provided taggedQueryTerms match the number of encrypted terms
        if (encTermCount != taggedQueryTerms.size()) {
            Logger.warn("Mismatch in provided number of tagged items (" + taggedQueryTerms.size() + ") and number of encrypted items in query (" + encTermCount + ")");
        }
    }

    /**
     * @return the list of encrypted terms from the queryRequest in top-bottom order.
     */
    private List<String> getEncryptedQueryTerms() {
        QueryDefinitionType qd = queryRequest.getQueryDefinition();
        List<String> extractedItems = new ArrayList<>();

        for (int p = 0; p < qd.getPanel().size(); p++) {
            PanelType panel = qd.getPanel().get(p);

            int nbItems = panel.getItem().size();
            for (int i = 0; i < nbItems; i++) {

                // check if item is clear or encrypted, extract and generate predicate if yes
                Matcher medcoKeyMatcher = Constants.REGEX_QUERY_KEY_ENC.matcher(panel.getItem().get(i).getItemKey());
                if (medcoKeyMatcher.matches()) {
                    extractedItems.add(medcoKeyMatcher.group(1));
                    Logger.debug("Returned item " + extractedItems.get(extractedItems.size() - 1) + "; panel=" + p + ", item=" + i);
                }
            }
        }

        Logger.info("Returned " + extractedItems.size() + " encrypted query terms for query " + queryRequest.getQueryName());
        return extractedItems;
    }
}
