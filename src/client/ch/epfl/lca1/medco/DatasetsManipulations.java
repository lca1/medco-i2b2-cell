package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.loader.EncryptedIdentifiersManager;
import ch.epfl.lca1.medco.unlynx.UnlynxEncrypt;
import ch.epfl.lca1.medco.util.StopWatch;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xpath.operations.Number;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by misbach on 20.07.17.
 */
public class DatasetsManipulations {

    public static void main(String[] args) throws IOException {
        //splitting();
        //generation();
        getInfoUseCases();
        //generateClearGenomicDataset();
        //timeEncrypt();
        //generateHistogramsForLeakage();
    }

    public static void generateHistogramsForLeakage() throws IOException {
        // generate histograms

        MedCoLoadingClient.loadSrv1Conf();
        String genomicFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad.txt",
                clinicalFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad.txt",
                clearGenomicOutputPath1 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/histogram_nb_patients.txt",
                clearGenomicOutputPath2 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/histogram_nb_facts.txt";

        // maps sample id -> patient id
        Map<String, String> samplePatientMap = new HashMap<>();

        // read clinical: store patient id - sample ids mapping
        {
            CSVReader clinicalReader = new CSVReader(new FileReader(clinicalFilePath), '\t', '\u0000', 5);
            String[] clinicalHeader = clinicalReader.readNext();

            // headers idx clinical
            int sampleIdId = -1, patientIdId = -1;
            for (int i = 0; i < clinicalHeader.length; i++) {
                if (clinicalHeader[i].trim().equals("SAMPLE_ID")) {
                    sampleIdId = i;
                } else if (clinicalHeader[i].trim().equals("PATIENT_ID")) {
                    patientIdId = i;
                }
            }

            // dataset store mapping
            String[] clinicalEntry;
            while ((clinicalEntry = clinicalReader.readNext()) != null) {
                try {
                    samplePatientMap.put(clinicalEntry[sampleIdId], clinicalEntry[patientIdId]);
                } catch (Throwable e) {
                    System.err.println("ignoring clinical entry ...");
                }
            }
        }

        // map variant id -> patients set
        Map<Long, Set<String>> nbPatientsMap = new HashMap<>();
        // map patient -> nb facts
        Map<String, Integer> nbFacts = new HashMap<>();
        {

            CSVReader genomicReader = new CSVReader(new FileReader(genomicFilePath), '\t', '\u0000', 1);
            String[] genomicHeader = genomicReader.readNext();


            // headers idx genomic
            int sampleIdId = -1, chromId = -1, startPosId = -1, altAlleleId = -1, refAlleleId = -1;
            for (int i = 0; i < genomicHeader.length; i++) {
                if (genomicHeader[i].trim().equals("Tumor_Sample_Barcode")) {
                    sampleIdId = i;
                } else if (genomicHeader[i].trim().equals("Chromosome")) {
                    chromId = i;
                } else if (genomicHeader[i].trim().equals("Start_Position")) {
                    startPosId = i;
                } else if (genomicHeader[i].trim().equals("Reference_Allele")) {
                    refAlleleId = i;
                } else if (genomicHeader[i].trim().equals("Tumor_Seq_Allele1")) {
                    altAlleleId = i;
                }
            }

            // dataset genomic reading + generating [sample_id, patient_id, variant_id]
            String[] genomicEntry;
            while ((genomicEntry = genomicReader.readNext()) != null) {
                try {
                    // extract from entry
                    long variantId = EncryptedIdentifiersManager.getVariantId(genomicEntry[chromId].trim(),
                            Long.parseLong(genomicEntry[startPosId].trim()), genomicEntry[refAlleleId].trim(), genomicEntry[altAlleleId].trim());

                    String patientId = samplePatientMap.get(genomicEntry[sampleIdId]);

                    // add patients to the set corresponding to variant
                    if (!nbPatientsMap.containsKey(variantId)) {
                        nbPatientsMap.put(variantId, new HashSet<>());
                    }
                    nbPatientsMap.get(variantId).add(patientId);

                    // update nb facts / patient
                    if (!nbFacts.containsKey(patientId)) {
                        nbFacts.put(patientId, 0);
                    }
                    nbFacts.replace(patientId, nbFacts.get(patientId) + 1);

                } catch (Throwable e) {
                    System.err.println("ignoring genomic entry ...");
                }
            }
        }

        CSVWriter genomicWriter1 = new CSVWriter(new FileWriter(clearGenomicOutputPath1), '\t', '\u0000');
        genomicWriter1.writeNext(new String[]{"VARIANT_ID", "NB_PATIENTS"});
        for (Map.Entry<Long, Set<String>> entry : nbPatientsMap.entrySet()) {
            genomicWriter1.writeNext(new String[]{String.valueOf(entry.getKey()), String.valueOf(entry.getValue().size())});
        }
        CSVWriter genomicWriter2 = new CSVWriter(new FileWriter(clearGenomicOutputPath2), '\t', '\u0000');
        genomicWriter2.writeNext(new String[]{"PATIENT_ID", "NB_GENOMIC_FACTS"});
        for (Map.Entry<String, Integer> entry : nbFacts.entrySet()) {
            genomicWriter2.writeNext(new String[]{String.valueOf(entry.getKey()), String.valueOf(entry.getValue())});
        }
        genomicWriter1.close();
        genomicWriter2.close();
    }

    public static void timeEncrypt() throws IOException {
        Logger.getRootLogger().setLevel(Level.OFF);
        MedCoLoadingClient.loadSrv1Conf();
        UnlynxEncrypt encrypt = new UnlynxEncrypt();

        StopWatch.overall.start();
        for (int i = 0 ; i < 1000000 ; i++) {
            String hey = encrypt.encryptInt(i);

            if (i % 250000 == 0) {
                long time = StopWatch.overall.getTotalTimeMillis();
                System.out.println(time);
            }

            if (i % 10000 == 0) {
                System.out.println(i);
            }
        }
    }

    public static void generateClearGenomicDataset() throws IOException {
        // generate full dataset for clear i2b2 loading, then split using the existing things

        MedCoLoadingClient.loadSrv1Conf();
        String genomicFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad.txt",
                clinicalFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad.txt",
                clearGenomicOutputPath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2.txt";

        // maps sample id -> patient id
        Map<String, String> samplePatientMap = new HashMap<>();

        // read clinical: store patient id - sample ids mapping
        {
            CSVReader clinicalReader = new CSVReader(new FileReader(clinicalFilePath), '\t', '\u0000', 5);
            String[] clinicalHeader = clinicalReader.readNext();

            // headers idx clinical
            int sampleIdId = -1, patientIdId = -1;
            for (int i = 0; i < clinicalHeader.length; i++) {
                if (clinicalHeader[i].trim().equals("SAMPLE_ID")) {
                    sampleIdId = i;
                } else if (clinicalHeader[i].trim().equals("PATIENT_ID")) {
                    patientIdId = i;
                }
            }

            // dataset store mapping
            String[] clinicalEntry;
            while ((clinicalEntry = clinicalReader.readNext()) != null) {
                try {
                    samplePatientMap.put(clinicalEntry[sampleIdId], clinicalEntry[patientIdId]);
                } catch (Throwable e) {
                    System.err.println("ignoring clinical entry ...");
                }
            }
        }

        // read genomic and output dataset we want
        {

            CSVReader genomicReader = new CSVReader(new FileReader(genomicFilePath), '\t', '\u0000', 1);
            String[] genomicHeader = genomicReader.readNext();

            CSVWriter genomicWriter = new CSVWriter(new FileWriter(clearGenomicOutputPath), '\t', '\u0000');
            genomicWriter.writeNext(new String[]{"SAMPLE_ID", "PATIENT_ID", "VARIANT_ID"});

            // headers idx genomic
            int sampleIdId = -1, chromId = -1, startPosId = -1, altAlleleId = -1, refAlleleId = -1;
            for (int i = 0; i < genomicHeader.length; i++) {
                if (genomicHeader[i].trim().equals("Tumor_Sample_Barcode")) {
                    sampleIdId = i;
                } else if (genomicHeader[i].trim().equals("Chromosome")) {
                    chromId = i;
                } else if (genomicHeader[i].trim().equals("Start_Position")) {
                    startPosId = i;
                } else if (genomicHeader[i].trim().equals("Reference_Allele")) {
                    refAlleleId = i;
                } else if (genomicHeader[i].trim().equals("Tumor_Seq_Allele1")) {
                    altAlleleId = i;
                }
            }

            // dataset genomic reading + generating [sample_id, patient_id, variant_id]
            String[] genomicEntry;
            while ((genomicEntry = genomicReader.readNext()) != null) {
                try {
                    long variantId = EncryptedIdentifiersManager.getVariantId(genomicEntry[chromId].trim(),
                            Long.parseLong(genomicEntry[startPosId].trim()), genomicEntry[refAlleleId].trim(), genomicEntry[altAlleleId].trim());

                    genomicWriter.writeNext(new String[]{
                            genomicEntry[sampleIdId],
                            samplePatientMap.get(genomicEntry[sampleIdId]),
                            variantId + ""});
                } catch (Throwable e) {
                    System.err.println("ignoring genomic entry ...");
                }
            }
        }
    }

    public static void getInfoUseCases() throws IOException {
        // cancer type detailed = cutaneous melanoma
        // primary tumor localization = skin
        // annotation protein position = 600/766
        // hugo symbol = braf

        MedCoLoadingClient.loadSrv1Conf();
        Logger.getRootLogger().setLevel(Level.OFF);

        String genomicFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad.txt";

        Set<Long>
            clearQueryVariantsUseCase1 = new HashSet<>(),
            clearQueryVariantsUseCase2Braf = new HashSet<>(),
            clearQueryVariantsUseCase2Others = new HashSet<>();


        {
            CSVReader genomicReader = new CSVReader(new FileReader(genomicFilePath), '\t', '\u0000', 1);
            String[] genomicHeader = genomicReader.readNext();

            // headers idx genomic
            int proteinPosId = -1, hugoSymbolId = -1, sampleIdId = -1,
                chromId = -1, startPosId = -1, altAlleleId = -1, refAlleleId = -1;
            for (int i = 0; i < genomicHeader.length; i++) {
                if (genomicHeader[i].trim().equals("Hugo_Symbol")) {
                    hugoSymbolId = i;
                } else if (genomicHeader[i].trim().equals("Protein_position")) {
                    proteinPosId = i;
                } else if (genomicHeader[i].trim().equals("Tumor_Sample_Barcode")) {
                    sampleIdId = i;
                } else if (genomicHeader[i].trim().equals("Chromosome")) {
                    chromId = i;
                } else if (genomicHeader[i].trim().equals("Start_Position")) {
                    startPosId = i;
                } else if (genomicHeader[i].trim().equals("Reference_Allele")) {
                    refAlleleId = i;
                } else if (genomicHeader[i].trim().equals("Tumor_Seq_Allele1")) {
                    altAlleleId = i;
                }
            }

            // read dataset
            String[] genomicEntry;

            while ((genomicEntry = genomicReader.readNext()) != null) {
                try {

                    // use case 1
                    if (genomicEntry[proteinPosId].trim().equals("600/766") &&
                            genomicEntry[hugoSymbolId].trim().equals("BRAF")) {

                        long id = EncryptedIdentifiersManager.getVariantId(genomicEntry[chromId].trim(),
                                Long.parseLong(genomicEntry[startPosId].trim()), genomicEntry[refAlleleId].trim(), genomicEntry[altAlleleId].trim());

                        clearQueryVariantsUseCase1.add(id);

                    }

                    // use case 2 - braf
                    if (genomicEntry[hugoSymbolId].trim().equals("BRAF")) {
                        long id = EncryptedIdentifiersManager.getVariantId(genomicEntry[chromId].trim(),
                                Long.parseLong(genomicEntry[startPosId].trim()), genomicEntry[refAlleleId].trim(), genomicEntry[altAlleleId].trim());

                        clearQueryVariantsUseCase2Braf.add(id);
                    }

                    // use case 2 - other mutations
                    if (genomicEntry[hugoSymbolId].trim().equals("PTEN") ||
                            genomicEntry[hugoSymbolId].trim().equals("CDKN2A") ||
                            genomicEntry[hugoSymbolId].trim().equals("MAP2K2") ||
                            genomicEntry[hugoSymbolId].trim().equals("MAP2K1")) {

                        long id = EncryptedIdentifiersManager.getVariantId(genomicEntry[chromId].trim(),
                                Long.parseLong(genomicEntry[startPosId].trim()), genomicEntry[refAlleleId].trim(), genomicEntry[altAlleleId].trim());

                        clearQueryVariantsUseCase2Others.add(id);
                    }

                } catch (Throwable e) {
                    System.err.println("ignoring genomic variant ...");
                }
            }

            System.err.println("nbs: " + clearQueryVariantsUseCase1.size() + " / " + clearQueryVariantsUseCase2Braf.size() + " / " + clearQueryVariantsUseCase2Others.size());

        }

        UnlynxEncrypt encrypt = new UnlynxEncrypt();

        // gen queries encrypted
        System.out.println("--- use case 1 ---\n");
        System.out.print("MEDCO_ENC:" + encrypt.encryptInt(4) + " AND "); // val 4
        System.out.println("MEDCO_ENC:" + encrypt.encryptInt(6) + " AND "); // val 6
        for (Long encId : clearQueryVariantsUseCase1) {
            System.out.println("MEDCO_GEN:" + encrypt.encryptInt(encId) + " OR ");
        }

        System.out.println("\n\n\n--- use case 2 ---\n");
        System.out.print("MEDCO_ENC:" + encrypt.encryptInt(4) + " AND "); // val 4
        System.out.println("MEDCO_ENC:" + encrypt.encryptInt(6) + " AND "); // val 6
        for (Long encId : clearQueryVariantsUseCase2Braf) {
            System.out.println("MEDCO_GEN:" + encrypt.encryptInt(encId) + " OR ");
        }
        System.out.println(" AND ");
        for (Long encId : clearQueryVariantsUseCase2Others) {
            System.out.println("MEDCO_GEN:" + encrypt.encryptInt(encId) + " OR ");
        }

        // gen queries clear
        System.out.println("\n\n\n--- use case 1 clear ---\n");
        for (Long id : clearQueryVariantsUseCase1) {
            System.out.println("\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + id + "\\ OR ");
        }

        System.out.println("\n\n\n--- use case 2 clear ---\n");
        for (Long id : clearQueryVariantsUseCase2Braf) {
            System.out.println("\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + id + "\\ OR ");
        }
        System.out.println(" AND ");
        for (Long id : clearQueryVariantsUseCase2Others) {
            System.out.println("\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\VARIANT_ID\\" + id + "\\ OR ");
        }
    }

    private static void generateLessData(int factor, String genomicInput, String genomicOuput) throws IOException {

        CSVWriter genomicWriter = new CSVWriter(new FileWriter(genomicOuput), '\t', '\u0000');

        CSVReader genomicReader = new CSVReader(new FileReader(genomicInput), '\t', '\u0000', 0);
        String[] genomicHeader = genomicReader.readNext();
        genomicWriter.writeNext(genomicHeader);

        String[] genomicEntry;
        int entriesCount = 0, writtenEntriesCount = 0;
        while ( (genomicEntry = genomicReader.readNext()) != null) {

            if (entriesCount % factor == 0) {
                genomicWriter.writeNext(genomicEntry);
                writtenEntriesCount++;
            }

            entriesCount++;
        }

        genomicWriter.close();
        System.out.println("Less data factor: " + factor + ", parsed " + entriesCount + " entries, written " + writtenEntriesCount);
    }

    private static void generateMoreData(int factor, String genomicInput, String genomicOuput) throws IOException {

        CSVWriter genomicWriter = new CSVWriter(new FileWriter(genomicOuput), '\t', '\u0000');

        CSVReader genomicReader = new CSVReader(new FileReader(genomicInput), '\t', '\u0000', 0);
        String[] genomicHeader = genomicReader.readNext();
        genomicWriter.writeNext(genomicHeader);

        String[] genomicEntry;
        int entriesCount = 0, writtenEntriesCount = 0;
        while ( (genomicEntry = genomicReader.readNext()) != null) {

            for (int i = 0 ; i < factor ; i++) {
                genomicWriter.writeNext(genomicEntry);
                writtenEntriesCount++;
            }

            entriesCount++;
        }

        genomicWriter.close();
        System.out.println("More data factor: " + factor + ", parsed " + entriesCount + " entries, written " + writtenEntriesCount);
    }

    /*
     from each part file: generate
     - clinical_half_patients: remove half of the patients, assign rest of of data to others remaining
     - clinical_double_patients: duplicate all patients
     - genomic_half_patients,
     - genomic_double_patients

     data generation:
     when less --> retain only 1 out of 2 or 1 out of 4
     when more --> all records duplicated / quadrupled

     --> clinical remains the same, only genomic touched (because nb clinical << nb genomic)
      */
    public static void generation() throws IOException {
        String  genomicPart1 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2_part1",
                genomicPart2 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2_part2",
                genomicPart3 = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2_part3";

        // half generation
        generateLessData(2, genomicPart1 + ".txt", genomicPart1 + "_half.txt");
        generateLessData(2, genomicPart2 + ".txt", genomicPart2 + "_half.txt");
        generateLessData(2, genomicPart3 + ".txt", genomicPart3 + "_half.txt");

        // quarter generation
        generateLessData(4, genomicPart1 + ".txt", genomicPart1 + "_quarter.txt");
        generateLessData(4, genomicPart2 + ".txt", genomicPart2 + "_quarter.txt");
        generateLessData(4, genomicPart3 + ".txt", genomicPart3 + "_quarter.txt");

        // double generation
        generateMoreData(2, genomicPart1 + ".txt", genomicPart1 + "_double.txt");
        generateMoreData(2, genomicPart2 + ".txt", genomicPart2 + "_double.txt");
        generateMoreData(2, genomicPart3 + ".txt", genomicPart3 + "_double.txt");

        // quadruple generation
        generateMoreData(4, genomicPart1 + ".txt", genomicPart1 + "_quadruple.txt");
        generateMoreData(4, genomicPart2 + ".txt", genomicPart2 + "_quadruple.txt");
        generateMoreData(4, genomicPart3 + ".txt", genomicPart3 + "_quadruple.txt");

    }

    public static void splitting() throws IOException {
        String clinicalFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad.txt",
                genomicFilePath = "/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2.txt";

        CSVWriter[] clinicalWriters = new CSVWriter[]{
                new CSVWriter(new FileWriter("/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad_clear_i2b2_part1.txt"),
                        '\t', '\u0000'),
                new CSVWriter(new FileWriter("/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad_clear_i2b2_part2.txt"),
                        '\t', '\u0000'),
                new CSVWriter(new FileWriter("/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_clinical_skcm_broad_clear_i2b2_part3.txt"),
                        '\t', '\u0000'),
        };
        CSVWriter[] genomicWriters = new CSVWriter[]{
                new CSVWriter(new FileWriter("/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2_part1.txt"),
                        '\t', '\u0000'),
                new CSVWriter(new FileWriter("/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2_part2.txt"),
                        '\t', '\u0000'),
                new CSVWriter(new FileWriter("/home/misbach/repositories/i2b2-core-server-medco/ch.epfl.lca1.medco/testfiles/datasets/full/skcm_broad/data_mutations_extended_skcm_broad_clear_i2b2_part3.txt"),
                        '\t', '\u0000'),
        };

        CSVReader clinicalReader = new CSVReader(new FileReader(clinicalFilePath), '\t', '\u0000', 5);
        String[] clinicalHeader = clinicalReader.readNext();
        clinicalWriters[0].writeNext(clinicalHeader);
        clinicalWriters[1].writeNext(clinicalHeader);
        clinicalWriters[2].writeNext(clinicalHeader);

        CSVReader genomicReader = new CSVReader(new FileReader(genomicFilePath), '\t', '\u0000', 0);
        String[] genomicHeader = genomicReader.readNext();
        genomicWriters[0].writeNext(genomicHeader);
        genomicWriters[1].writeNext(genomicHeader);
        genomicWriters[2].writeNext(genomicHeader);

        Set<String>[] sampleIdsSets = new HashSet[] {
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>()
        };

        // read clinical file
        String[] clinicalEntry;
        int clinicalEntriesCount = 0;
        while ( (clinicalEntry = clinicalReader.readNext()) != null) {
            clinicalEntriesCount++;

            if (clinicalEntriesCount <= 40) {
                clinicalWriters[0].writeNext(clinicalEntry);
                sampleIdsSets[0].add(clinicalEntry[0].trim());
            } else if (clinicalEntriesCount > 40 && clinicalEntriesCount <= 80) {
                clinicalWriters[1].writeNext(clinicalEntry);
                sampleIdsSets[1].add(clinicalEntry[0].trim());
            } else {
                clinicalWriters[2].writeNext(clinicalEntry);
                sampleIdsSets[2].add(clinicalEntry[0].trim());
            }
        }

        System.out.println("Parsed " + clinicalEntriesCount + " entries, sizes: " + sampleIdsSets[0].size() + " - " +
                sampleIdsSets[1].size() + " - " + sampleIdsSets[2].size());

        // read genomic file
        int sampleIdIdx = 0; //15 for skcm broad originial, 2 for generated clear i2b2 dataset
        String[] genomicEntry;
        int genomicCount = 0;
        while ( (genomicEntry = genomicReader.readNext()) != null) {
            genomicCount++;

            if (sampleIdsSets[0].contains(genomicEntry[sampleIdIdx].trim())) {
                genomicWriters[0].writeNext(genomicEntry);
            } else if (sampleIdsSets[1].contains(genomicEntry[sampleIdIdx].trim())) {
                genomicWriters[1].writeNext(genomicEntry);
            } else if (sampleIdsSets[2].contains(genomicEntry[sampleIdIdx].trim())){
                genomicWriters[2].writeNext(genomicEntry);
            } else {
                System.out.println("Non existing sample in clinical: " + genomicEntry[sampleIdIdx]);
            }
        }

        System.out.println("Parsed " + genomicCount + " entries");

        clinicalWriters[0].close();
        clinicalWriters[1].close();
        clinicalWriters[2].close();
        genomicWriters[0].close();
        genomicWriters[1].close();
        genomicWriters[2].close();

    }
}
