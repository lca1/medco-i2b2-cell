package ch.epfl.lca1.medco.loader.clinical;

import ch.epfl.lca1.medco.MedCoDataLoader;
import ch.epfl.lca1.medco.i2b2.I2b2Status;
import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.crc.PatientDataObject;
import ch.epfl.lca1.medco.i2b2.fr.I2B2FRCell;
import ch.epfl.lca1.medco.i2b2.ont.I2B2ONTCell;
import ch.epfl.lca1.medco.loader.DataType;
import ch.epfl.lca1.medco.loader.EncryptedIdentifiersManager;
import ch.epfl.lca1.medco.loader.AbstractLoader;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.crc.datavo.pdo.ConceptType;
import edu.harvard.i2b2.crc.loader.datavo.loader.query.LoadDataResponseType;
import org.javatuples.Pair;


import java.util.*;

// no modifiers are used
// use of values?
public class OntologyLoader extends AbstractLoader {

    // ontology!!!!!!
    // the presence, or the value, or both???
    // enum:
    //  - clear: 1 node in tree, subvalues are enum values // concept code for header, modifier for value
    //  - enc: 1 enc val: concat of id+val
    // int:
    //  - clear: 1 node, value as modifier // concept code for header, value for value
    //  - enc: 1 enc val: concat of id+val

    /**
     * Maps fields of the dataset to all of the possible values it can take.
     */
    private Map<String, Set<String>> datasetValues;

    /**
     * List of concept in the PDO format, for the CRC cell concept_dimension table.
     */
    private List<ConceptType> pdoConcepts;


    public OntologyLoader(String[] datasetFieldsName, DataType[] datasetFieldsType, String providerId) throws I2B2Exception {//String providerId) {
        super(datasetFieldsName, datasetFieldsType, providerId);
        datasetValues = new HashMap<>();
        pdoConcepts = new ArrayList<>();

        if (datasetFieldsType[0] != DataType.SAMPLE_ID ||
            datasetFieldsType[1] != DataType.PATIENT_ID) {
            throw Logger.error(new IllegalArgumentException("Ill-formated types."));
        }
    }

    private void updateDatasetValues(String fieldName, String fieldValue) {
        if (!datasetValues.containsKey(fieldName)) {
            datasetValues.put(fieldName, new HashSet<>());
        }

        datasetValues.get(fieldName).add(fieldValue);
    }



    public void parseEntry(String[] entry) {

        if (entry.length != datasetFieldsName.length) {
            Logger.warn("Invalid entry length, skipping: " + Arrays.toString(entry));
            return;
        }

        for (int i = 2 ; i < entry.length ; i++) {
            if (entry[i].isEmpty()) {
                entry[i] = MedCoDataLoader.EMPTY_VALUE_FLAG;
            }

            updateDatasetValues(datasetFieldsName[i], entry[i]);
        }
    }

    // check exceptions

    /**
     * Load the accumulated ontology into the i2b2 ontology cell.
     *
     * @param ontCell
     * @throws I2B2Exception
     * @throws UnlynxException
     */
    // TODO BUG: should be freshly loaded (no support of incremental loading)
    public void loadOntology(I2B2ONTCell ontCell) throws I2B2Exception, UnlynxException {

        EncryptedIdentifiersManager idsManager =
                new EncryptedIdentifiersManager(ontCell.getNextEncUsableId());
        long nextClearUsableId = ontCell.getNextClearUsableId();

        // iter over fields name
        for (String fieldName : datasetValues.keySet()) {
            switch (datasetFieldsType.get(fieldName)) {
                case ENC: {
                    ontCell.accumulateConcept(fieldName, I2B2ONTCell.ONT_PATH_CLINICAL_SENSITIVE, "CA",
                            I2B2ONTCell.BASECODE_PREFIX_ENC, null, "Sensitive field encrypted by Unlynx",
                            I2B2ONTCell.TABLE_CD_CLINICAL_SENSITIVE, null, null);
                    break;
                }

                case CLEAR: {
                    ontCell.accumulateConcept(fieldName, I2B2ONTCell.ONT_PATH_CLINICAL_NONSENSITIVE, "CA",
                            I2B2ONTCell.BASECODE_PREFIX_CLEAR, null, "Non-sensitive field",
                            I2B2ONTCell.TABLE_CD_CLINICAL_NON_SENSITIVE, null, null);
                    break;
                }
            }

            // iter over field values
            for (String fieldValue : datasetValues.get(fieldName)) {
                switch (datasetFieldsType.get(fieldName)) {
                    case ENC: {
                        ontCell.accumulateConcept(fieldValue, I2B2ONTCell.ONT_PATH_CLINICAL_SENSITIVE + fieldName + "\\", "LA",
                                I2B2ONTCell.BASECODE_PREFIX_ENC, idsManager.getNextUsableClinicalId() + "", "Sensitive value encrypted by Unlynx",
                                I2B2ONTCell.TABLE_CD_CLINICAL_SENSITIVE, null, pdoConcepts);
                        break;
                    }

                    case CLEAR: {
                        ontCell.accumulateConcept(fieldValue, I2B2ONTCell.ONT_PATH_CLINICAL_NONSENSITIVE + fieldName + "\\", "LA",
                                I2B2ONTCell.BASECODE_PREFIX_CLEAR, nextClearUsableId++ + "", "Non-sensitive value",
                                I2B2ONTCell.TABLE_CD_CLINICAL_NON_SENSITIVE, null, pdoConcepts);
                        break;
                    }
                }
            }
        }

        I2b2Status status = ontCell.loadConcepts();
        ontCell.updateNextEncUsableId(idsManager.getNextUsableClinicalId());
        ontCell.updateNextClearUsableId(nextClearUsableId);
    }

    public void loadConceptDimension(I2B2FRCell frCell, I2B2CRCCell crcCell) throws I2B2Exception {
        String uploadName = "pdoConceptsUpload" + providerId + ".xml";
        frCell.uploadPDO(new PatientDataObject(providerId, pdoConcepts), uploadName);
        Pair<I2b2Status, LoadDataResponseType> loadResult = crcCell.publishData("/opt/FRC_files/Demo/" + uploadName, providerId);
        //todo: errors check
        pdoConcepts.clear();
    }
}
