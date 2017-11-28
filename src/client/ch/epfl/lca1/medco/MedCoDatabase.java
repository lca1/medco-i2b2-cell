/*
 * Copyright (c) 2006-2007 Massachusetts General Hospital
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the i2b2 Software License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 * 		Lori Phillips
 */
package ch.epfl.lca1.medco;

import java.util.*;

import javax.sql.DataSource;

import ch.epfl.lca1.medco.util.Logger;
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
    }

    /**
     * search a concept in the CLINICAL_NON_SENSITIVE TABLE
     */
    public String searchConceptClinicalNonSensitive(String concept){
        String sql =
                "select c_fullname " +
                        "from shrine_ont.clinical_non_sensitive " +
                        "where c_fullname like " + "'%"+concept+"%'";

        try {
            SqlRowSet rowSet = this.getJdbcTemplate().queryForRowSet(sql);

            String concept_path = "";
            rowSet.next();
            concept_path = rowSet.getString("c_fullname");
            return concept_path ;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * search a concept in the CLINICAL_SENSITIVE TABLE
     */
    public String searchConceptClinicalSensitive(String concept){
        String sql =
                "select c_basecode " +
                        "from shrine_ont.clinical_sensitive " +
                        "where c_fullname like " + "'%"+concept+"%'";

        try {
            SqlRowSet rowSet = this.getJdbcTemplate().queryForRowSet(sql);

            String concept_path = "";
            rowSet.next();
            concept_path = rowSet.getString("c_basecode");
            return concept_path ;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * search a concept in the GENOMIC_ANNOTATIONS
     */
    public List<String> searchConceptGenomic(String key, String value){
        String sql =
                "select variant_id " +
                        "from shrine_ont.genomic_annotations " +
                        "where variant_annotations ->> '" + key + "' = '" + value + "'";

        try {
            SqlRowSet rowSet = this.getJdbcTemplate().queryForRowSet(sql);

            List<String> genomicIDs = new ArrayList<>(0);
            while (rowSet.next()){
                genomicIDs.add(rowSet.getString("variant_id"));
            }
            return genomicIDs;
        } catch (Exception e) {
            return new ArrayList<>(0);
        }
    }

}
