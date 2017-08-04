package ch.epfl.lca1.medco.loader.genomic;

import ch.epfl.lca1.medco.dao.MedCoDatabase;
import ch.epfl.lca1.medco.i2b2.ont.I2B2ONTCell;
import ch.epfl.lca1.medco.loader.EncryptedIdentifiersManager;
import ch.epfl.lca1.medco.loader.DataType;
import ch.epfl.lca1.medco.loader.AbstractLoader;
import ch.epfl.lca1.medco.unlynx.UnlynxEncrypt;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.XMLUtil;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import edu.harvard.i2b2.common.exception.I2B2Exception;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

// todo: optimize storage by enumerating values possible
public class GenomicLoader extends AbstractLoader {
// genomic ontology:
    // 1 entry / dataset
    // generate a xml containing headers, put that in blob (needed for the search)
    // also containing assay information?? what are they??
    // one node is the annotations, then        each leaf contains the values for a column


    // variant id additional thing
    // row id(calculated)/variant id/value

    /**
     * Maps fields of the dataset to the exact values it can take.
     */
    private Map<String, List<String>> datasetValues;
    private List<Long> variantsIds;

    private UnlynxEncrypt encryptor;
    private MedCoDatabase medcoDao;

    private int ignoredEntriesCount;

    public GenomicLoader(String[] datasetFieldsName, DataType[] datasetFieldsType, String providerId) throws I2B2Exception {//String providerId) {
        super(datasetFieldsName, datasetFieldsType, providerId);
        datasetValues = new HashMap<>();
        variantsIds = new ArrayList<>();

        encryptor = new UnlynxEncrypt();
        medcoDao = new MedCoDatabase();

        ignoredEntriesCount = 0;

        // TODO: assay / gene panel information (in ontoplogy->here)
        // TODO: counting ids


    }

    private void addDatasetValues(Map<String, String> fieldNameValueMap) {

        for (Map.Entry<String, String> entry : fieldNameValueMap.entrySet()) {
            if (!datasetValues.containsKey(entry.getKey())) {
                datasetValues.put(entry.getKey(), new ArrayList<>());
            }

            datasetValues.get(entry.getKey()).add(entry.getValue());
        }
    }


    /**
     * Parses an entry of the genomic dataset and stores the values in an ordered way in memory.
     * Some types of values are ignored ("IGNORE").
     * Types are assumed valid.
     * here: get variant id + sample and gen an obs fact
     * @param entry the entry of the genomic dataset
     */
    public void parseEntry(String[] entry) {

        if (entry.length != datasetFieldsName.length) {
            Logger.warn("Invalid entry length, skipping: " + Arrays.toString(entry));
            ignoredEntriesCount++;
            return;
        }

        // iter over entry and extract values
        String chromosome = null, startPos = null, refAlleles = null, altAlleles = null, sampleId = null;
        Map<String, String> stagingDatasetValues = new HashMap<>();

        for (int i = 0 ; i < entry.length ; i++) {
            switch (datasetFieldsType.get(datasetFieldsName[i])) {

                // filter out types that will not be stored
                case IGNORE:
                    break;

                // get sample id
                case SAMPLE_ID:
                    sampleId = entry[i];
                    break;

                // variant id types
                case CHROMOSOME:
                    chromosome = entry[i];
                    stagingDatasetValues.put(datasetFieldsName[i], entry[i]);
                    break;

                case START_POS:
                    startPos = entry[i];
                    stagingDatasetValues.put(datasetFieldsName[i], entry[i]);
                    break;

                case REF_ALLELES:
                    refAlleles = entry[i];
                    stagingDatasetValues.put(datasetFieldsName[i], entry[i]);
                    break;

                case ALT_ALLELES:
                    altAlleles = entry[i];
                    stagingDatasetValues.put(datasetFieldsName[i], entry[i]);
                    break;

                // types that will be stored by default
                default:
                    stagingDatasetValues.put(datasetFieldsName[i], entry[i]);
                    break;
            }
        }

        // parse the values, return if values of entries are not valid
        long startPosParsed, variantId;
        String encVariantId;
        try {
            if (chromosome == null || startPos == null || refAlleles == null || altAlleles == null || sampleId == null) {
                throw new IllegalArgumentException("Required entries not properly parsed.");
            }

            startPosParsed = Long.parseLong(startPos);
            variantId = EncryptedIdentifiersManager.getVariantId(chromosome, startPosParsed, refAlleles, altAlleles);
            encVariantId = encryptor.encryptInt(variantId);
        } catch (IllegalArgumentException | UnlynxException | IOException e) {
            Logger.warn("Ignoring invalid entry: " + Arrays.toString(entry), e);
            ignoredEntriesCount++;
            return;
        }

        // commit the staging values
        addDatasetValues(stagingDatasetValues);

        // store the variant id
        variantsIds.add(variantId);

        // store the enc obs fact
        medcoDao.accumulateEncObservationFact(sampleId, "UPDATE_ME", providerId, encVariantId);

        Logger.info("Successfully added entry for sample " + sampleId);
    }

    public void loadEntries() {
        int[] results = medcoDao.commitEncObservationFact();
        int nb = 0;
        for (int i = 0 ; i < results.length ; i++)
            nb += results[i];

        //todo: only when nothing else updates the concept_dim!!
        medcoDao.sqlUpdate("insert into i2b2demodata.concept_dimension(concept_path, concept_cd, name_char, import_date)" +
                "select c_fullname, c_basecode, c_name, update_date from i2b2metadata.clinical_non_sensitive where c_basecode is not null;");//c_fullname like '%VARIANT_ID%' and

        Logger.info("Successfully loaded sql data: " + nb);
    }

    /**
     * Generates the metadataxml field of the ontology for the annotations metadata.
     * Contains the variants ids (ordered the same way as the others entries), the assay
     * information and the name of the annotations.
     *
     * Format is the following:
     * {@code
     *  <genomic_annotations_metadata>
     *      <annotations_names>
     *           <name>XXX</name>
     *      <annotations_names>
     *
     *      <nb_variants>55</nb_variants>
     *
     *      <assay_information>whatever</assay_information>
     *
     *      <variants_ids format="csv"> X;X;X;X </variants_ids>
     *
     *  </genomic_annotations_metadata>
     *  }
     *
     * @return the XML string of the annotations metadata
     */
    private String genXmlAnnotationsMetadata() {
        // TODO: some csv values could contain ";" -> problem! they seem quoted -> check

        StringWriter sw = new StringWriter();
        sw.append("<genomic_annotations_metadata>");

        // annotations name
        sw.append("<annotations_names>");
        for (String fieldName : datasetFieldsName) {
            sw.append("<name>");
            sw.append(XMLUtil.escapeXmlValue(fieldName));
            sw.append("</name>");
        }
        sw.append("</annotations_names>");

        // number of variants
        sw.append("<nb_variants>");
        sw.append(variantsIds.size() + "");
        sw.append("</nb_variants>");

        // the assay information
        sw.append("<assay_information>");
        // TODO, don't forget XMLUtil.escapeXmlValue()
        sw.append("</assay_information>");

        // the variants ids
        sw.append("<variants_ids format=\"csv\">");
        for (int i = 0 ; i < variantsIds.size() ; i++) {
            sw.append(XMLUtil.escapeXmlValue(variantsIds.get(i).toString()));

            if (i < variantsIds.size() - 1) {
                sw.append(";");
            }
        }
        sw.append("</variants_ids>");

        sw.append("</genomic_annotations_metadata>");
        return sw.toString();
    }

    /**
     * Generates the metadataxml field of the ontology for the annotations values.
     * Containes all the ordered values for one annotation.
     *
     * Format is the following:
     * {@code
     *  <genomic_annotations_values>
     *      <annotation name="chromosome">
     *          <values format="csv"> x;x;x;x </values>
     *      </annotation>
     *  </genomic_annotations_values>
     * }
     *
     * @return the XML string of the annotations values
     */
    private String genXmlAnnotationsValues(String fieldName) {
        // TODO: some csv values could contain ";" -> problem! they seem quoted -> check

        StringWriter sw = new StringWriter();
        sw.append("<genomic_annotations_values>");
        sw.append("<annotation name=\"");
        sw.append(XMLUtil.escapeXmlValue(fieldName));
        sw.append("\">");

        // the annotations values
        sw.append("<values format=\"csv\">");
        List<String> fieldValues = datasetValues.get(fieldName);
        for (int i = 0 ; i < fieldValues.size() ; i++) {
            sw.append(XMLUtil.escapeXmlValue(fieldValues.get(i)));

            if (i < fieldValues.size() - 1) {
                sw.append(";");
            }
        }
        sw.append("</values>");

        sw.append("</annotation>");
        sw.append("</genomic_annotations_values>");
        return sw.toString();
    }

    // check exceptions

    /**
     * Load the accumulated ontology into the i2b2 ontology cell.
     *
     * @param ontCell
     * @throws I2B2Exception
     * @throws UnlynxException
     */
    public void loadOntology(String datasetId, I2B2ONTCell ontCell) throws I2B2Exception, UnlynxException {

        String annotationsName = "Genomic Annotations (" + datasetId + ")";

        // entry corresponding to this dataset, to query these annotations
        ontCell.accumulateConcept(annotationsName, I2B2ONTCell.ONT_PATH_GENOMIC, "CA",
                I2B2ONTCell.BASECODE_PREFIX_GEN, datasetId, "Genomic annotations and sample data.",
                I2B2ONTCell.TABLE_CD_GENOMIC, genXmlAnnotationsMetadata(), null);

        // entries for each annotation of this dataset
        for (String fieldName : datasetValues.keySet()) {
            ontCell.accumulateConcept(fieldName, I2B2ONTCell.ONT_PATH_GENOMIC + annotationsName + "\\", "LA",
                    I2B2ONTCell.BASECODE_PREFIX_GEN, null, "Genomic annotation or sample data.",
                    I2B2ONTCell.TABLE_CD_GENOMIC, genXmlAnnotationsValues(fieldName), null);
        }

        ontCell.loadConcepts();
        Logger.info("There were " + ignoredEntriesCount + " ignored genomic entries, " + variantsIds.size() + " variant ids ");
    }
}
