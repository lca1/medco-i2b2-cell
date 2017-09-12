package ch.epfl.lca1.medco.i2b2.ont;

import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import edu.harvard.i2b2.crc.datavo.ontology.*;
import edu.harvard.i2b2.crc.datavo.ontology.ConceptType;
import ch.epfl.lca1.medco.i2b2.I2B2Cell;
import ch.epfl.lca1.medco.i2b2.I2b2Status;
import ch.epfl.lca1.medco.i2b2.MessagesUtil;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.ConceptNotFoundException;
import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.crc.datavo.i2b2message.BodyType;
import org.javatuples.Pair;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.util.*;

// todo: harmonize error handling (i2b2status + exceptions)
public class I2B2ONTCell extends I2B2Cell {

    private static final String URL_PATH_I2B2_LOADMETADATA = "/loadMetadata",
                                URL_PATH_I2B2_GETTERMINFO = "/getTermInfo",
                                URL_PATH_I2B2_GETCHILDREN = "/getChildren",
                                URL_PATH_I2B2_DELETECHILD = "/deleteChild",
                                URL_PATH_I2B2_ADDCHILD = "/addChild",
                                URL_PATH_I2B2_GETCODEINFO = "/getCodeInfo";

    public static final String  ONT_PATH_ROOT = "\\medco\\",
                                ONT_PATH_CLINICAL = ONT_PATH_ROOT + "clinical\\",
                                ONT_PATH_GENOMIC = ONT_PATH_ROOT + "genomic\\",
                                ONT_PATH_CLINICAL_SENSITIVE = ONT_PATH_CLINICAL + "sensitive\\",
                                ONT_PATH_CLINICAL_NONSENSITIVE = ONT_PATH_CLINICAL + "nonsensitive\\";

    public static final String  ONT_PATH_NEXT_ID_SENSITIVE = ONT_PATH_CLINICAL_SENSITIVE + "next_usable_id\\",
                                ONT_PATH_NEXT_ID_NONSENSITIVE = ONT_PATH_CLINICAL_NONSENSITIVE + "next_usable_id\\";

    public static final String  BASECODE_PREFIX_ENC = "MEDCO_ENC",
                                BASECODE_PREFIX_CLEAR = "MEDCO_CLEAR",
                                BASECODE_PREFIX_ADMIN = "MEDCO_ADMIN",
                                BASECODE_PREFIX_GEN = "MEDCO_GEN";

    public static final String  TABLE_CD_CLINICAL_SENSITIVE = "CLINICAL_SENSITIVE",
                                TABLE_CD_CLINICAL_NON_SENSITIVE = "CLINICAL_NON_SENSITIVE",
                                TABLE_CD_GENOMIC = "GENOMIC";



    /**
     * Pairs of tables in ontology + concepts to be loaded.
     */
    private Map<String, List<OntologyDataType>> conceptsListByTable;

    public I2B2ONTCell(MedCoI2b2MessageHeader auth) {
        super(medCoUtil.getOntologyCellUrl(), auth); // get url

        conceptsListByTable = new HashMap<>();//ontOF.createMetadataLoadType();
    }

    // for node: a contianer not queryable, lvl=1, + the values of container, lvl=2
    //
	//public void addConcept(String path, String uniqueName,
      //                           long ontologyId, String valueType) throws I2B2Exception {
        //have enum for categories??
        // key: \\C_TABLE_CD\C_FULLNAME
        //C_TABLE_NAME: medco_sensitive, medco_non_sensitive //
        // C_TABLE_CD: medco_clinical_sensitive, medco_clinical_non_sensitive, medco_genomic_annotations
        // fullname: \medco\clinical\sensitive
        // TODO
        // basecode === valuetype:id #
        // value type: UNLYNX_ENC, etc
        // prefill table access, schemes (categeries in basecode)
    //    String fullName = path + uniqueName + "\\";

    //    addConcept(
     //           1, uniqueName, "N", "LA", 0, valueType + ":" + ontologyId, fullName,
     //           "concept_cd", "concept_path", "T", "LIKE", "",
     //           fullName, valueType, fullName, "concept_dimension", "@", new GregorianCalendar(),
      //          null);
    //}

    public void accumulateConcept(String name, String path, String visualAttr, String code, String basecodeSuffix,
                                  String comment, String ontologyTable, String metadataxml, List<edu.harvard.i2b2.crc.datavo.pdo.ConceptType> pdoConcepts) throws I2B2Exception {
        String fullName = path + name + "\\";
        int lvl = path.split("\\\\").length - 1;
        String basecode = basecodeSuffix == null ? null : code + ":" + basecodeSuffix;

        accumulateConcept(
                lvl, name, "N", visualAttr, 0, basecode, fullName,
                "concept_cd", "concept_path", "T", "LIKE",
                comment, fullName, code, fullName,
                "concept_dimension", "@", new GregorianCalendar(), metadataxml, ontologyTable);
        Logger.debug("Concept accumulated: " + fullName + ", " + basecode);


        // concept_dimension table in CRC, only for queriable concepts
        if (pdoConcepts != null && basecode != null && !basecodeSuffix.equals(BASECODE_PREFIX_ADMIN)) {
            edu.harvard.i2b2.crc.datavo.pdo.ConceptType pdoConcept = pdoOF.createConceptType();
            pdoConcept.setConceptPath(fullName);
            pdoConcept.setConceptCd(basecode);
            pdoConcept.setNameChar(name);
            pdoConcepts.add(pdoConcept);

            Logger.debug("Concept added to pdo concept dimension: " + fullName + ", " + basecode);
        }
    }


    /*
    medco ontology: lvl 0
    clinical / genomical: lvl 1
    etc.
    doesn't need the table cd here
     */
	private void accumulateConcept(int level, String name, String synonymCd, String visualAttributes, int totalNum,
                                 String basecode, String dimcode, String factTableColumn, String columnName,
                                 String columnDataType, String operator, String comment, String tooltip, String valueTypeCd,
                                 String fullName, String dimtablename, String appliedPath, GregorianCalendar updateDate,
                                 String metadataXml, String ontologyTable) throws I2B2Exception {

        OntologyDataType concept = ontOF.createOntologyDataType();
        concept.setLevel(level);
        concept.setName(name);
        concept.setSynonymCd(synonymCd);
        concept.setVisualattributes(visualAttributes);
        concept.setTotalnum(totalNum);
        concept.setBasecode(basecode);
        concept.setFacttablecolumn(factTableColumn);
        concept.setColumnname(columnName);
        concept.setColumndatatype(columnDataType);
        concept.setOperator(operator);
        concept.setDimcode(dimcode);
        concept.setComment(comment);
        concept.setTooltip(tooltip);
        concept.setValuetypeCd(valueTypeCd);
        concept.setFullname(fullName);
        concept.setDimtablename(dimtablename);
        concept.setAppliedPath(appliedPath);

        try {
            concept.setUpdateDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(updateDate));
        } catch (DatatypeConfigurationException e) {
            throw Logger.error(new I2B2XMLException("Could not create calendar", e));
        }

        if (metadataXml != null) {
            XmlValueType xmlMetadataXml = ontOF.createXmlValueType();
            xmlMetadataXml.getAny().add(edu.harvard.i2b2.common.util.xml.XMLUtil.convertStringToDOM(metadataXml).getDocumentElement());
            concept.setMetadataxml(xmlMetadataXml);
        }

        // ontologyTable
        if (!conceptsListByTable.containsKey(ontologyTable)) {
            conceptsListByTable.put(ontologyTable, new ArrayList<>());
        }
        conceptsListByTable.get(ontologyTable).add(concept);
    }

/*
    public void addAdmin(String path, String uniqueName,
                           int ontologyId, String valueType) throws I2B2Exception {
        //have enum for categories??
        // key: \\C_TABLE_CD\C_FULLNAME
        //C_TABLE_NAME: medco_sensitive, medco_non_sensitive //
        // C_TABLE_CD: medco_clinical_sensitive, medco_clinical_non_sensitive, medco_genomic_annotations
        // fullname: \medco\clinical\sensitive
        // TODO
        // basecode === valuetype:id #
        // value type: UNLYNX_ENC, etc
        // prefill table access, schemes (categeries in basecode)
        String fullName = path + uniqueName + "\\";

        addConcept(
                1, uniqueName, "N", "LA", 0, valueType + ontologyId, fullName,
                "concept_cd", "concept_path", "T", "LIKE", "",
                fullName, valueType, fullName, "concept_dimension", "@", new GregorianCalendar(),
                null);
    }*/


    public String getClearConceptCd(String fieldName, String fieldValue) throws I2B2Exception, UnlynxException {
        String searchPath = "\\\\" + TABLE_CD_CLINICAL_NON_SENSITIVE + ONT_PATH_CLINICAL_NONSENSITIVE +
                fieldName + "\\" + fieldValue + "\\";

        ConceptsType concepts = getTermInfo(searchPath, "default", false,
                false, false, 1);

        if (concepts.getConcept().size() > 1) {
            throw Logger.error(new UnlynxException("Database inconsistent, size 1 != " + concepts.getConcept().size()));
        } else if (concepts.getConcept().size() == 0) {
            throw Logger.warn(new ConceptNotFoundException("Database inconsistent, size 1 != " + concepts.getConcept().size()));
        }

        return concepts.getConcept().get(0).getBasecode();
    }

    public long getEncryptId(String fieldName, String fieldValue) throws I2B2Exception, UnlynxException {

        String searchPath = "\\\\" + TABLE_CD_CLINICAL_SENSITIVE + ONT_PATH_CLINICAL_SENSITIVE +
                fieldName + "\\" + fieldValue + "\\";

        ConceptsType concepts = getTermInfo(searchPath, "default", false,
                false, false, 1);

        if (concepts.getConcept().size() > 1) {
            throw Logger.error(new UnlynxException("Database inconsistent, size 1 != " + concepts.getConcept().size()));
        } else if (concepts.getConcept().size() == 0) {
            throw Logger.warn(new ConceptNotFoundException("Database inconsistent, size 1 != " + concepts.getConcept().size()));
        }

        try {
            return Long.parseLong(concepts.getConcept().get(0).getBasecode().split(":")[1]);
        } catch (NumberFormatException e) {
            throw Logger.error(new UnlynxException("Database inconsistent", e));
        }
    }

    public long getNextEncUsableId() throws I2B2Exception, UnlynxException {
        return getNextUsableId("\\\\" + TABLE_CD_CLINICAL_SENSITIVE + ONT_PATH_NEXT_ID_SENSITIVE);
    }

    public long getNextClearUsableId() throws I2B2Exception, UnlynxException {
        return getNextUsableId("\\\\" + TABLE_CD_CLINICAL_NON_SENSITIVE + ONT_PATH_NEXT_ID_NONSENSITIVE);
    }

    private long getNextUsableId(String searchkey) throws I2B2Exception, UnlynxException {
        ConceptsType concepts =
                getChildren(searchkey, "default", 1, true, false, false);

        if (concepts.getConcept().size() != 1) {
            throw Logger.error(new UnlynxException("Database inconsistent"));
        }

        try {
            return Long.parseLong(concepts.getConcept().get(0).getName());
        } catch (NumberFormatException e) {
            throw Logger.error(new UnlynxException("Database inconsistent", e));
        }
    }

    public void updateNextEncUsableId(long id) throws I2B2Exception {
        updateNextUsableId(id, "\\\\" + TABLE_CD_CLINICAL_SENSITIVE + ONT_PATH_NEXT_ID_SENSITIVE,
                "nextEncUsableId", ONT_PATH_NEXT_ID_SENSITIVE, "The next usable id for Unlynx encryption.");
    }

    public void updateNextClearUsableId(long id) throws I2B2Exception {
        updateNextUsableId(id, "\\\\" + TABLE_CD_CLINICAL_NON_SENSITIVE + ONT_PATH_NEXT_ID_NONSENSITIVE,
                "nextClearUsableId", ONT_PATH_NEXT_ID_NONSENSITIVE, "The next usable id for non sensitive clinical data.");
    }

    private void updateNextUsableId(long id, String searchKey, String basecodeSuffix, String dimcode, String comment) throws I2B2Exception {
        ConceptsType concepts =
                getChildren(searchKey, "default", 1, true, false, false);

        // delete previous one (normally only 1 entry)
        for (ConceptType concept : concepts.getConcept()) {
            I2b2Status status = deleteChild(concept.getKey(), concept.getBasecode(), true,
                    concept.getLevel(), concept.getName(), "Y", null);

            if (status != I2b2Status.DONE) {
                Logger.warn("deleteChild status: " + status + ", " + status.getStatusMessage());
            } else {
                Logger.info("Deleted old next usable ID");
            }
        }

        // add the new one
        String basecode = BASECODE_PREFIX_ADMIN + ":" + basecodeSuffix;
        I2b2Status status = addChild(
                4, searchKey + id + "\\", id + "", basecode, "T",
                "concept_path", dimcode + id + "\\", "concept_cd", comment,
                "LH", BASECODE_PREFIX_ADMIN, "N", "LIKE", "concept_dimension", null, 0,
                new GregorianCalendar(), null);
        if (status != I2b2Status.DONE) {
            Logger.warn("addChild status: " + status + ", " + status.getStatusMessage());
        } else {
            Logger.info("Updated next usable ID");
        }
    }

    public boolean codeExists(String code) throws I2B2Exception {
        ConceptsType concepts = getCodeInfo(code, "default", "contains", false, true, false);
        return (concepts.getConcept().size() != 0);
    }

    // ------------------------------------------------------------
    // raw methods to contact I2b2 cell
    // ------------------------------------------------------------
    private I2b2Status deleteChild(String key, String basecode, boolean includeChildren, int lvl, String name,
                                  String synonyms, String visualAttr) throws I2B2Exception {

        DeleteChildType deleteChild = new DeleteChildType();
        deleteChild.setKey(key);
        deleteChild.setBasecode(basecode);
        deleteChild.setIncludeChildren(includeChildren);
        deleteChild.setLevel(lvl);
        deleteChild.setName(name);
        deleteChild.setSynonymCd(synonyms);
        deleteChild.setVisualattribute(visualAttr);

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(ontOF.createDeleteChild(deleteChild));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_DELETECHILD, body);
            Logger.info("delete child result: " + resp.getValue0());

            return resp.getValue0();
        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    private I2b2Status addChild(int lvl, String key, String name, String basecode, String columnDataType, String columnName,
                                String dimcode, String factTableCol, String comment, String visualAttr, String valueType,
                                String synonyms, String operator, String tableName, String tooltip, Integer totalNum,
                                GregorianCalendar updateDate, String metadataXml) throws I2B2Exception {

        ConceptType add = new ConceptType();
        add.setLevel(lvl);
        add.setKey(key);
        add.setName(name);
        add.setBasecode(basecode);
        add.setColumndatatype(columnDataType);
        add.setColumnname(columnName);
        add.setDimcode(dimcode);
        add.setFacttablecolumn(factTableCol);
        add.setComment(comment);
        add.setVisualattributes(visualAttr);
        add.setValuetypeCd(valueType);
        add.setSynonymCd(synonyms);
        add.setOperator(operator);
        add.setTablename(tableName);
        add.setTooltip(tooltip);
        add.setTotalnum(totalNum);

        try {
            add.setUpdateDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(updateDate));
        } catch (DatatypeConfigurationException e) {
            throw Logger.error(new I2B2XMLException("Could not create calendar", e));
        }

        if (metadataXml != null) {
            XmlValueType xmlMetadataXml = ontOF.createXmlValueType();
            xmlMetadataXml.getAny().add(edu.harvard.i2b2.common.util.xml.XMLUtil.convertStringToDOM(metadataXml).getDocumentElement());
            add.setMetadataxml(xmlMetadataXml);
        }

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(ontOF.createAddChild(add));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_ADDCHILD, body);
            Logger.info("add child result: " + resp.getValue0());

            return resp.getValue0();
        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    private ConceptsType getChildren(String parent, String type, int max, boolean hiddens, boolean synonyms, boolean blob) throws I2B2Exception {

        GetChildrenType getChildren = new GetChildrenType();
        getChildren.setMax(max);//1
        getChildren.setHiddens(hiddens);
        getChildren.setSynonyms(synonyms);
        getChildren.setType(type);// default, all, core
        getChildren.setParent(parent);
        getChildren.setBlob(blob);

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(ontOF.createGetChildren(getChildren));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_GETCHILDREN, body);
            Logger.info("get children result: " + resp.getValue0() + ", " + resp.getValue0().getStatusMessage());

            return (ConceptsType) msgUtil.getUnwrapHelper().getObjectByClass(resp.getValue1().getAny(), ConceptsType.class);
        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    /**
     * TODO doc
     * Sends request to i2b2 ontology cell of loading the concepts accumulated with addConcept in the field conceptsList.
     * conceptsList is emptied if the process succeed.XXX
     *
     * @param path the ontology table in which the concept should be stored
     *                          @param dataType: default, code, all
     * @return the {@link I2b2Status}
     * @throws I2B2Exception if the i2b2 request fails not gracefully
     */
    private ConceptsType getTermInfo(String path, String dataType, boolean getBlob,
                                    boolean getHiddens, boolean getSynonyms, int max) throws I2B2Exception {

        GetTermInfoType termInfo = ontOF.createGetTermInfoType();
        termInfo.setBlob(getBlob);
        termInfo.setHiddens(getHiddens);
        termInfo.setSynonyms(getSynonyms);
        termInfo.setType(dataType);
        termInfo.setMax(max);
        termInfo.setSelf(path);

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(ontOF.createGetTermInfo(termInfo));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_GETTERMINFO, body);
            Logger.info("get term info result: " + resp.getValue0());

            return (ConceptsType) MessagesUtil.getUnwrapHelper().getObjectByClass(
                    resp.getValue1().getAny(), ConceptsType.class);

        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    private ConceptsType getCodeInfo(String code, String dataType, String strategy, boolean getBlob,
                                     boolean getHiddens, boolean getSynonyms) throws I2B2Exception {

        VocabRequestType vocab = ontOF.createVocabRequestType();
        vocab.setBlob(getBlob);
        vocab.setHiddens(getHiddens);
        vocab.setSynonyms(getSynonyms);
        vocab.setType(dataType);

        MatchStrType matchStr = ontOF.createMatchStrType();
        matchStr.setStrategy(strategy);
        matchStr.setValue(code);
        vocab.setMatchStr(matchStr);

        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(ontOF.createGetCodeInfo(vocab));

        try {
            Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_GETCODEINFO, body);
            Logger.info("get code info result: " + resp.getValue0());

            return (ConceptsType) resp.getValue1().getAny().get(0);
        } catch (Exception e) {
            throw Logger.error(new I2B2Exception("Request failed.", e));
        }
    }

    /**
     * Sends request to i2b2 ontology cell of loading the concepts accumulated with addConcept in the field conceptsList.
     * conceptsList is emptied if the process succeed.
     * Send one message per annotation!
     *
     * @return the {@link I2b2Status}
     * @throws I2B2Exception if the i2b2 request fails not gracefully
     */
    public I2b2Status loadConcepts() throws I2B2Exception {

        I2b2Status reqStatus = null;
        int conceptsLoadedCount = 0;

        for (Map.Entry<String, List<OntologyDataType>> entries : conceptsListByTable.entrySet()) {

            for (OntologyDataType concept : entries.getValue()) {
                MetadataLoadType loadType = ontOF.createMetadataLoadType();
                loadType.setTableName(entries.getKey());
                loadType.getMetadata().add(concept);

                BodyType body = i2b2OF.createBodyType();
                body.getAny().add(ontOF.createLoadMetadata(loadType));

                try {
                    Pair<I2b2Status, BodyType> resp = requestToCell(URL_PATH_I2B2_LOADMETADATA, body);
                    reqStatus = resp.getValue0();
                    Logger.info("load metadata result: " + reqStatus + ", msg: " + reqStatus.getStatusMessage());

                    if (reqStatus != I2b2Status.DONE) {
                        Logger.error("Request failed for " + concept.getName() + ", continuing");
                        //return reqStatus;
                        continue;
                    }
                } catch (Exception e) {
                    Logger.error("Request failed for " + concept.getName() + ", continuing", e);
                }

                conceptsLoadedCount++;
            }
        }

        // everything loaded, clear the concepts
        Logger.info("Loaded " + conceptsLoadedCount + " concepts in i2b2 ontology");
        conceptsListByTable.clear();
        return reqStatus;
    }
}
