package ch.epfl.lca1.medco.loader;

/**
 * Data types that can be encountered in the datasets.
 */
public enum DataType {

    // --- common types ---
    SAMPLE_ID, // only 1 allowed
    IGNORE,

    // --- clinical types ---
    PATIENT_ID, // only 1 allowed
    CLEAR,
    ENC,

    // --- genomic types ---
    ANNOTATION,
    SAMPLE_DATA,

    // variant id: only 1 allowed
    CHROMOSOME,
    START_POS,
    REF_ALLELES,
    ALT_ALLELES;

    /**
     * Checks for types that can be present exactly once and conflicts.
     *
     * @param types the data types array to check for correctness
     * @return true is type array is valid
     */
    public static boolean typesArrayIsValid(DataType[] types) {

        // identify clinical case
        if (types[0] == SAMPLE_ID && types[1] == PATIENT_ID) {

            // no genomic types allowed, nor other sample / patient
            for (int i = 2 ; i < types.length ; i++) {
                switch (types[i]) {

                    // only types allowed
                    case IGNORE:
                    case CLEAR:
                    case ENC:
                        break;

                    default:
                        return false;
                }
            }

            return true;


        // identify genomic case
        } else {

            boolean sampleId = false, chromosome = false, startPos = false, refAll = false, altAll = false;

            for (DataType type : types) {
                switch (type) {

                    // types disallowed
                    case CLEAR:
                    case ENC:
                        return false;

                    // types allowed as much as needed
                    case IGNORE:
                    case ANNOTATION:
                    case SAMPLE_DATA:
                        break;

                    // types allowed once
                    case SAMPLE_ID:
                        if (sampleId) {
                            return false;
                        } else {
                            sampleId = true;
                        }
                        break;
                    case CHROMOSOME:
                        if (chromosome) {
                            return false;
                        } else {
                            chromosome = true;
                        }
                        break;
                    case START_POS:
                        if (startPos) {
                            return false;
                        } else {
                            startPos = true;
                        }
                        break;
                    case REF_ALLELES:
                        if (refAll) {
                            return false;
                        } else {
                            refAll = true;
                        }
                        break;
                    case ALT_ALLELES:
                        if (altAll) {
                            return false;
                        } else {
                            altAll = true;
                        }
                        break;

                    // should not happen
                    default:
                        return false;
                }
            }

            return true;
        }
    }
}
