package ch.epfl.lca1.medco;


import edu.harvard.i2b2.crc.datavo.pm.RolesType;

public enum QueryType {

    OBFUSCATED_PER_SITE,
    AGGREGATED_PER_SITE,
    AGGREGATED_TOTAL;


    /**
     * i2b2 permissions: DATA_OBFSC / DATA_AGG / DATA_LDS / DATA_DEID / DATA_PROT
     *
     * @return
     */
    public static QueryType resolveUserPermission(RolesType pmRoles) {
        // ifnothing -> lowest
        // todo implement me
        return AGGREGATED_PER_SITE;
    }
}
