package ch.epfl.lca1.medco.i2b2.crc;

import edu.harvard.i2b2.crc.datavo.pdo.*;

import ch.epfl.lca1.medco.util.Logger;
import org.javatuples.Pair;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.util.*;

/**
 * Pid, eid, patient set and event set are populated incrementally by calling addPatientSampleIds.
 * Observer set (provider) populated at object creation.
 * Concepts set populated at object creation (from the ontology loading process)
 *
 * Observation facts loaded incrementally by calling addObservationFact.
 */
public class PatientDataObject extends PatientDataType {

    private static edu.harvard.i2b2.crc.datavo.pdo.ObjectFactory pdoOF =
            new edu.harvard.i2b2.crc.datavo.pdo.ObjectFactory();

    private static final String PROVIDERS_PATH = "\\medco\\institutions\\";//todo: in constants

    /**
     * Contains the pair of patient and sample ids.
     * A: patient id, B: sample id
     */
    private Set<Pair<String, String>> patientSampleIdsPairs;

    private String providerId;

    /**
     * constructo for usage in data
     * @param providerId
     */
    public PatientDataObject(String providerId) {
        this.providerId = providerId;

        patientSampleIdsPairs = new HashSet<>();

        // set up observation set
        ObservationSet observationSet = pdoOF.createObservationSet();
        //obsSet.setPanelName("XXX"); TODO: not sure?
        this.getObservationSet().add(observationSet);

        // set up observer set / provider
        ObserverType xmlProvider = pdoOF.createObserverType();
        xmlProvider.setNameChar(providerId);
        xmlProvider.setObserverCd(providerId);
        xmlProvider.setObserverPath(PROVIDERS_PATH + providerId + "\\");

        ObserverSet observerSet = pdoOF.createObserverSet();
        observerSet.getObserver().add(xmlProvider);
        this.setObserverSet(observerSet);


    }

    /**
     * constructor for usage in ont
     * @param providerId
     * @param concepts
     */
    public PatientDataObject(String providerId, List<ConceptType> concepts) {
        this.providerId = providerId;

        // set up concepts set (from ontology)
        ConceptSet conceptSet = pdoOF.createConceptSet();
        conceptSet.getConcept().addAll(concepts);
        this.setConceptSet(conceptSet);
    }

        public void addPatientSampleIds(String patientId, String sampleId) {
        patientSampleIdsPairs.add(new Pair<>(patientId, sampleId));
    }

    /**
     * Does not set: modifier_cd (default: @), valtype (no value), tval_char, nval_num, valueflag_cd, quantity_num,
     * units_cd, end_date, observation_blob, confidence_num
     *
     * @param patientId
     * @param sampleId
     * @param conceptCd
     * @param startDate
     */
    public void addObservationFact(String patientId, String sampleId, String conceptCd, GregorianCalendar startDate) {
        ObservationType obs = pdoOF.createObservationType();

        PatientIdType xmlPatientId = pdoOF.createPatientIdType();
        xmlPatientId.setValue(patientId);
        xmlPatientId.setSource(providerId);
        obs.setPatientId(xmlPatientId);

        ObservationType.EventId xmlEventId = pdoOF.createObservationTypeEventId();
        xmlEventId.setValue(sampleId);
        xmlEventId.setSource(providerId);
        obs.setEventId(xmlEventId);

        ObservationType.ConceptCd xmlConceptCd = pdoOF.createObservationTypeConceptCd();
        xmlConceptCd.setValue(conceptCd);
        xmlConceptCd.setName(conceptCd);
        obs.setConceptCd(xmlConceptCd);

        ObservationType.ObserverCd xmlObserverCd = pdoOF.createObservationTypeObserverCd();
        xmlObserverCd.setValue(providerId);
        xmlObserverCd.setName(providerId);
        obs.setObserverCd(xmlObserverCd);

        try {
            obs.setStartDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(startDate));
        } catch (DatatypeConfigurationException e) {
            Logger.warn(e);
        }

        ObservationType.LocationCd xmlLocationCd = pdoOF.createObservationTypeLocationCd();
        xmlLocationCd.setValue(providerId);
        xmlLocationCd.setName(providerId);
        obs.setLocationCd(xmlLocationCd);

        ObservationType.ModifierCd xmlModifierCd = pdoOF.createObservationTypeModifierCd();
        xmlModifierCd.setValue("@");
        xmlModifierCd.setName("@");
        obs.setModifierCd(xmlModifierCd);

        ObservationType.InstanceNum xmlInstanceNum = pdoOF.createObservationTypeInstanceNum();
        xmlInstanceNum.setValue("1");
        obs.setInstanceNum(xmlInstanceNum);

        this.getObservationSet().get(0).getObservation().add(obs);
    }

    public void finalizePatientsAndEvents(String providerId) {

        // commit sets to xml (data from patient_mapping and encounter_mapping)
        this.setPidSet(pdoOF.createPidSet());
        this.getPidSet().getPid().clear();

        this.setEidSet(pdoOF.createEidSet());
        this.getEidSet().getEid().clear();

        this.setPatientSet(pdoOF.createPatientSet());
        this.setEventSet(pdoOF.createEventSet());

        for (Pair<String, String> patientSampleIds: patientSampleIdsPairs) {

            // pid
            PidType.PatientId xmlPatientId = pdoOF.createPidTypePatientId();
            xmlPatientId.setValue(patientSampleIds.getValue0());
            xmlPatientId.setSource(providerId);

            PidType xmlPidType = pdoOF.createPidType();
            xmlPidType.setPatientId(xmlPatientId);

            this.getPidSet().getPid().add(xmlPidType);

            // eid
            EidType.EventId xmlSampleId = pdoOF.createEidTypeEventId();
            xmlSampleId.setValue(patientSampleIds.getValue1());
            xmlSampleId.setSource(providerId);
            xmlSampleId.setPatientId(patientSampleIds.getValue0());
            xmlSampleId.setPatientIdSource(providerId);

            EidType xmlEidType = pdoOF.createEidType();
            xmlEidType.setEventId(xmlSampleId);

            this.getEidSet().getEid().add(xmlEidType);

            // patient set
            PatientType xmlPatient = pdoOF.createPatientType();
            PatientIdType xmlPatientId2 = pdoOF.createPatientIdType();
            xmlPatientId2.setSource(providerId);
            xmlPatientId2.setValue(patientSampleIds.getValue0());
            xmlPatient.setPatientId(xmlPatientId2);

            xmlPatient.setSourcesystemCd(providerId);
            this.getPatientSet().getPatient().add(xmlPatient);

            // event set
            EventType xmlEvent = pdoOF.createEventType();
            EventType.EventId xmlEventId = pdoOF.createEventTypeEventId();
            xmlEventId.setSource(providerId);
            xmlEventId.setValue(patientSampleIds.getValue1());
            xmlEvent.setEventId(xmlEventId);

            xmlEvent.setPatientId(xmlPatientId2);
            xmlEvent.setSourcesystemCd(providerId);
            this.getEventSet().getEvent().add(xmlEvent);

        }
    }

}
