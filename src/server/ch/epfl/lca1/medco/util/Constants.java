package ch.epfl.lca1.medco.util;

import java.util.regex.Pattern;

public class Constants {

    // -----------------------------------------------------
    // XML formats -----------------------------------------
    // -----------------------------------------------------

    public static String
            DDT_REQ_XML_EL = "unlynx_ddt_request",
            DDT_REQ_XML_START_TAG = "<" + DDT_REQ_XML_EL + ">",
            DDT_REQ_XML_END_TAG = "</" + DDT_REQ_XML_EL + ">",
            DDT_RESP_XML_EL = "unlynx_ddt_response",
            DDT_RESP_XML_START_TAG = "<" + DDT_RESP_XML_EL + ">",
            DDT_RESP_XML_END_TAG = "</" + DDT_RESP_XML_EL + ">";

    public static String
            AGG_REQ_XML_EL = "unlynx_agg_request",
            AGG_REQ_XML_START_TAG = "<" + AGG_REQ_XML_EL + ">",
            AGG_REQ_XML_END_TAG = "</" + AGG_REQ_XML_EL + ">",
            AGG_RESP_XML_EL = "unlynx_agg_response",
            AGG_RESP_XML_START_TAG = "<" + AGG_RESP_XML_EL + ">",
            AGG_RESP_XML_END_TAG = "</" + AGG_RESP_XML_EL + ">";


    // -----------------------------------------------------
    // DB formats ------------------------------------------
    // -----------------------------------------------------

    public static String PATIENT_DUMMY_FLAG_COL_NAME = "enc_dummy_flag_cd";


    // -----------------------------------------------------
    // Ontology formats ------------------------------------
    // -----------------------------------------------------


    public static String
            CONCEPT_KEY_ENCRYPTED_FLAG = "ENCRYPTED_KEY",
            CONCEPT_PATH_TAGGED_PREFIX = "\\\\SENSITIVE_TAGGED\\medco\\tagged\\", // todo: should be configurable
            CONCEPT_NAME_TEST_FLAG = "TESTKEY";

    /**
     * Regex patterns that matches query keys with encrypted term
     * todo: replace path encrypted / tagged
     */
    public static final Pattern REGEX_QUERY_KEY_ENC =
            Pattern.compile("^\\s*\\\\\\\\" + CONCEPT_KEY_ENCRYPTED_FLAG + "\\\\([a-zA-Z0-9+/=]+)\\\\\\s*$");


    /**
     * Prefixes to the MedCo concept codes.
     * <p>
     * QUERY_ITEM_KEY_ENC_PREFIX: collectively encrypted (or to be) codes
     * QUERY_ITEM_KEY_CLEAR_PREFIX: clear-text codes
     * QUERY_ITEM_KEY_TAG_PREFIX: tagged codes
     */
    public static final String
            PREFIX_QUERY_ITEM_KEY_ENC = "C_ENC:",
            PREFIX_QUERY_ITEM_KEY_TAG = "TAG:",
            PREFIX_QUERY_ITEM_KEY_CLEAR = "CLEAR:";

    
}

