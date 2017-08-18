package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.dao.MedCoDatabase;
import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.fr.I2B2FRCell;
import ch.epfl.lca1.medco.i2b2.ont.I2B2ONTCell;
import ch.epfl.lca1.medco.i2b2.pm.UserAuthentication;
import ch.epfl.lca1.medco.loader.clinical.DataLoader;
import ch.epfl.lca1.medco.loader.clinical.OntologyLoader;
import ch.epfl.lca1.medco.loader.DataType;
import ch.epfl.lca1.medco.loader.genomic.GenomicLoader;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import com.opencsv.CSVReader;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.javatuples.Pair;

import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

/**
 * Created by misbach on 06.06.17.
 */
// todo: think about how to treat mismatch when exist in genomic but not clinical! (right now the ids to num are not updated)
public class MedCoDataLoader {

    public static final String EMPTY_VALUE_FLAG = "<empty>";

    private String providerId, datasetId;
    private MedCoDatabase medcoDao;


    private UserAuthentication auth;
    private I2B2ONTCell ontCell;
    private I2B2CRCCell crcCell;
    private I2B2FRCell frCell;


    public MedCoDataLoader(String providerId, String datasetId, String domainId, String projectId, String username, String password) throws I2B2Exception {

        this.providerId = providerId;
        this.datasetId = datasetId;

        // init connection to i2b2 cells
        auth = new UserAuthentication(domainId, projectId, username, false, 0, password);
        ontCell = new I2B2ONTCell(auth);
        //crcCell = new I2B2CRCCell(auth);
        frCell = new I2B2FRCell(auth);

        medcoDao = new MedCoDatabase();
    }

    /**
     * First line = header
     *'\t',
     * @param nbLinesToSkip
     * @param dataTypes
     * @throws IOException
     */
    // split up in 2 fonctins: ontology and data ; pdoconcepts/concept dim -> during ontology
    public void loadClinicalFileOntology(String clinicalFilePath, char csvSeparator, char csvQuote, int nbLinesToSkip, DataType[] dataTypes)
            throws IOException, I2B2Exception, UnlynxException {

        CSVReader reader = new CSVReader(new FileReader(clinicalFilePath), csvSeparator, csvQuote, nbLinesToSkip);
        String[] csvHeader = reader.readNext();
        String[] csvEntry;

        // first pass: load ontology
        OntologyLoader clinicalOntLoader = new OntologyLoader(csvHeader, dataTypes, providerId);
        while ((csvEntry = reader.readNext()) != null) {
            clinicalOntLoader.parseEntry(csvEntry);
        }
        clinicalOntLoader.loadOntology(ontCell);
//        clinicalOntLoader.loadConceptDimension(frCell, crcCell);
        //todo: commented in order to load big things, use sql query to update: !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //insert into i2b2demodata.concept_dimension(concept_path, concept_cd, name_char, import_date)
        //select c_fullname, c_basecode, c_name, update_date from i2b2metadata.clinical_non_sensitive where c_fullname like '%VARIANT_ID%' and c_basecode is not null;

        reader.close();
    }

    public void loadClinicalFileData(String clinicalFilePath, char csvSeparator, char csvQuote, int nbLinesToSkip, DataType[] dataTypes)
            throws IOException, I2B2Exception, UnlynxException {

        CSVReader reader = new CSVReader(new FileReader(clinicalFilePath), csvSeparator, csvQuote, nbLinesToSkip);
        String[] csvHeader = reader.readNext();
        String[] csvEntry;

        // second pass: load data
        DataLoader clinicalData = new DataLoader(csvHeader, dataTypes, providerId);
        reader = new CSVReader(new FileReader(clinicalFilePath), csvSeparator, csvQuote, nbLinesToSkip + 1);
        while ((csvEntry = reader.readNext()) != null) {
            clinicalData.loadEntry(ontCell, csvEntry);
        }
        clinicalData.loadData(frCell, crcCell);
    }

    public void loadGenomicFile(String genomicFilePath, char csvSeparator, char csvQuote, int nbLinesToSkip, DataType[] dataTypes)
            throws IOException, I2B2Exception, UnlynxException {

        CSVReader reader = new CSVReader(new FileReader(genomicFilePath), csvSeparator, csvQuote, nbLinesToSkip);
        String[] csvHeader = reader.readNext();
        String[] csvEntry;

        GenomicLoader genOntLoader = new GenomicLoader(csvHeader, dataTypes, providerId);
        while ((csvEntry = reader.readNext()) != null) {
            genOntLoader.parseEntry(csvEntry);
        }
        genOntLoader.loadEntries();
        //genOntLoader.loadOntology(datasetId, ontCell); todo: tmp disabled!!
        reader.close();
    }

    /**
     * Should be called after loading
     * @throws I2B2Exception
     */
    public void translateIdsToNums() throws I2B2Exception {
        Map<String, Pair<String, String>> maps =
                crcCell.getSampleAndPatientNums(medcoDao.getUniqueSampleIds());
        medcoDao.updateSampleAndPatientIds(maps);
    }
}
