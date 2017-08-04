package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.util.MedCoUtil;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Created by misbach on 14.07.17.
 */
public class ConfiguredTests {
    protected void loadMedCoConf(String hostname, int i2b2Port, int psqlPort, String unlynxEntryPoint) {
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.ONTCELL_WS_URL_PROPERTIES,
                    "http://" + hostname + ":" + i2b2Port + "/i2b2/services/OntologyService");
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.FRCELL_WS_URL_PROPERTIES,
                    "http://" + hostname + ":" + i2b2Port + "/i2b2/services/FRService");
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.CRCCELL_WS_URL_PROPERTIES,
                    "http://" + hostname + ":" + i2b2Port + "/i2b2/services/QueryToolService");
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.I2B2CELLS_WS_WAITTIME_PROPERTIES,
                    "180000");

            MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_BINARY_PATH_PROPERTIES, "unlynxI2b2"); // assumed in bin path
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_GROUP_FILE_PATH_PROPERTIES,
                    "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/deployment/configuration/keys/iccluster-group.toml");

            MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_DEBUG_LEVEL_PROPERTIES, "5");
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_PROOFS_PROPERTIES, "0");
            MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_ENTRY_POINT_IDX_PROPERTIES, unlynxEntryPoint);

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerName(hostname);
            ds.setDatabaseName("i2b2demotest");
            ds.setPortNumber(psqlPort);
            ds.setUser("medco_data");
            ds.setPassword("demouser");
            ds.setCurrentSchema("medco_data");

            MedCoUtil.getTestInstance().setDataSource(ds);
    }

    protected void loadSrv1Conf() {
        loadMedCoConf("iccluster061.iccluster.epfl.ch", 8080,
                5432, "0");
    }

    protected void loadSrv3Conf() {
        loadMedCoConf("iccluster062.iccluster.epfl.ch", 8080,
                5432, "1");
    }

    protected void loadSrv5Conf() {
        loadMedCoConf("iccluster063.iccluster.epfl.ch", 8080,
                5432, "2");
    }
}
