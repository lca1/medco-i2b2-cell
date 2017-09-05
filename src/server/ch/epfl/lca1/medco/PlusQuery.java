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

import ch.epfl.lca1.medco.dao.MedCoDatabase;
import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.unlynx.UnlynxClient;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.setfinder.query.PanelType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryDefinitionRequestType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryDefinitionType;
import org.javatuples.Pair;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//

//todo doc: https://github.com/chb/shrine/tree/master/doc

/**
 * Represents a query to MedCo.
 * From the XML query (in CRC format), parse to extract the sensitive attributes, 
 * make query to CRC for non-sensitive attributes, get the patient set from CRC,
 * query the cothority with the patient sets and sensitive attributes and answer.
 *
 */
public class PlusQuery {//extends MedCoQuery {


	/** The original request from client. **/
	private I2B2QueryRequest queryRequest;

	/** The original XML request from client (a query definition) extracted as XML. **/
	private QueryDefinitionRequestType originalRequestXml;

    private I2B2CRCCell crcCell;

    private MedCoDatabase medcoDao;


	public PlusQuery(I2B2QueryRequest request) throws I2B2Exception {
		//super(request);
	}
	
	/**
	 * 
	 * @return the query answer in CRC XML format.
	 * @throws JAXBUtilException 
	 */
	// todo: handle cases: only clear no encrypt / only encrypt no clear
	public I2B2QueryResponse executeQuery(int resultMode, String clientPubKey, long timoutSeconds) throws MedCoException, I2B2Exception {
/*
	    // retrieve the encrypted query terms
        StopWatch.steps.start("Query parsing/splitting");
        List<String> encryptedItems = extractEncryptedQueryTerms(true, true);
        String predicate = encryptedItems.get(encryptedItems.size() - 1);
        encryptedItems.remove(predicate);
        StopWatch.steps.stop();

        // make query to i2b2 with the leftovers clear query terms, creates patient set on server side
        StopWatch.steps.start("Clear query: i2b2 query");
        I2B2QueryResponse clearResponse = crcCell.queryClearTerms(queryRequest);
        StopWatch.steps.stop();

        // predicate empty: means that there is no encrypted query terms, we can stop here the processing
        if (predicate.isEmpty()) {
            clearResponse.resetResultInstanceListToClearCountOnly();
            clearResponse.setQueryResults("", "", StopWatch.generateFullReport(null));
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
        clearResponse.setQueryResults(clientPubKey, queryResult.getEncResultB64(), StopWatch.generateFullReport(queryResult.getTimes()));
		return clearResponse;
		*/
return null;
		
	}



}
