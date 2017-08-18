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
import ch.epfl.lca1.medco.i2b2.pm.UserAuthentication;
import ch.epfl.lca1.medco.i2b2.pm.UserInformation;
import ch.epfl.lca1.medco.unlynx.UnlynxClient;
import ch.epfl.lca1.medco.util.Constants;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.Timers;
import ch.epfl.lca1.medco.util.exceptions.MedCoError;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.setfinder.query.PanelType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryDefinitionType;
import org.javatuples.Pair;

import java.io.StringWriter;
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



    //int resultMode, String clientPubKey, long timoutSeconds
	public StandardQuery(I2B2QueryRequest request,
                         String unlynxBinPath, String unlynxGroupFilePath, int unlynxDebugLevel, int unlynxEntryPointIdx,
                         int unlynxProofsFlag, long unlynxTimeoutSeconds,
                         String crcCellUrl, String pmCellUrl, UserAuthentication medcoI2b2Auth) throws I2B2Exception {
		this.queryRequest = request;
		unlynxClient = new UnlynxClient(unlynxBinPath, unlynxGroupFilePath, unlynxDebugLevel, unlynxEntryPointIdx, unlynxProofsFlag, unlynxTimeoutSeconds);
		crcCell = new I2B2CRCCell(crcCellUrl, medcoI2b2Auth);
		pmCell = new I2B2PMCell(pmCellUrl, medcoI2b2Auth);
	}
	
	/**
	 * 
	 * @return the query answer in CRC XML format.
	 * @throws JAXBUtilException 
	 */
	public I2B2QueryResponse executeQuery() throws MedCoException, I2B2Exception {
	    Timers.resetTimers();
	    Timers.get("overall").start();

	    // get user information (auth., privacy budget, authorizations, public key)
        // todo: get and check budget query / user
        // todo: get user permissions
        Timers.get("steps").start("User information retrieval");
        UserInformation user = pmCell.getUserInformation(queryRequest.getMessageHeader());
        if (!user.isAuthenticated()) {
            Logger.warn("Authentication failed for user " + user.getUsername());
            // todo: proper auth failed response
            return null;
        }
        QueryType queryType = QueryType.resolveUserPermission(user.getRoles());
        Timers.get("steps").stop();

        // retrieve the encrypted query terms
        Timers.get("steps").start("Query parsing/splitting");
        List<String> encryptedQueryItems = extractEncryptedQueryTerms(false, false);
        Timers.get("steps").stop();

        // query unlynx to tag the query terms
        Timers.get("steps").start("Query tagging");
        List<String> taggedItems = unlynxClient.computeDistributedDetTags(queryRequest.getQueryName(), encryptedQueryItems);
        Timers.addAdditionalTimes(unlynxClient.getLastTimingMeasurements());
        Timers.get("steps").stop();

        // replace the query terms, query i2b2 with the original clear query terms + the tagged ones
        Timers.get("steps").start("i2b2 query");
        replaceEncryptedQueryTerms(taggedItems);
        I2B2QueryResponse i2b2Response = crcCell.queryRequest(queryRequest);
        Timers.get("steps").stop();

        // retrieve the patient set, including the encrypted dummy flags
        Timers.get("steps").start("i2b2 patient set retrieval");
        Pair<List<String>, List<String>> patientSet = crcCell.queryForPatientSet(i2b2Response.getPatientSetId(), true);
        Timers.get("steps").stop();

        String aggResult;
        switch (queryType) {

            case AGGREGATED_PER_SITE:
                aggResult = unlynxClient.aggregateData(queryRequest.getQueryName(), user.getUserPublicKey(), patientSet.getValue1());
                Timers.addAdditionalTimes(unlynxClient.getLastTimingMeasurements());

                break;

            case OBFUSCATED_PER_SITE:
            case AGGREGATED_TOTAL:
            default:
                throw new MedCoError("Query type not supported yet.");

        }

        i2b2Response.resetResultInstanceListToEncryptedCountOnly();
        i2b2Response.setQueryResults(user.getUserPublicKey(), aggResult, Timers.generateFullReport());

        Logger.info("MedCo query successful (" + queryRequest.getQueryName() + ").");
        Timers.get("overall").stop();
        return i2b2Response;
		
	}


    /**
     * TODO
     * No checks on panels are done (i.e. if they contain mixed query types or not)
     *
     * @param taggedItems
     * @throws MedCoException
     */
    private void replaceEncryptedQueryTerms(List<String> taggedItems) throws MedCoException {
        QueryDefinitionType qd = queryRequest.getQueryDefinition();
        int encTermCount = 0;

        // iter on the panels
        for (int p = 0; p < qd.getPanel().size(); p++) {
            PanelType panel = qd.getPanel().get(p);

            // iter on the items
            int nbItems = panel.getItem().size();
            for (int i = 0; i < nbItems; i++) {

                // replace encryptem item with its tagged version
                Matcher medcoKeyMatcher = Constants.REGEX_QUERY_KEY_ENC.matcher(panel.getItem().get(i).getItemKey());
                if (medcoKeyMatcher.matches()) {
                    panel.getItem().get(i).setItemKey(medcoKeyMatcher.replaceFirst(
                            "$1" + Constants.CONCEPT_PATH_NODE_TAGGED + "$3" + taggedItems.get(encTermCount++) + "$5"));
                }

            }
        }

        // check the provided taggedItems match the number of encrypted terms
        if (encTermCount != taggedItems.size()) {
            Logger.warn("Mismatch in provided number of tagged items (" + taggedItems.size() + ") and number of encrypted items in query (" + encTermCount + ")");
        }
    }

    /**
     * Extract from the i2b2 query the sensitive / encrypted items recognized by the prefix defined in {@link Constants}.
     * Accepts only panels fully clear or encrypted, i.e. no mix is allowed.
     * <p>
     * The predicate, if returned, has the following format:
     * (exists(v0, r) || exists(v1, r)) &amp;&amp; (exists(v2, r) || exists(v3, r)) &amp;&amp; exists(v4, r)
     *
     * @param removePanels removes the encrypted panels when encountered if true
     * @param getPredicate adds at the end of the returned list the corresponding predicate if true
     * @return the list of encrypted query terms and optionally the corresponding predicate
     * @throws MedCoException if a panel contains mixed clear and encrypted query terms
     */
    private List<String> extractEncryptedQueryTerms(boolean removePanels, boolean getPredicate) throws MedCoException {
        // todo: handle cases: only clear no encrypt / only encrypt no clear
        // todo: must be modified if invertion implementation

        QueryDefinitionType qd = queryRequest.getQueryDefinition();
        StringWriter predicateSw = new StringWriter();
        List<String> extractedItems = new ArrayList<>();
        int encTermCount = 0;

        // iter on the panels
        for (int p = 0; p < qd.getPanel().size(); p++) {
            boolean panelIsEnc = false, panelIsClear = false;
            PanelType panel = qd.getPanel().get(p);

            // iter on the items
            int nbItems = panel.getItem().size();
            for (int i = 0; i < nbItems; i++) {

                // check if item is clear or encrypted, extract and generate predicate if yes
                Matcher medcoKeyMatcher = Constants.REGEX_QUERY_KEY_ENC.matcher(panel.getItem().get(i).getItemKey());
                if (medcoKeyMatcher.matches()) {

                    if (i == 0) {
                        predicateSw.append("(");
                    }

                    extractedItems.add(medcoKeyMatcher.group(4));
                    predicateSw.append("exists(v" + encTermCount++ + ", r)");

                    if (i < nbItems - 1) {
                        predicateSw.append(" || ");
                    } else if (i == nbItems - 1) {
                        predicateSw.append(")");
                        if (p < qd.getPanel().size() - 1) {
                            predicateSw.append(" &amp;&amp; ");
                        }
                    }

                    Logger.debug("Extracted item " + extractedItems.get(extractedItems.size() - 1));
                    panelIsEnc = true;
                } else {
                    panelIsClear = true;
                }

                // enforce that a panel can only be one type
                if (panelIsClear && panelIsEnc) {
                    throw Logger.error(new MedCoException("Encountered panel with mixed clear and encrypted query terms: not allowed."));
                }
            }

            // remove panel and log
            if (panelIsEnc) {
                if (removePanels) {
                    qd.getPanel().remove(panel);
                    p--;
                    Logger.debug("Removed encrypted panel");
                }
                Logger.debug("Encountered encrypted panel");
            } else if (panelIsClear) {
                Logger.debug("Encountered clear panel");
            } else {
                Logger.warn("Encountered empty panel in query " + qd.getQueryName());
            }
        }

        String predicate = predicateSw.toString();
        Logger.info("Extracted " + extractedItems.size() + " encrypted query terms and generated unlynx predicate with " +
                encTermCount + " terms: " + predicate + " for query " + queryRequest.getQueryName());

        if (getPredicate) {
            extractedItems.add(predicate);
        }
        return extractedItems;
    }

}
