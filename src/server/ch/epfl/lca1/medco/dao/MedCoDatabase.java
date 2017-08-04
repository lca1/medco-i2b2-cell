/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the i2b2 Software License v1.0 
 * which accompanies this distribution. 
 * 
 * Contributors:
 * 		Lori Phillips
 */
package ch.epfl.lca1.medco.dao;

import java.util.*;

import javax.sql.DataSource;

import ch.epfl.lca1.medco.util.Logger;
import org.javatuples.Pair;
import org.springframework.jdbc.core.support.JdbcDaoSupport;

import ch.epfl.lca1.medco.util.MedCoUtil;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.springframework.jdbc.support.rowset.SqlRowSet;


// TODO: migrate the medco_data.enc_observation_fact to the normal i2b2 observation fact
// todo: do not use the bootstrap datasource, use it according to the user (from PM)

public class MedCoDatabase extends JdbcDaoSupport {

	public MedCoDatabase() throws I2B2Exception {
		DataSource ds = null;
		try {
			ds = MedCoUtil.getInstance().getDataSource("java:/MedCoBootStrapDS");
			Logger.debug(ds.toString());
		} catch (I2B2Exception e2) {
			throw Logger.fatal(e2);
		}
		this.setDataSource(ds);

		this.batchSqlStatements = new ArrayList<>();
	}


	public int sqlUpdate(String sql, String... params) {
	    return this.getJdbcTemplate().update(sql, params);
    }

    public SqlRowSet sqlSelect(String sql, String... params) {
        return this.getJdbcTemplate().queryForRowSet(sql, params);
    }

    public int sqlSelectInt(String sql, String... params) {
	    return getJdbcTemplate().queryForInt(sql, params);
    }

    public ArrayList<String> batchSqlStatements;
    public int[] sqlBatchUpdate() {
	    int[] ret = this.getJdbcTemplate().batchUpdate(batchSqlStatements.toArray(new String[]{}));
	    batchSqlStatements.clear();
	    return ret;
    }


    /**
     * Adds a medco encrypted observation fact.
     */
    public int addEncObservationFact(String encounterNum, String patientNum, String providerId, String encConceptNum) {

    	String sql =
            "insert into medco_data.enc_observation_fact " +
            "(encounter_id, patient_id, provider_id, enc_concept_id) " +
            "values (?,?,?,?)";
	//String metadataId = null; //TODO metadata_id??? (not for now)
        return sqlUpdate(sql, encounterNum, patientNum, providerId, encConceptNum);
         // TODO: temp disabled
    }


    /**
     * Adds a medco encrypted observation fact.
     */
    public void accumulateEncObservationFact(String encounterNum, String patientNum, String providerId, String encConceptNum) {

        batchSqlStatements.add("insert into medco_data.enc_observation_fact " +
                        "(encounter_id, patient_id, provider_id, enc_concept_id) " +
                        "values ('" + encounterNum + "','" + patientNum + "','" + providerId + "','" + encConceptNum + "')");
        //String metadataId = null; //TODO metadata_id??? (not for now)
        //return sqlUpdate(sql, encounterNum, patientNum, providerId, encConceptNum);
        // TODO: temp disabled
    }

    public int[] commitEncObservationFact() {
        return sqlBatchUpdate();
    }

        /**
         * Returns pairs of {sample_id, sample_id_source/provider_id} from the enc obs fact
         *
         * @return
         */
    public Set<Pair<String, String>> getUniqueSampleIds() {
        String sql = "select distinct encounter_id, provider_id from medco_data.enc_observation_fact";
        SqlRowSet rowSet = sqlSelect(sql);

        Set<Pair<String, String>> returnSet = new HashSet<>();
        while (rowSet.next()) {
            returnSet.add(new Pair<>(
                    rowSet.getString("encounter_id"),
                    rowSet.getString("provider_id")
            ));
        }

        Logger.info("Retrieved pairs of sample_id and sample_id_source, size:" + returnSet.size());
        return returnSet;
    }

    /**
     * Updates the patients and samples ids to their corresponding nums using the provided map.
     *
     * @param map map containing: key=sample_id, value={sample_num, patient_num}TODO wrong
     *            val0: sample, val1: patients
     */
    public int updateSampleAndPatientIds(Map<String, Pair<String, String>> map) {

        int nbUpdatesCount = 0;
        for (Map.Entry<String, Pair<String, String>> entry: map.entrySet()) {
            String sql = "update medco_data.enc_observation_fact " +
                    "set encounter_id = ?, patient_id = ? where encounter_id = ?";
            nbUpdatesCount += sqlUpdate(sql, entry.getValue().getValue0(), entry.getValue().getValue1(), entry.getKey());
        }

        return nbUpdatesCount;
    }

    public List<List<String>> getPatientsData(List<String> patientIdsList) {
        List<List<String>> patientsData = new ArrayList<>();

        for (String patientId : patientIdsList) {
            String sql = "select enc_concept_id from medco_data.enc_observation_fact where patient_id = ?";
            SqlRowSet rowSet = sqlSelect(sql, patientId);

            List<String> currentPatientData = new ArrayList<>();
            while (rowSet.next()) {
                currentPatientData.add(rowSet.getString("enc_concept_id"));
            }
            patientsData.add(currentPatientData);
        }

        return patientsData;
    }

// TODO: tests!
}