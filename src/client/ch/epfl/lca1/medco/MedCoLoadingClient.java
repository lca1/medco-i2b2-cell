package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.axis2.MedCoQueryRequestDelegate;
import ch.epfl.lca1.medco.dao.MedCoDatabase;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryResponse;
import ch.epfl.lca1.medco.i2b2.pm.UserAuthentication;
import ch.epfl.lca1.medco.loader.DataType;
import ch.epfl.lca1.medco.unlynx.UnlynxDecrypt;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import com.opencsv.CSVReader;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.apache.commons.cli.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.javatuples.Triplet;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by misbach on 15.07.17.
 */
public class MedCoLoadingClient {

    public static void main(String[] args) throws InterruptedException, I2B2Exception, IOException, UnlynxException {

        Logger.getRootLogger().setLevel(Level.INFO);

        // rerun for each dataset setting:
        //loadStandardDataset("quarter"); //quadruple, double, normal, half, quarter
        //loadClearDataset("quadruple");
        //System.out.println(StopWatch.misc.prettyPrint());
        //loadFullGenomicClearOntology();
        loadProfileManyNode();
    }

    public static void loadFullGenomicClearOntology() throws I2B2Exception, IOException {
        loadSrv5Conf();
        String
                dir = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/";

        MedCoDatabase dao = new MedCoDatabase();
        CSVReader genomicReader = new CSVReader(new FileReader(dir + "data_mutations_extended_skcm_broad_clear_i2b2.txt"), '\t', '\u0000', 0);
        String[] genomicHeader = genomicReader.readNext();

        String[] genomicEntry;
        int count = 0;
        while ((genomicEntry = genomicReader.readNext()) != null) {
            try {

                // ontology entry
                dao.batchSqlStatements.add(
                        "insert into i2b2metadata.clinical_non_sensitive" +
                                "(c_hlevel, c_fullname, c_name, c_synonym_cd, c_visualattributes, c_basecode, c_facttablecolumn, c_tablename, c_columnname, c_columndatatype, c_operator, c_dimcode, update_date, valuetype_cd, m_applied_path) values(" +
                                "'4', '\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + genomicEntry[2] + "\\', '" + genomicEntry[2] + "', 'N', 'LA', '" + genomicEntry[2] + "', 'concept_cd', 'concept_dimension', 'concept_path', 'T', 'LIKE', " +
                                "'\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + genomicEntry[2] + "\\', now(), 'MEDCO_CLEAR', '@') ON CONFLICT DO NOTHING");

            } catch (Throwable e) {
                System.out.println("ignoring entry");
            }

        }

        dao.sqlBatchUpdate();
        // concept dim entry: all at once
        dao.sqlUpdate("insert into i2b2demodata.concept_dimension(concept_path, concept_cd, name_char, import_date)" +
                "select c_fullname, c_basecode, c_name, update_date from i2b2metadata.clinical_non_sensitive where c_basecode is not null ON CONFLICT DO NOTHING;");//c_fullname like '%VARIANT_ID%' and

    }

    public static void loadClearDataset(String datasetSplit) throws UnlynxException, IOException, I2B2Exception {

        // change me node number
        loadSrv1Conf();

        //StopWatch.misc.start("clear dataset loading: clinical ontology");
        String
                dir = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/",
                nodeId = "1",
                datasetId = "skcm_broad_clear_i2b2_part"+nodeId+"_" + datasetSplit;


        System.out.println("Starting loading node " + nodeId);
        MedCoDataLoader loader1 = new MedCoDataLoader("chuvclear" + nodeId, datasetId, "i2b2demotest", "Demo", "demo", "demouser");

        loader1.loadClinicalFileOntology(dir + "data_clinical_skcm_broad.txt", '\t', '\u0000', 5, typesClinical);
        System.out.println("Node " + nodeId + ": ontology clinical OK");
        //StopWatch.misc.stop();

        //StopWatch.misc.start("clear dataset loading: clinical data");
        loader1.loadClinicalFileData(dir + "data_clinical_skcm_broad.txt", '\t', '\u0000', 5, typesClinical);
        System.out.println("Node " + nodeId + ": clinical data OK");

        // for each entry of the dataset:
        // generate ontology entry, generate concept dim entry generate obs fact
        //StopWatch.misc.stop();

        //StopWatch.misc.start("clear dataset loading: variants data");
        MedCoDatabase dao = new MedCoDatabase();
        //CSVReader genomicReader = new CSVReader(new FileReader(dir + "data_mutations_extended_skcm_broad_clear_i2b2_part"+ nodeId + "_"+datasetSplit+".txt"), '\t', '\u0000', 0);
        CSVReader genomicReader = new CSVReader(new FileReader(dir + "data_mutations_extended_skcm_broad_clear_i2b2.txt"), '\t', '\u0000', 0);
        String[] genomicHeader = genomicReader.readNext();

        // [sample_id, patient_id, variant_id]
        Map<String, Integer> patientsMap = new HashMap<>(), samplesMap = new HashMap<>();
        String[] genomicEntry;
        int count = 0;
        while ((genomicEntry = genomicReader.readNext()) != null) {
            try {

                // ontology entry
                dao.batchSqlStatements.add(
                "insert into i2b2metadata.clinical_non_sensitive" +
                        "(c_hlevel, c_fullname, c_name, c_synonym_cd, c_visualattributes, c_basecode, c_facttablecolumn, c_tablename, c_columnname, c_columndatatype, c_operator, c_dimcode, update_date, valuetype_cd, m_applied_path) values(" +
                        "'4', '\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + genomicEntry[2] + "\\', '" + genomicEntry[2] + "', 'N', 'LA', '" + genomicEntry[2] + "', 'concept_cd', 'concept_dimension', 'concept_path', 'T', 'LIKE', " +
                        "'\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + genomicEntry[2] + "\\', now(), 'MEDCO_CLEAR', '@') ON CONFLICT DO NOTHING");
                // todo: remove me
                //dao.batchSqlStatements.add(
                //        "insert into i2b2metadata.clinical_non_sensitive" +
                //                "(c_hlevel, c_fullname, c_name, c_synonym_cd, c_visualattributes, c_basecode, c_facttablecolumn, c_tablename, c_columnname, c_columndatatype, c_operator, c_dimcode, update_date, valuetype_cd, m_applied_path) values(" +
                 //               "'4', '\\medco\\clinical\\nonsensitive\\VARIANT_ID\\S" + genomicEntry[2] + "\\', 'S" + genomicEntry[2] + "', 'N', 'LA', 'S" + genomicEntry[2] + "', 'concept_cd', 'concept_dimension', 'concept_path', 'T', 'LIKE', " +
                //                "'\\medco\\clinical\\nonsensitive\\VARIANT_ID\\S" + genomicEntry[2] + "\\', now(), 'MEDCO_CLEAR', '@') ON CONFLICT DO NOTHING");
                // obs fact entry
                int patient_id = -1, sample_id = -1;
                if (patientsMap.containsKey(genomicEntry[1])) {
                    patient_id = patientsMap.get(genomicEntry[1]);
                } else {
                    patient_id = dao.sqlSelectInt("select patient_num from i2b2demodata.patient_mapping where patient_ide=?", genomicEntry[1]);
                    patientsMap.put(genomicEntry[1], patient_id);
                }
                if (samplesMap.containsKey(genomicEntry[0])) {
                    sample_id = samplesMap.get(genomicEntry[0]);
                } else {
                    sample_id = dao.sqlSelectInt("select encounter_num from i2b2demodata.encounter_mapping where encounter_ide=?", genomicEntry[0]);
                    samplesMap.put(genomicEntry[0], sample_id);
                }

                int instanceNum=1;//count++%4;//change me for quadruple

                dao.batchSqlStatements.add("insert into i2b2demodata.observation_fact(encounter_num, patient_num, concept_cd, provider_id, start_date, instance_num) values(" +
                        "'" + sample_id + "', '" + patient_id + "', '" + genomicEntry[2] + "', 'chuvclear"+nodeId+datasetSplit+"', NOW(), '"+instanceNum+"')");
                //todo: remove me
                //instanceNum=2;
                //dao.batchSqlStatements.add("insert into i2b2demodata.observation_fact(encounter_num, patient_num, concept_cd, provider_id, start_date, instance_num) values(" +
                 //       "'" + sample_id + "', '" + patient_id + "', '" + genomicEntry[2] + "', 'chuvclear"+nodeId+datasetSplit+"', NOW(), '"+instanceNum+"')");
                System.out.println(patient_id + " -- " + sample_id);
            } catch (Throwable e) {
                e.printStackTrace();
                System.err.println("ignoring genomic entry ...");
            }
        }

        //dao.exec the batchie
        dao.sqlBatchUpdate();
        // concept dim entry: all at once
        dao.sqlUpdate("insert into i2b2demodata.concept_dimension(concept_path, concept_cd, name_char, import_date)" +
                "select c_fullname, c_basecode, c_name, update_date from i2b2metadata.clinical_non_sensitive where c_basecode is not null;");//c_fullname like '%VARIANT_ID%' and

        //StopWatch.misc.stop();



//        loader1.loadClinicalFileOntology(dir + "data_mutations_extended_skcm_broad_clear_i2b2_part1_normal.txt", '\t', '\u0000', 0, typesGenomicClear);
        //System.out.println("Node " + nodeId + ": ontology clinical 2 OK");



        //loader1.loadClinicalFileData(dir + "data_mutations_extended_skcm_broad_clear_i2b2_part1_normal.txt", '\t', '\u0000',0, typesGenomicClear);
        //System.out.println("Node " + nodeId + ": genomic data OK");

        //loader1.translateIdsToNums();
        //System.out.println("Node " + nodeId + ": translate OK");

    }

    public static void loadStandardDataset(String datasetSplit) throws UnlynxException, IOException, I2B2Exception {
        {

            loadSrv1Conf();

            String
                    dir = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/",
                    datasetId = "skcm_broad_part1_" + datasetSplit;

            nodeLoading("1", datasetId,
                    dir + "data_clinical_skcm_broad.txt",
                    dir + "data_clinical_skcm_broad_part1.txt",
                    dir + "data_mutations_extended_" + datasetId + ".txt");

        } {
            loadSrv3Conf();

            String
                    dir = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/",
                    datasetId = "skcm_broad_part2_" + datasetSplit;

            nodeLoading("2", datasetId,
                    dir + "data_clinical_skcm_broad.txt",
                    dir + "data_clinical_skcm_broad_part2.txt",
                    dir + "data_mutations_extended_" + datasetId + ".txt");

        } {
            loadSrv5Conf();

            String
                    dir = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/",
                    datasetId = "skcm_broad_part3_" + datasetSplit;

            nodeLoading("3", datasetId,
                    dir + "data_clinical_skcm_broad.txt",
                    dir + "data_clinical_skcm_broad_part3.txt",
                    dir + "data_mutations_extended_" + datasetId + ".txt");
        }
    }

    public static void loadProfileManyNode() throws UnlynxException, IOException, I2B2Exception {

            loadSrv1Conf();
            String datasetSplit = "normal";

            String
                    dir = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/",
                    datasetId = "skcm_broad_part1_" + datasetSplit;

            nodeLoading("1", datasetId,
                    dir + "data_clinical_skcm_broad.txt",
                    dir + "data_clinical_skcm_broad_part1.txt",
                    dir + "data_mutations_extended_" + datasetId + ".txt");

    }

    public static void nodeLoading(String nodeId, String datasetId, String clinicalPath_full, String clinicalPath_part, String genomicPath_part) throws I2B2Exception, IOException, UnlynxException {
        System.out.println("Starting loading node " + nodeId);
        MedCoDataLoader loader1 = new MedCoDataLoader("chuv" + nodeId, datasetId, "medcodeployment", "Demo", "demo", "demouser");

        loader1.loadClinicalFileOntology(clinicalPath_full, '\t', '\u0000', 5, typesClinical);
        System.out.println("Node " + nodeId + ": ontology OK");

        loader1.loadClinicalFileData(clinicalPath_part, '\t', '\u0000', 0, typesClinical);
        System.out.println("Node " + nodeId + ": clinical data OK");

        loader1.loadGenomicFile(genomicPath_part, '\t', '\u0000',0, typesGenomic);
        System.out.println("Node " + nodeId + ": genomic data OK");

        loader1.translateIdsToNums();
        System.out.println("Node " + nodeId + ": translate OK");
    }

    protected static void loadMedCoConf(String hostname, int i2b2Port, int psqlPort, String unlynxEntryPoint) {
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.ONTCELL_WS_URL_PROPERTIES,
                "http://" + hostname + ":" + i2b2Port + "/i2b2/services/OntologyService");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.FRCELL_WS_URL_PROPERTIES,
                "http://" + hostname + ":" + i2b2Port + "/i2b2/services/FRService");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.CRCCELL_WS_URL_PROPERTIES,
                "http://" + hostname + ":" + i2b2Port + "/i2b2/services/QueryToolService");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.I2B2CELLS_WS_WAITTIME_PROPERTIES,
                "180000");

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_BINARY_PATH_PROPERTIES, "unlynxI2b2"); // assumed in bin path
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_GROUP_FILE_PATH_PROPERTIES,
                "/home/misbach/repositories/medco-deployment/configuration/keys/dev-3nodes-samehost/group.toml");

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_DEBUG_LEVEL_PROPERTIES, "5");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_PROOFS_PROPERTIES, "0");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_ENTRY_POINT_IDX_PROPERTIES, unlynxEntryPoint);

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName(hostname);
        ds.setDatabaseName("medcodeployment");
        ds.setPortNumber(psqlPort);
        ds.setUser("postgres");
        ds.setPassword("prigen2017");
        //ds.setCurrentSchema("medco_data");

        MedCoUtil.getTestInstance().setDataSource(ds);
    }

    protected static void loadSrv1Conf() {
        loadMedCoConf("localhost", 8082,
                5434, "0");
    }

    protected static void loadSrv3Conf() {
        loadMedCoConf("iccluster062.iccluster.epfl.ch", 8080,
                5432, "1");
    }

    protected static void loadSrv5Conf() {
        loadMedCoConf("iccluster063.iccluster.epfl.ch", 8080,
                5432, "2");
    }


    private static DataType[] typesClinical = new DataType[]{
            DataType.SAMPLE_ID,
            DataType.PATIENT_ID,
            DataType.CLEAR,
            DataType.CLEAR,
            DataType.CLEAR,
            DataType.CLEAR,

            DataType.ENC,

            DataType.CLEAR,
            DataType.CLEAR,
            DataType.CLEAR,
            DataType.ENC

    }, typesGenomicClear = new DataType[]{
            DataType.SAMPLE_ID,
            DataType.PATIENT_ID,
            DataType.CLEAR

    },typesGenomic = new DataType[]{

            // Hugo_Symbol	Entrez_Gene_Id	Center	NCBI_Build	Chromosome	Start_Position	End_Position	Strand
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.CHROMOSOME,
            DataType.START_POS,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // Variant_Classification	Variant_Type	Reference_Allele	Tumor_Seq_Allele1	Tumor_Seq_Allele2	dbSNP_RS
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.REF_ALLELES,
            DataType.ALT_ALLELES,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // dbSNP_Val_Status	Tumor_Sample_Barcode	Matched_Norm_Sample_Barcode	Match_Norm_Seq_Allele1
            DataType.ANNOTATION,
            DataType.SAMPLE_ID,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // Match_Norm_Seq_Allele2	Tumor_Validation_Allele1	Tumor_Validation_Allele2	Match_Norm_Validation_Allele1
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // Match_Norm_Validation_Allele2	Verification_Status	Validation_Status	Mutation_Status	Sequencing_Phase
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // Sequence_Source	Validation_Method	Score	BAM_File	Sequencer	MA:FImpact	MA:FIS	MA:protein.change
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // MA:link.MSA	MA:link.PDB	MA:link.var	Tumor_Sample_UUID	Matched_Norm_Sample_UUID	HGVSc	HGVSp
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // HGVSp_Short	Transcript_ID	Exon_Number	t_depth	t_ref_count	t_alt_count	n_depth	n_ref_count	n_alt_count
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // all_effects	Allele	Gene	Feature	Feature_type	Consequence	cDNA_position	CDS_position
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // Protein_position	Amino_acids	Codons	Existing_variation	ALLELE_NUM	DISTANCE	SYMBOL	SYMBOL_SOURCE
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // HGNC_ID	BIOTYPE	CANONICAL	CCDS	ENSP	SWISSPROT	TREMBL	UNIPARC	RefSeq	SIFT	PolyPhen	EXON
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // INTRON	DOMAINS	GMAF	AFR_MAF	AMR_MAF	ASN_MAF	EAS_MAF	EUR_MAF	SAS_MAF	AA_MAF	EA_MAF	CLIN_SIG
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // SOMATIC	PUBMED	MOTIF_NAME	MOTIF_POS	HIGH_INF_POS	MOTIF_SCORE_CHANGE	IMPACT	PICK	VARIANT_CLASS
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // TSL	HGVS_OFFSET	PHENO	MINIMISED	ExAC_AF	ExAC_AF_AFR	ExAC_AF_AMR	ExAC_AF_EAS	ExAC_AF_FIN	ExAC_AF_NFE
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,

            // ExAC_AF_OTH	ExAC_AF_SAS	GENE_PHENO	FILTER
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION,
            DataType.ANNOTATION
    };
}
