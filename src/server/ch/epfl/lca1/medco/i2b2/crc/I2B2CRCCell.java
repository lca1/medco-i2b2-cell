package ch.epfl.lca1.medco.i2b2.crc;

import ch.epfl.lca1.medco.i2b2.MessagesUtil;
import ch.epfl.lca1.medco.util.Constants;
import edu.harvard.i2b2.crc.datavo.i2b2message.BodyType;
import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseMessageType;
import edu.harvard.i2b2.crc.datavo.pdo.EidType;
import edu.harvard.i2b2.crc.datavo.pdo.ParamType;
import edu.harvard.i2b2.crc.datavo.pdo.PatientType;
import edu.harvard.i2b2.crc.datavo.pdo.PidType;
import edu.harvard.i2b2.crc.datavo.pdo.query.*;
import ch.epfl.lca1.medco.i2b2.I2B2Cell;
import ch.epfl.lca1.medco.i2b2.I2b2Status;
import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import edu.harvard.i2b2.crc.loader.datavo.loader.query.*;
import edu.harvard.i2b2.crc.loader.datavo.loader.query.InputOptionListType;
import edu.harvard.i2b2.crc.loader.datavo.loader.query.OutputOptionListType;
import edu.harvard.i2b2.crc.loader.datavo.loader.query.OutputOptionType;
import ch.epfl.lca1.medco.util.Logger;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.javatuples.Pair;

import java.util.*;

/**
 * Represents an i2b2 CRC cell and provides methods to make request against it.
 * A communication error will raise an exception, but if an error happens within the cell the error message will be
 * encapsulated in the response message.
 */
public class I2B2CRCCell extends I2B2Cell {
    // todo: harmonize error handling (i2b2status + exceptions)

    /**
     * URL path suffixes used for request against the CRC cell.
     */
    private static final String URL_PATH_I2B2_PUBLISHDATAREQ = "/publishDataRequest",
                                URL_PATH_I2B2_PDOREQ = "/pdorequest",
                                URL_PATH_I2B2_REQ = "/request";

    /**
     * @param crcCellUrl the URL of the CRC cell, e.g. http://host:port/i2b2/QueryService
     * @param header the header used to contact the CRC cell
     */
    public I2B2CRCCell(String crcCellUrl , MedCoI2b2MessageHeader header) {
        super(crcCellUrl, header);
    }

    /**
     * Make a query request against the CRC.
     *
     * @param request the query request
     * @return the query response
     *
     * @throws I2B2Exception if a communication error occurs during the query.
     */
    public I2B2QueryResponse queryRequest(I2B2QueryRequest request) throws I2B2Exception {

        // make query request (from query definition) with a patient set result output
        I2B2QueryResponse parsedResp;
        try {
            ResponseMessageType resp = requestToCell(URL_PATH_I2B2_REQ, request);
            parsedResp = new I2B2QueryResponse(resp);
            Logger.info("CRC query request result: " + parsedResp.getI2b2Status());

        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }

        return parsedResp;
    }

    /**
     * Given the original i2b2 query that has been stripped from the encrypted query terms, query the i2b2 crc for
     * the corresponding patient set, includes the enc dummies
     *
     * @return list of ids + list of dummy flags
     *
     * @throws I2B2Exception if a communication error occurs during the query.
     */
    public Pair<List<String>, List<String>> queryForPatientSet(String patientSetId, boolean getDummyFlags) throws I2B2Exception {


        // make query to get the actual patient set with the id
        PatientListType pdoQueryPatientList = queryPdoOF.createPatientListType();
        pdoQueryPatientList.setPatientSetCollId(patientSetId);
        Pair<I2b2Status, PatientDataResponseType> patientSetResponse = pdoQueryFromInputList(null, null, pdoQueryPatientList,
                null, false, false,false, true, false, false, false, false);

        if (patientSetResponse.getValue0() != I2b2Status.DONE) {
            throw Logger.error(new I2B2Exception("Query for patient set id " + patientSetId + " to i2b2 CRC failed."));
        }

        // extract the list of patient ids and enc dummy flags
        List<String> patientIds = new ArrayList<>();
        List<String> patientEncDummyFlags = new ArrayList<>();
        for (PatientType patientType : patientSetResponse.getValue1().getPatientData().getPatientSet().getPatient()) {
            patientIds.add(patientType.getPatientId().getValue());
            Logger.debug("Patient id extracted: " + patientIds.get(patientIds.size() - 1));

            if (getDummyFlags) {
                for (ParamType paramType : patientType.getParam()) {
                    if (paramType.getColumn().trim().equals(Constants.PATIENT_DUMMY_FLAG_COL_NAME)) {
                        patientEncDummyFlags.add(paramType.getValue());
                        Logger.debug("Patient dummy flag extracted: " + patientEncDummyFlags.get(patientEncDummyFlags.size() - 1));
                    }
                }

            }
        }

        if (getDummyFlags && patientEncDummyFlags.size() != patientIds.size()) {
            Logger.warn("Dummy flags requested but mismatch between nb patient ids (" + patientIds.size() + ") and nb dummy flags (" + patientEncDummyFlags.size() + ")");
        }

        Logger.info("Query for patient set id " + patientSetId + " returning a patient set of size " + patientIds.size());
        return new Pair<>(patientIds, patientEncDummyFlags);
    }

    /**
     *
     * Error checking left for the caller
     * @param fileRepoLocationURI
     * @param providerId
     * @return
     * @throws I2B2Exception
     */
    public Pair<I2b2Status, LoadDataResponseType> publishData(String fileRepoLocationURI, String providerId) throws I2B2Exception {

        PublishDataRequestType publishReq = loaderCrcOF.createPublishDataRequestType();

        {
            InputOptionListType input = loaderCrcOF.createInputOptionListType();
            DataListType dataList = loaderCrcOF.createDataListType();

            DataListType.LocationUri locationUri = loaderCrcOF.createDataListTypeLocationUri();
            locationUri.setProtocolName("FR");
            locationUri.setValue(fileRepoLocationURI);
            dataList.setLocationUri(locationUri);

            dataList.setDataFormatType(DataFormatType.PDO);
            dataList.setLoadLabel("MedCo dataset loading");
            dataList.setSourceSystemCd(providerId);

            input.setDataFile(dataList);
            publishReq.setInputList(input);
        } {
            LoadType loadList = loaderCrcOF.createLoadType();
            loadList.setCommitFlag(true);
            loadList.setClearTempLoadTables(true);

            FactLoadOptionType factLoad = loaderCrcOF.createFactLoadOptionType();
            factLoad.setAppendFlag(true);
            factLoad.setIgnoreBadData(true);
            loadList.setLoadObservationSet(factLoad);

            LoadOptionType loadOption = loaderCrcOF.createLoadOptionType();
            loadOption.setIgnoreBadData(true);
            loadList.setLoadEventSet(loadOption);
            loadList.setLoadPatientSet(loadOption);
            loadList.setLoadObserverSet(loadOption);
            loadList.setLoadPidSet(loadOption);
            loadList.setLoadEidSet(loadOption);
            loadList.setLoadConceptSet(loadOption);
            //loadList.setLoadModifierSet(loadOption); // XXX: modifiers not used in current implementation

            publishReq.setLoadList(loadList);
        } {
            OutputOptionListType outputList = loaderCrcOF.createOutputOptionListType();
            OutputOptionType outputOption = loaderCrcOF.createOutputOptionType();
            outputOption.setBlob(false);
            outputOption.setOnlykeys(true);
            outputOption.setTechdata(false);

            outputList.setObservationSet(outputOption);
            outputList.setPatientSet(outputOption);
            outputList.setEventSet(outputOption);
            outputList.setObserverSet(outputOption);
            outputList.setConceptSet(outputOption);
            //outputList.setModifierSet(outputOption); // XXX: modifiers not used in current implementation
            outputList.setPidSet(outputOption);
            outputList.setEidSet(outputOption);

            publishReq.setOutputList(outputList);
        }

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(loaderCrcOF.createPublishDataRequest(publishReq));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_PUBLISHDATAREQ, body);
            Logger.info("publish data req result: " + resp.getValue0());

            // error checking and handling completely left to the caller
            return new Pair<>(
                    resp.getValue0(),
                    (LoadDataResponseType) MessagesUtil.getUnwrapHelper().getObjectByClass(
                            resp.getValue1().getAny(), LoadDataResponseType.class));

        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    /**
     * Returns the HIVE id of sample and patients (the internal i2b2 number).
     *
     * @param ids pairs of {sample_id, source/provider_id}
     * @return pair of maps: [key=sample_id ; value=sample_num] , [key=patient_id ; value=patient_num]
     * nope: sample_id->sample_num,patient_num
     */
    public Map<String, Pair<String, String>> getSampleAndPatientNums(Set<Pair<String, String>> ids) throws I2B2Exception {

        // construct xml request for samples
        EidListType eids = queryPdoOF.createEidListType();
        for (Pair<String, String> id: ids) {
            EidListType.Eid eid = queryPdoOF.createEidListTypeEid();
            eid.setValue(id.getValue0());
            eid.setSource(id.getValue1());
            //XXX: index parameter ??? what does it do?
            eids.getEid().add(eid);
        }

        // make request for samples
        Pair<I2b2Status, PatientDataResponseType> pdoResultSamples =
                pdoQueryFromInputList(eids, null, null, null, false, true,
                false, false, false, false, false, true);

        if (pdoResultSamples.getValue0() != I2b2Status.DONE) {
            Logger.warn("PDO request status is not done, expect problems: " + pdoResultSamples.getValue0());
        }

        // extract the results / samples and construct xml for patients
        Map<String, Pair<String, String>> resultMap = new HashMap<>();
        List<EidType> resultEids = pdoResultSamples.getValue1().getPatientData().getEidSet().getEid();
        PidListType pids = queryPdoOF.createPidListType();

        for (EidType resultEid : resultEids) {
            for (EidType.EventMapId resultMapEid: resultEid.getEventMapId()) {

                // add to result map for sample
                resultMap.put(
                        resultMapEid.getValue(), // the ID in source system (not hive)
                        new Pair<>(
                                resultEid.getEventId().getValue(), // the sample_num (HIVE number)
                                resultEid.getEventId().getPatientId() // the patient_id (not num for now)
                ));
            }

            // construct query for patient
            PidListType.Pid pid = queryPdoOF.createPidListTypePid();
            pid.setValue(resultEid.getEventId().getPatientId()); // patient_id
            pid.setSource(resultEid.getEventId().getPatientIdSource()); // source / provider
            pids.getPid().add(pid);
        }

        // make request for patients
        Pair<I2b2Status, PatientDataResponseType> pdoResultPatients =
                pdoQueryFromInputList(null, pids, null, null, false, true,
                        false, false, false, false, true, false);

        if (pdoResultPatients.getValue0() != I2b2Status.DONE) {
            Logger.warn("PDO request status is not done, expect problems: " + pdoResultPatients.getValue0());
        }

        // extract the results / patients
        Map<String, String> patientsMap = new HashMap<>();
        List<PidType> resultPids = pdoResultPatients.getValue1().getPatientData().getPidSet().getPid();
        for (PidType resultPid : resultPids) {
            for (PidType.PatientMapId resultMapPid: resultPid.getPatientMapId()) {
                patientsMap.put(
                        resultMapPid.getValue(), // the ID in source system (not hive)
                        resultPid.getPatientId().getValue() // the patient_num (HIVE number)
                );
            }
        }

        // merge the patients map into the result map
        for (Map.Entry<String, Pair<String, String>> entry : resultMap.entrySet()) {
            resultMap.replace(
                    entry.getKey(),
                    entry.getValue().setAt1(
                            patientsMap.get(
                                    entry.getValue().getValue1()))
            );
        }

        return resultMap;
    }

    /**
     * Note: only 1 of the 4 is taken into account (put the rest to null)
     *
     * @param eidList
     * @param pidList
     * @param patientList
     * @param eventList
     *
     * @param getBlob true to get blob column
     * @param getOnlykeys true to get only the primary keys
     * @param getTechdata true to get the tech data
     * @return
     * @throws I2B2Exception
     */
    private Pair<I2b2Status, PatientDataResponseType> pdoQueryFromInputList(
            EidListType eidList, PidListType pidList, PatientListType patientList, EventListType eventList,
            boolean getBlob, boolean getOnlykeys, boolean getTechdata,
            boolean queryPatientSet, boolean queryObservationSet, boolean queryEventSet, boolean queryPidSet, boolean queryEidSet)
            throws I2B2Exception {

        PdoQryHeaderType queryHeader = queryPdoOF.createPdoQryHeaderType();
        queryHeader.setRequestType(PdoRequestTypeType.GET_PDO_FROM_INPUT_LIST);

        GetPDOFromInputListRequestType queryRequest = queryPdoOF.createGetPDOFromInputListRequestType();

        {
            edu.harvard.i2b2.crc.datavo.pdo.query.InputOptionListType inputOptionList = queryPdoOF.createInputOptionListType();

            if (eidList != null) {
                inputOptionList.setEidList(eidList);
            } else if (pidList != null) {
                inputOptionList.setPidList(pidList);
            } else if (patientList != null) {
                inputOptionList.setPatientList(patientList);
            } else if (eventList != null) {
                inputOptionList.setEventList(eventList);
            } else {
                throw Logger.error(new IllegalArgumentException("All input lists are null."));
            }
            queryRequest.setInputList(inputOptionList);
        } {
            FilterListType filterList = queryPdoOF.createFilterListType();
            // todo: left empty for now
            queryRequest.setFilterList(filterList);
        } {
            edu.harvard.i2b2.crc.datavo.pdo.query.OutputOptionListType outputOptionList = queryPdoOF.createOutputOptionListType();
            outputOptionList.setNames(OutputOptionNameType.NONE); // do not use code_lookup to resolve code to their names

            // common attributes for the following returned elements
            edu.harvard.i2b2.crc.datavo.pdo.query.OutputOptionType outputOption = queryPdoOF.createOutputOptionType();
            outputOption.setBlob(getBlob);
            outputOption.setOnlykeys(getOnlykeys);
            outputOption.setTechdata(getTechdata);
            outputOption.setSelect(OutputOptionSelectType.USING_INPUT_LIST);

            if (queryPatientSet) {
                outputOptionList.setPatientSet(outputOption);
            }

            if (queryObservationSet) {
                edu.harvard.i2b2.crc.datavo.pdo.query.FactOutputOptionType factOutputOption = queryPdoOF.createFactOutputOptionType();
                factOutputOption.setBlob(outputOption.isBlob());
                factOutputOption.setOnlykeys(outputOption.isOnlykeys());
                factOutputOption.setTechdata(outputOption.isTechdata());
                factOutputOption.setSelect(outputOption.getSelect());
                outputOptionList.setObservationSet(factOutputOption);
            }

            if (queryEventSet) {
                outputOptionList.setEventSet(outputOption);
            }

            if (queryPidSet) {
                outputOptionList.setPidSet(outputOption);
            }

            if (queryEidSet) {
                outputOptionList.setEidSet(outputOption);
            }

            queryRequest.setOutputOption(outputOptionList);
        }

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(queryPdoOF.createPdoheader(queryHeader));
        body.getAny().add(queryPdoOF.createRequest(queryRequest));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_PDOREQ, body);
            Logger.info("pdo query result: " + resp.getValue0());

            // error checking and handling completely left to the caller
            return new Pair<>(
                    resp.getValue0(),
                    (PatientDataResponseType) msgUtil.getUnwrapHelper().getObjectByClass(
                            resp.getValue1().getAny(), PatientDataResponseType.class));

        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }
}
