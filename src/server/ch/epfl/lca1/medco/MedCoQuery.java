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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.epfl.lca1.medco.dao.MedCoDatabase;
import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.unlynx.UnlynxClient;
import ch.epfl.lca1.medco.unlynx.UnlynxQuery;
import ch.epfl.lca1.medco.unlynx.UnlynxQueryResult;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.StopWatch;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;

import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
//
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.setfinder.query.PanelType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryDefinitionRequestType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryDefinitionType;
import org.javatuples.Pair;

//todo doc: https://github.com/chb/shrine/tree/master/doc

/**
 * Represents a query to MedCo.
 * From the XML query (in CRC format), parse to extract the sensitive attributes, 
 * make query to CRC for non-sensitive attributes, get the patient set from CRC,
 * query the cothority with the patient sets and sensitive attributes and answer.
 * 
 * Setfinder query request delegate class $Id: QueryRequestDelegate.java,v 1.17
 * 2008/05/08 15:13:45 rk903 Exp $
 *
 * @author rkuttan
 */
public class MedCoQuery {

    private static final String QUERY_ITEM_KEY_ENC_PREFIX = "MEDCO_ENC:",
                                QUERY_ITEM_KEY_GEN_PREFIX = "MEDCO_GEN:";

	/** The original request from client. **/
	private I2B2QueryRequest queryRequest;

	/** The original XML request from client (a query definition) extracted as XML. **/
	private QueryDefinitionRequestType originalRequestXml;

    private I2B2CRCCell crcCell;

    private MedCoDatabase medcoDao;


	public MedCoQuery(I2B2QueryRequest request) throws I2B2Exception {
		this.queryRequest = request;
		crcCell = new I2B2CRCCell(request.getUserAuthentication());
        medcoDao = new MedCoDatabase();
	}
	
	/**
	 * 
	 * @return the query answer in CRC XML format.
	 * @throws JAXBUtilException 
	 */
	// todo: handle cases: only clear no encrypt / only encrypt no clear
	public I2B2QueryResponse executeQuery(int resultMode, String clientPubKey, long timoutSeconds) throws MedCoException, I2B2Exception {

	    // retrieve the encrypted query terms
        StopWatch.steps.start("Query parsing/splitting");
        Pair<List<String>, String> encryptedItemsAndPredicate = extractEncryptedQueryTermsAndPredicate();
        List<String> encryptedItems = encryptedItemsAndPredicate.getValue0();
        String predicate = encryptedItemsAndPredicate.getValue1();
        StopWatch.steps.stop();

        // make query to i2b2 with the leftovers clear query terms, creates patient set on server side
        StopWatch.steps.start("Clear query: i2b2 query");
        I2B2QueryResponse clearResponse = crcCell.queryClearTerms(queryRequest);
        StopWatch.steps.stop();

        // predicate empty: means that there is no encrypted query terms, we can stop here the processing
        if (predicate.isEmpty()) {
            clearResponse.resetResultInstanceListToClearCountOnly();
            clearResponse.setQueryResults("", "", StopWatch.generateAllCsvReports(null));
            Logger.info("Query clear only successful (" + queryRequest.getQueryName() + ").");
            return clearResponse;
        }

        StopWatch.steps.start("Clear query: patient set retrieval");
        List<String> clearPatientSet = crcCell.queryForPatientSet(clearResponse.getPatientSetId());
        StopWatch.steps.stop();

        // retrieve encrypted data of the patients
        StopWatch.steps.start("Patient set encrypted data retrieval");
        List<List<String>> patientsEncData = medcoDao.getPatientsData(clearPatientSet);
        //List<List<String>> patientsEncData = new ArrayList<>();
        //List<String> tempTest = new ArrayList<>();
        //tempTest.add("ZwneoQQyvDUckDcxlOvS+1IvDckXgw7n13IpznyAcHaU6r3uHuSZOXFHUHxhqINh0q6PFj9htw4Ogrt0TR7b5w=="); //todo: change me for 6/9/10!
        //patientsEncData.add(tempTest);
        StopWatch.steps.stop();

        // query unlynx
        StopWatch.steps.start("Unlynx query");
		UnlynxQuery unlynxQuery = new UnlynxQuery(
		        queryRequest.getQueryName(),
                predicate,
                encryptedItems,
                patientsEncData,
                clientPubKey,
                resultMode,
                timoutSeconds
                );
        UnlynxClient unlynxClient = UnlynxClient.executeQuery(unlynxQuery);
        unlynxClient.waitForCompletion();
        UnlynxQueryResult queryResult = unlynxClient.getQueryResult();

        if (unlynxClient.getQueryState() == UnlynxClient.QueryState.COMPLETED) {
            Logger.info("MedCo query successful (" + queryRequest.getQueryName() + ").");
        } else {
            throw Logger.error(new MedCoException("Error during MedCo query: " + queryResult.getErrorMessage()));
        }

        StopWatch.steps.stop();
        StopWatch.overall.stop();

        clearResponse.resetResultInstanceListToEncryptedCountOnly();
        clearResponse.setQueryResults(clientPubKey, queryResult.getEncResultB64(), StopWatch.generateAllCsvReports(queryResult.getTimes()));
		return clearResponse;
		
	}



	/**
	 * Extracts from the i2b2 query the sensitive / encrypted items.
     *
     * They are the items with a non standard name of "MEDCO_ENC" or "MEDCO_GEN"
     * enforce that panel are exclusive!! at least for now
     *
     // from panel - items to
     // (exists(v0, r) || exists(v1, r)) &amp;&amp; (exists(v2, r) || exists(v3, r)) &amp;&amp; exists(v4, r)
     * todo: must be modified if invertion implementation
     *
     * @throws MedCoException if a panel contains mixed clear and encrypted query terms
	 */
	private Pair<List<String>, String> extractEncryptedQueryTermsAndPredicate() throws MedCoException {

		QueryDefinitionType qd = queryRequest.getQueryDefinition();
        StringWriter predicateSw = new StringWriter();
        List<String> extractedItems = new ArrayList<>();
        int encTermCount = 0;

        // regex that matches with encrypted query terms
        Pattern medcoKeyRegex = Pattern.compile("^(\\s*)(" + QUERY_ITEM_KEY_ENC_PREFIX + "|" + QUERY_ITEM_KEY_GEN_PREFIX + ")(.*)");

        // iter on the panels
		for (int p = 0 ; p < qd.getPanel().size() ; p++) {
		    boolean panelIsEnc = false, panelIsClear = false;
            PanelType panel = qd.getPanel().get(p);

            // iter on the items
            int nbItems = panel.getItem().size();
			for (int i = 0 ; i < nbItems ; i++) {

			    // check if item is clear or encrypted, extract and generate predicate if yes
			    Matcher medcoKeyMatcher = medcoKeyRegex.matcher(panel.getItem().get(i).getItemKey());
			    if (medcoKeyMatcher.matches()) {

			        if (i == 0) {
                        predicateSw.append("(");
                    }

                    extractedItems.add(medcoKeyMatcher.group(3));
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
			    qd.getPanel().remove(panel);
			    p--;
			    Logger.debug("Encountered encrypted panel, removed");
            } else if (panelIsClear) {
                Logger.debug("Encountered clear panel");
            } else {
			    Logger.warn("Encountered empty panel in query " + qd.getQueryName());
            }
		}

        String predicate = predicateSw.toString();
		Logger.info("Extracted " + extractedItems.size() + " encrypted query terms and generated unlynx predicate with " +
                encTermCount + " terms: " + predicate + " for query " + queryRequest.getQueryName());
		return new Pair<>(extractedItems, predicate);
	}
}
