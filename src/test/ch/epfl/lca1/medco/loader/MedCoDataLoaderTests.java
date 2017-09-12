package ch.epfl.lca1.medco.loader;

import ch.epfl.lca1.medco.ConfiguredTests;
import ch.epfl.lca1.medco.MedCoDataLoader;
import ch.epfl.lca1.medco.i2b2.ont.I2B2ONTCell;
import ch.epfl.lca1.medco.i2b2.pm.MedCoI2b2MessageHeader;
import ch.epfl.lca1.medco.loader.clinical.OntologyLoader;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by misbach on 16.06.17.
 */
public class MedCoDataLoaderTests extends ConfiguredTests {

    @Test
    public void testClinicalOntLoader() throws I2B2Exception, UnlynxException {
        String[] headers = new String[] {
                "sample_id", "patient_id", "test_enc_1", "test enc 2", "some clear1", "clear2", "ency3"
        };
        DataType[] types = new DataType[] {
                DataType.SAMPLE_ID,
                DataType.PATIENT_ID,
                DataType.ENC,
                DataType.ENC,
                DataType.CLEAR,
                DataType.CLEAR,
                DataType.ENC
        };
        MedCoI2b2MessageHeader auth = new MedCoI2b2MessageHeader("i2b2demotest", "Demo", "demo", false, 0, "demouser");

        OntologyLoader loader = new OntologyLoader(headers, types, "chuv");
        loader.parseEntry(new String[]{"22", "333F",      "A", "B", "A", "33", "5630"});
        loader.parseEntry(new String[]{"22", "33F",       "B", "B", "B", "33", "511630"});
        loader.parseEntry(new String[]{"223", "33F",      "A", "C", "A", "353", "5630"});
        loader.parseEntry(new String[]{"22F", "33f3F",    "B", "B", "C", "333", "5630"});
        loader.parseEntry(new String[]{"22F", "3f33F",    "C", "L", "A", "33", "563220"});

        loader.loadOntology(new I2B2ONTCell(auth));

    }

    @Test
    public void testClinicalLoader() throws UnlynxException, IOException, I2B2Exception {
        String  clinicalPath_full = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad.txt",
                clinicalPath_part1 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad_part1.txt",
                clinicalPath_part2 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad_part2.txt",
                clinicalPath_part3 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad_part3.txt";

        // --- mapping for the test dataset
        //SAMPLE_ID	PATIENT_ID	AGE	DISTANT_METASTASIS_PATHOLOGIC_SPREAD	GENDER
        // HISTOLOGICAL_SUBTYPE	ICD_O_3_HISTOLOGY	ICD_O_3_SITE	PRETREATMENT_HISTORY
        // PRIMARY_TUMOR_PATHOLOGIC_SPREAD	PRIOR_DIAGNOSIS	RESIDUAL_TUMOR
        // TOBACCO_SMOKING_HISTORY_INDICATOR	TUMOR_STAGE_2009	OS_STATUS	OS_MONTHS
        // DFS_STATUS	DFS_MONTHS	CANCER_TYPE	CANCER_TYPE_DETAILED
        /*
        DataType[] typesClinical = new DataType[]{
                DataType.SAMPLE_ID,
                DataType.PATIENT_ID,
                DataType.CLEAR,
                DataType.ENC,
                DataType.CLEAR,

                DataType.ENC,
                DataType.ENC,
                DataType.ENC,
                DataType.CLEAR,

                DataType.CLEAR,
                DataType.CLEAR,
                DataType.ENC,

                DataType.IGNORE,
                DataType.ENC,
                DataType.CLEAR,
                DataType.CLEAR,

                DataType.ENC,
                DataType.ENC,
                DataType.CLEAR,
                DataType.IGNORE
        };*/

        // --- mapping for the skcm_broad dataset
        // SAMPLE_ID	PATIENT_ID	GENDER(C)	AGE(C)	AGE_AT_PROCUREMENT(C)	PRIMARY_DIAGNOSIS(C)
        // [PRIMARY_TUMOR_LOCALIZATION_TYPE](S)
        // PRIMARY_SITE(C)	BRESLOW_DEPTH(C)	CANCER_TYPE(C)	[CANCER_TYPE_DETAILED](S)

        DataType[] typesClinical = new DataType[]{
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
        };

        {
            loadSrv1Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv1", "skcm_broad_part1", "i2b2demotest", "Demo", "demo", "demouser");
            loader.loadClinicalFileOntology(clinicalPath_full, '\t', '\u0000', 5, typesClinical);
            loader.loadClinicalFileData(clinicalPath_part1, '\t', '\u0000', 0, typesClinical);
        } {
            loadSrv3Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv2", "skcm_broad_part2", "i2b2demotest", "Demo", "demo", "demouser");
            loader.loadClinicalFileOntology(clinicalPath_full, '\t', '\u0000', 5, typesClinical);
            loader.loadClinicalFileData(clinicalPath_part2, '\t', '\u0000', 0, typesClinical);
        } {
            loadSrv5Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv3", "skcm_broad_part3", "i2b2demotest", "Demo", "demo", "demouser");
            loader.loadClinicalFileOntology(clinicalPath_full, '\t', '\u0000', 5, typesClinical);
            loader.loadClinicalFileData(clinicalPath_part3, '\t', '\u0000', 0, typesClinical);
        }

        /*
       <ns2:load_data_response>
            <upload_id>1</upload_id>
            <user_id>demo</user_id>
            <data_file_location_uri>/opt/FRC_files/Demo/pdoUploadchuv.xml</data_file_location_uri>
            <load_status>COMPLETED</load_status>
            <start_date>2017-07-20T16:54:25.121Z</start_date>
            <end_date>2017-07-20T16:54:26.761Z</end_date>
            <message />
            <observation_set inserted_record="847" ignored_record="0" total_record="847" />
            <patient_set inserted_record="121" ignored_record="0" total_record="121" />
            <event_set inserted_record="118" ignored_record="3" total_record="121" />
            <observer_set inserted_record="1" ignored_record="0" total_record="1" />
            <concept_set inserted_record="199" ignored_record="0" total_record="199" />
            <pid_set inserted_record="242" ignored_record="-121" total_record="121" />
            <eventid_set inserted_record="242" ignored_record="-121" total_record="121" />
        </ns2:load_data_response>
         */
    }

    @Test
    public void testGenomicLoader() throws UnlynxException, IOException, I2B2Exception {
        String genomicPath_part1 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_part1.txt",
                genomicPath_part2 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_part2.txt",
                genomicPath_part3 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_part3.txt";

        DataType[] typesGenomic = new DataType[]{

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

        // dataset test
        /*
        DataType[] typesGenomic = new DataType[] {

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

                // 	dbSNP_Val_Status	Tumor_Sample_Barcode	Matched_Norm_Sample_Barcode	Match_Norm_Seq_Allele1	Match_Norm_Seq_Allele2
                DataType.ANNOTATION,
                DataType.IGNORE,
                DataType.IGNORE,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,

                // Tumor_Validation_Allele1	Tumor_Validation_Allele2	Match_Norm_Validation_Allele1	Match_Norm_Validation_Allele2
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,

                // Verification_Status	Validation_Status	Mutation_Status	Sequencing_Phase	Sequence_Source	Validation_Method
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,

                // Score	BAM_File	Sequencer	MA:FImpact	MA:FIS	MA:protein.change	MA:link.MSA	MA:link.PDB	MA:link.var
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.SAMPLE_DATA,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,

                // Tumor_Sample_UUID	Matched_Norm_Sample_UUID	HGVSc	HGVSp	HGVSp_Short	Transcript_ID	Exon_Number
                DataType.IGNORE,
                DataType.IGNORE,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,

                // t_depth	t_ref_count	t_alt_count	n_depth	n_ref_count	n_alt_count	all_effects	Allele	Gene	Feature	Feature_type
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.SAMPLE_DATA,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,

                // Consequence	cDNA_position	CDS_position	Protein_position	Amino_acids	Codons	Existing_variation	ALLELE_NUM
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,

                // DISTANCE	SYMBOL	SYMBOL_SOURCE	HGNC_ID	BIOTYPE	CANONICAL	CCDS	ENSP	SWISSPROT	TREMBL	UNIPARC	RefSeq	SIFT
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
                DataType.ANNOTATION,

                // PolyPhen	EXON	INTRON	DOMAINS	GMAF	AFR_MAF	AMR_MAF	ASN_MAF	EAS_MAF	EUR_MAF	SAS_MAF	AA_MAF	EA_MAF	CLIN_SIG
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

                // TSL	HGVS_OFFSET	PHENO	MINIMISED	ExAC_AF	ExAC_AF_AFR	ExAC_AF_AMR	ExAC_AF_EAS	ExAC_AF_FIN	ExAC_AF_NFE	ExAC_AF_OTH
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

                // ExAC_AF_SAS	GENE_PHENO	FILTER  SAMPLE_ID
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.ANNOTATION,
                DataType.SAMPLE_ID
        };
        */

        {
            loadSrv1Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv1", "skcm_broad_part1","i2b2demotest", "Demo", "demo", "demouser");
            loader.loadGenomicFile(genomicPath_part1, '\t', '\u0000',0, typesGenomic);
        } {
            loadSrv3Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv2", "skcm_broad_part2","i2b2demotest", "Demo", "demo", "demouser");
            loader.loadGenomicFile(genomicPath_part2, '\t', '\u0000',0, typesGenomic);
        } {
            loadSrv5Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv3", "skcm_broad_part3","i2b2demotest", "Demo", "demo", "demouser");
            loader.loadGenomicFile(genomicPath_part3, '\t', '\u0000',0, typesGenomic);
        }

    }

    @Test
    public void testTranslateIdsToNums() throws UnlynxException, IOException, I2B2Exception {
        {
            loadSrv1Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv1", "skcm_broad_part1","i2b2demotest", "Demo", "demo", "demouser");
            loader.translateIdsToNums();
        } {
            loadSrv3Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv2", "skcm_broad_part2","i2b2demotest", "Demo", "demo", "demouser");
            loader.translateIdsToNums();
        } {
            loadSrv5Conf();
            MedCoDataLoader loader = new MedCoDataLoader("chuv3", "skcm_broad_part3","i2b2demotest", "Demo", "demo", "demouser");
            loader.translateIdsToNums();
        }


    }
}
