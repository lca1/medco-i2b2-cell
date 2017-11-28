package ch.epfl.lca1.medco.i2b2.crc;

import ch.epfl.lca1.medco.i2b2.I2b2Status;
import ch.epfl.lca1.medco.i2b2.MessagesUtil;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.XMLUtil;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.i2b2message.*;
import edu.harvard.i2b2.crc.datavo.setfinder.query.*;
import org.javatuples.Triplet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import javax.xml.bind.JAXBContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;

/**
 * Created by misbach on 15.07.17.
 */
public class I2B2QueryResponse extends ResponseMessageType {

    private static MedCoUtil util = MedCoUtil.getInstance();
    private static MessagesUtil msgUtil = MedCoUtil.getMsgUtil();

    private MasterInstanceResultResponseType parsedBody;

    private QueryResultInstanceType resultPatientSet;
    private QueryResultInstanceType resultPatientCount;

    private String encCountResult;

    private static edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory i2b2OF =
            new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();
    private static edu.harvard.i2b2.crc.datavo.setfinder.query.ObjectFactory querySetFinderOF =
            new edu.harvard.i2b2.crc.datavo.setfinder.query.ObjectFactory();


    public I2b2Status getI2b2Status() throws I2B2XMLException {
        return msgUtil.parseStatus(this);
    }

    /**
     * Extract only the first result type (between PATIENT_SET and PATIENT_COUNT_XML) from the results.
     * Verficication made by the caller.
     */
    private void extractResults() throws I2B2Exception {

        // extract instance result and check status
        for (QueryResultInstanceType queryResultInstance : parsedBody.getQueryResultInstance()) {

            QueryStatusTypeType queryStatus = queryResultInstance.getQueryStatusType();
            Logger.info("Status of query " + parsedBody.getQueryMaster().getName() + " is " + queryStatus.getName() + " / " + queryStatus.getStatusTypeId());

            if (!queryStatus.getStatusTypeId().trim().equals("3")) {
                throw Logger.error(new I2B2Exception("I2b2 query does not have the finished status: " + queryStatus.getName() + " / " + queryStatus.getStatusTypeId()));
            }

            // result type id = 1 ==> type "PATIENTSET"
            // result type id = 4 ==> type "PATIENT_COUNT_XML"
            if (resultPatientSet == null && queryResultInstance.getQueryResultType().getResultTypeId().trim().equals("1")) {
                resultPatientSet = queryResultInstance;
            } else if (resultPatientCount == null && queryResultInstance.getQueryResultType().getResultTypeId().trim().equals("4")) {
                resultPatientCount = queryResultInstance;
            }
        }
    }

    /**
     * Server side constructor
     *
     * @param queryReqResponse
     * @throws I2B2Exception
     */
    public I2B2QueryResponse(ResponseMessageType queryReqResponse) throws I2B2Exception {
        setMessageBody(queryReqResponse.getMessageBody());
        setMessageHeader(queryReqResponse.getMessageHeader());
        setRequestHeader(queryReqResponse.getRequestHeader());
        setResponseHeader(queryReqResponse.getResponseHeader());

        // attempt to bug fix: https://stackoverflow.com/questions/377865/intermittent-classcastexception-from-elementnsimpl-to-own-type-during-unmarshall
        synchronized (JAXBContext.class) {
            try {
                parsedBody = (MasterInstanceResultResponseType) msgUtil.getUnwrapHelper().getObjectByClass(
                        getMessageBody().getAny(), MasterInstanceResultResponseType.class);
            } catch (ClassCastException | JAXBUtilException e) {
                throw Logger.error(new I2B2XMLException("JAXB unwrap failed.", e));
            }

            extractResults();
        }
        Logger.info("Query " + parsedBody.getQueryMaster().getName() + " result: patient set id = " + getPatientSetId() + ", size = " + getPatientSetSize());

    }

    public String getPatientSetId() {
        return resultPatientSet == null ? null : resultPatientSet.getResultInstanceId();
    }

    public int getPatientSetSize() {
        return resultPatientSet == null ? -1 : resultPatientSet.getSetSize();
    }

    public int getPatientCount() {
        return resultPatientCount == null ? -1 : resultPatientCount.getSetSize();
    }

    public void resetResultInstanceListToClearCountOnly() {
        parsedBody.getQueryResultInstance().clear();
        parsedBody.getQueryResultInstance().add(resultPatientCount);
    }

    public void resetResultInstanceListToEncryptedCountOnly() {
        parsedBody.getQueryResultInstance().clear();

        // copy most of the fields value from the original count result
        QueryResultInstanceType resultInstance = querySetFinderOF.createQueryResultInstanceType();
        resultInstance.setResultInstanceId(resultPatientCount.getResultInstanceId());
        resultInstance.setQueryInstanceId(resultPatientCount.getQueryInstanceId());
        resultInstance.setDescription(resultPatientCount.getDescription());
        resultInstance.setQueryResultType(resultPatientCount.getQueryResultType());
        resultInstance.setObfuscateMethod(resultPatientCount.getObfuscateMethod());
        resultInstance.setStartDate(resultPatientCount.getStartDate());
        resultInstance.setEndDate(resultPatientCount.getEndDate());
        resultInstance.setQueryStatusType(resultPatientCount.getQueryStatusType());

        // override 2 specific fields: set size and message (contains the encrypted count)
        XmlValueType xml = querySetFinderOF.createXmlValueType();
        resultInstance.setMessage(xml);
        resultInstance.setSetSize(-1);

        parsedBody.getQueryResultInstance().add(resultInstance);
    }

    /**
     * Sets the encrypted count value both in the class field and into the response XML message.
     * The
     * @param pubKey
     * @param encResult
     * @throws I2B2Exception
     */
    public void setQueryResults(String pubKey, String encResult, JsonObject times) throws I2B2Exception {

        this.encCountResult = encResult;

        JsonObject jsonResults = Json.object()
                .add("pub_key", Json.value(pubKey))
                .add("enc_count_result", Json.value(encResult))
                .add("times", times);

        parsedBody.getQueryResultInstance().get(0).setDescription(jsonResults.toString());
        Logger.debug("MedCo results string is " + jsonResults.toString());
        Logger.info("Query results set for query " + parsedBody.getQueryMaster().getName());
    }

    public String getEncCountResult() {
        return encCountResult;
    }

    public Triplet<String, String, String> getQueryResults() throws I2B2XMLException {

        String jsonResultString = parsedBody.getQueryResultInstance().get(0).getDescription();
        JsonObject jsonResult = Json.parse(jsonResultString).asObject();

        String pubKey = jsonResult.getString("pub_key", "NOT_FOUND"),
                    encResult = jsonResult.getString("enc_count_result", "NOT_FOUND");
        JsonObject times = jsonResult.get("times").asObject();

        return new Triplet<>(pubKey, encResult, times.toString());
    }
}
