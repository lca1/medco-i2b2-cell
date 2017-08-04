package ch.epfl.lca1.medco.loader.clinical;


import ch.epfl.lca1.medco.MedCoDataLoader;
import ch.epfl.lca1.medco.dao.MedCoDatabase;
import edu.harvard.i2b2.crc.datavo.pdo.ConceptType;
import ch.epfl.lca1.medco.i2b2.I2b2Status;
import ch.epfl.lca1.medco.i2b2.crc.I2B2CRCCell;
import ch.epfl.lca1.medco.i2b2.crc.PatientDataObject;
import ch.epfl.lca1.medco.i2b2.fr.I2B2FRCell;
import ch.epfl.lca1.medco.i2b2.ont.I2B2ONTCell;
import ch.epfl.lca1.medco.loader.AbstractLoader;
import ch.epfl.lca1.medco.loader.DataType;
import edu.harvard.i2b2.crc.loader.datavo.loader.query.LoadDataResponseType;
import ch.epfl.lca1.medco.unlynx.UnlynxEncrypt;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.ConceptNotFoundException;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.javatuples.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Created by misbach on 17.06.17.
 */

/**
 * Datasets expectations:
 * header line: unique names, sync with other nodes, max number of character: 15
 * same for value  fields! or not?
 */
public class DataLoader extends AbstractLoader {


    private List<String[]> encObsFacts;


    private PatientDataObject pdo;

    // for clear data: send message with crc (what is needed exactly?)

    // for enc data: get for field/value the nb associated, encrypt it and store in the db using the dao
    // make search on name to ont (get_name_info),should return one entry, take the key, and get children and
    // get the value id and good to go

    public DataLoader(String[] datasetFieldsName, DataType[] datasetFieldsType,
                      String providerId) throws I2B2Exception {
        super(datasetFieldsName, datasetFieldsType, providerId);
        encObsFacts = new ArrayList<>();

        pdo = new PatientDataObject(providerId);

    }

    public void loadEntry(I2B2ONTCell ontCell, String[] entry) throws I2B2Exception, UnlynxException {

        if (entry.length != datasetFieldsName.length) {
            Logger.warn("Invalid entry length, skipping: " + entry);
            return;
        }

        String sampleId = entry[0];
        String patientId = entry[1];
        pdo.addPatientSampleIds(patientId, sampleId); // takes case of eid, pid, patient, encounter

        for (int i = 2 ; i < entry.length ; i++) {
            if (entry[i].isEmpty()) {
                entry[i] = MedCoDataLoader.EMPTY_VALUE_FLAG;
            }

            switch (datasetFieldsType.get(datasetFieldsName[i])) {
                case ENC:
                    try {
                        long id = ontCell.getEncryptId(datasetFieldsName[i], entry[i]);
                        String encId = encryptor.encryptInt(id);
                        medcoDao.addEncObservationFact(sampleId, patientId, providerId, encId);
                    } catch (ConceptNotFoundException | IOException e) {
                        Logger.warn("Ignored dataset entry: " + e.getMessage());
                        continue;
                    }
                    break;

                case CLEAR:
                    // TODO: startDate set to now
                    String basecode = ontCell.getClearConceptCd(datasetFieldsName[i], entry[i]);
                    pdo.addObservationFact(patientId, sampleId, basecode, new GregorianCalendar());
                    break;
            }
        }
    }

    public void loadData(I2B2FRCell frCell, I2B2CRCCell crcCell) throws I2B2Exception {
        pdo.finalizePatientsAndEvents(providerId);

        String uploadName = "pdoUpload" + providerId + ".xml";
        frCell.uploadPDO(pdo, uploadName);
        Pair<I2b2Status, LoadDataResponseType> loadResult = crcCell.publishData("/opt/FRC_files/Demo/" + uploadName, providerId);
        //TODO
    }


    // for each value, generate xml and send it to the FR, then load it through crc
    // generation of pdo xml:
    // pid_Set (patient_mapping), eid_set (encounter_mapping) <-> string
    // enforce in i2b2 DB that patient_mapping has only 2 things (same encounter)

    // accumulate for each [sampleId, patientId]
    // then for unlynx/encrypted fields:
    //  add in enc_observation_fact

    // in CRC comm/loader: patient + encounter (eid + pid) to generate, with provider

}
