package ch.epfl.lca1.medco.dao;

import ch.epfl.lca1.medco.util.MedCoUtil;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.Hashtable;

/**
 * Created by misbach on 19.06.17.
 */
public class DaoTests {

// TODO: some common setup for tests
    static {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName("localhost");
        ds.setDatabaseName("i2b2demotest");
        ds.setPortNumber(5432);
        ds.setUser("medco_data");
        ds.setPassword("demouser");
        ds.setCurrentSchema("medco_data");

        MedCoUtil.getTestInstance().setDataSource(ds);
    }

    @Test
    public void encObsFactTest() throws I2B2Exception {
        MedCoDatabase dao = new MedCoDatabase();
        dao.addEncObservationFact("test1", "test2", "chuv", "amflnwieruzlfb666asvgbli");


    }
}
