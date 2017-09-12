package ch.epfl.lca1.medco.i2b2;

import ch.epfl.lca1.medco.i2b2.fr.I2B2FRCell;
import ch.epfl.lca1.medco.i2b2.ont.I2B2ONTCell;
import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import org.junit.Test;

import javax.xml.datatype.DatatypeConfigurationException;

import static org.junit.Assert.*;

/**
 * Created by misbach on 12.06.17.
 */
public class I2b2CellsTests {

    static {
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.ONTCELL_WS_URL_PROPERTIES,
                "http://localhost:8080/i2b2/services/OntologyService");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.FRCELL_WS_URL_PROPERTIES,
                "http://localhost:8080/i2b2/services/FRService");
    }

    private MedCoI2b2MessageHeader auth = new MedCoI2b2MessageHeader("i2b2demotest", "Demo",
            "demo", false, 0, "demouser");

    private I2B2ONTCell ontCell = new I2B2ONTCell(auth);

    private I2B2FRCell frCell = new I2B2FRCell(auth);


    @Test
    public void ontAddConceptTest() throws I2B2Exception, JAXBUtilException, DatatypeConfigurationException {
        ontCell.accumulateConcept("test1", "\\test1\\", "FA", "codetest", "suffix", "comment", "CUSTOM_META", null, null);
        ontCell.accumulateConcept("test2", "\\test2\\", "FA", "codetest", "suffix2", "comment", "CUSTOM_META", null, null);

        I2b2Status res = ontCell.loadConcepts();
        System.out.println(res);
        System.out.println(res.getStatusMessage());
        assertEquals(res, I2b2Status.DONE);

    }

    @Test
    public void ontIdTest() throws I2B2Exception, UnlynxException {
        ontCell.updateNextEncUsableId(3);
        ontCell.updateNextClearUsableId(40);
        long idE = ontCell.getNextEncUsableId();
        long idC = ontCell.getNextClearUsableId();
        assertEquals(idE, 3);
        assertEquals(idC, 40);
        System.out.println(idE + " , " + idC);
        ontCell.updateNextEncUsableId(idE + 1);
        ontCell.updateNextClearUsableId(idC + 1);
    }

    @Test
    public void frUploadTest() throws I2B2Exception {
        try {
            frCell.uploadFile("testfiles/medco_request_query_def_reference.xml", "testFile.xml");
        } catch (Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        assertTrue(true);
    }

}
