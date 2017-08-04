package ch.epfl.lca1.medco.loader;

import ch.epfl.lca1.medco.dao.MedCoDatabase;
import ch.epfl.lca1.medco.unlynx.UnlynxEncrypt;
import ch.epfl.lca1.medco.util.Logger;
import edu.harvard.i2b2.common.exception.I2B2Exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by misbach on 17.06.17.
 */
public abstract class AbstractLoader {
    protected Map<String, DataType> datasetFieldsType;

    protected String[] datasetFieldsName;

    protected String providerId;

    protected MedCoDatabase medcoDao;
    protected UnlynxEncrypt encryptor;

    public AbstractLoader(String[] datasetFieldsName, DataType[] datasetFieldsType, String providerId) throws I2B2Exception {

        if (datasetFieldsName.length != datasetFieldsType.length ||
                !DataType.typesArrayIsValid(datasetFieldsType)) {
            throw Logger.error(new IllegalArgumentException("Ill-formated types."));
        }

        this.datasetFieldsName = datasetFieldsName;

        this.datasetFieldsType = new HashMap<>();
        for (int i = 0 ; i < datasetFieldsName.length ; i++) {
            this.datasetFieldsType.put(datasetFieldsName[i], datasetFieldsType[i]);
        }

        this.providerId = providerId;
        medcoDao = new MedCoDatabase();
        encryptor = new UnlynxEncrypt();

    }
}
