package ch.epfl.lca1.medco.loader;

import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.exceptions.UnlynxException;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines and manipulates the identifiers that are meant to be encrypted by Unlynx for the purpose of answering queries.
 *
 * Convention: 64 bits integers.
 *
 * Genomic variant:
 * 1 bit (2): flag genomic variant (1)
 * 5 bits (32): chromosome id
 * 28 bits (268'435'456): start position of the mutation (1-based coordinate system)
 * 3 bits (8): length in # bases of the reference allele
 * 12 bits (4'096): reference allele (6 bases)
 * 3 bits (8): length in # bases of the alternative allele (mutated)
 * 12 bits (4'096): alternative allele (6 bases)
 *
 * Clinical:
 * 1 bit (2): flag clinical (0)
 * 32 bits (4'294'967'296): clinical id (ontology), String.hashCode() of the field name
 * 31 bits (2'147'483'648): clinical value
 */
public class EncryptedIdentifiersManager {

    private long nextUsableClinicalId;

    public EncryptedIdentifiersManager(long nextUsableClinicalId) {
        this.nextUsableClinicalId = nextUsableClinicalId;
    }

    /**
     * Size in bits of the identifier.
     */
    public static final int ID_BIT_SIZE = 64;

    /**
     * Breakdown of the size in bits.
     */
    public static final int TYPE_FLAG_BIT_SIZE = 1,
                            CLINICAL_ID_BIT_SIZE = 32,
                            CLINICAL_VAL_BIT_SIZE = 31,
                            CHR_BIT_SIZE = 5,
                            POS_BIT_SIZE = 28,
                            ALLELES_BASE_LENGTH_BIT_SIZE = 3,
                            ALLELES_BIT_SIZE = 12;
    /**
     * Valid values for the chromosome id:
     * Number from 1 to 23 inclusive, or
     * X, Y, or M
     *
     * -> Range from 1 to 26 inclusive, 2^5 = 32 ==> 5 bits storage
     */
    public static final String CHROMOSOME_ID_REGEX = "^[XYM]|[1-9]|(1[0-9])|(2[0-3])$";

    /**
     * Mapping to encode non-numeric chromosome ids.
     */
    public static final long    CHROMOSOME_X_INT_ID = 24,
                                CHROMOSOME_Y_INT_ID = 25,
                                CHROMOSOME_M_INT_ID = 26;

    /**
     * Mapping to encode type of id.
     */
    public static final long    TYPE_FLAG_CLINICAL = 0b0,
                                TYPE_FLAG_GENOMIC_VARIANT = 0b1;

    /**
     * Mapping to encode alleles.
     */
    public static final Map<Character, Long> ALLELE_MAPPING = new HashMap<>();
    static {
        ALLELE_MAPPING.put('A', 0L);
        ALLELE_MAPPING.put('T', 1L);
        ALLELE_MAPPING.put('G', 2L);
        ALLELE_MAPPING.put('C', 3L);
    }

    /**
     * Valid values for the alleles.
     * Either nothing ("-") or a certain number of bases ({A, T, G, C}).
     * Each [A, T, G, C] base is encoded on 2 bits.
     *
     * The maximum number of bases supported is  6 -> 12bits
     * and an additional 3 bits are used to encode the length.
     *
     */
    public static final String ALLELES_REGEX = "^([ATCG]{1,6}|-)$";

    /**
     * Possible range of positions values (position in 1-based coordinate system, minimum is 1).
     * Result is encoded into bits so the range is rounded to the nearest power of 2.
     * According to https://en.wikipedia.org/wiki/Human_genome#Molecular_organization_and_gene_content,
     * the chromosome with the higher number of base is #1 with 248'956'422 bases. 2^28 = 268'435'456.
     * ==> 28 bits storage
     */
    public static final long POSITION_MIN = 1, POSITION_MAX = 1L << POS_BIT_SIZE;

    /**
     * Encodes a genomic variant ID to be encrypted, according to the specifications.

     * @param chromosomeId the chromosome ID
     * @param startPosition the start position of the mutation
     * @param refAlleles the reference alleles
     * @param altAlleles the alternative alleles
     * @return the encoded ID
     * @throws UnlynxException if the input is not valid and cannot be encoded
     */
    public static long getVariantId(String chromosomeId, long startPosition, String refAlleles, String altAlleles) throws UnlynxException {

        // validate input
        if (!chromosomeId.matches(CHROMOSOME_ID_REGEX) ||
                startPosition < POSITION_MIN || startPosition > POSITION_MAX ||
                !refAlleles.matches(ALLELES_REGEX) || !altAlleles.matches(ALLELES_REGEX) ||
                TYPE_FLAG_BIT_SIZE + CHR_BIT_SIZE + POS_BIT_SIZE + 2 * (ALLELES_BASE_LENGTH_BIT_SIZE + ALLELES_BIT_SIZE) != ID_BIT_SIZE) {

            throw Logger.warn(new UnlynxException("Invalid input: chr=" + chromosomeId +
                    ", pos=" + startPosition + ", ref=" + refAlleles + ", alt=" + altAlleles));
        }

        // interpret chromosome id (content validated by regex)
        long chromosomeIntId;
        try {
            chromosomeIntId = Long.parseLong(chromosomeId);
        } catch (NumberFormatException e) {
            switch (chromosomeId) {
                case "X":
                    chromosomeIntId = CHROMOSOME_X_INT_ID;
                    break;
                case "Y":
                    chromosomeIntId = CHROMOSOME_Y_INT_ID;
                    break;
                case "M":
                    chromosomeIntId = CHROMOSOME_M_INT_ID;
                    break;
                default:
                    throw Logger.warn(new UnlynxException("Invalid chr id:" + chromosomeId));
            }
        }

        // alleles
        if (refAlleles.equals("-")) {
            refAlleles = "";
        }

        if (altAlleles.equals("-")) {
            altAlleles = "";
        }

        long refAllelesBaseLength = refAlleles.length();
        long altAllelesBaseLength = altAlleles.length();

        // generate the variant
        long id = 0L;
        id = pushBitsFromRight(id, TYPE_FLAG_BIT_SIZE, TYPE_FLAG_GENOMIC_VARIANT);
        id = pushBitsFromRight(id, CHR_BIT_SIZE, chromosomeIntId);
        id = pushBitsFromRight(id, POS_BIT_SIZE, startPosition);
        id = pushBitsFromRight(id, ALLELES_BASE_LENGTH_BIT_SIZE, refAllelesBaseLength);
        id = pushBitsFromRight(id, ALLELES_BIT_SIZE, encodeAlleles(refAlleles));
        id = pushBitsFromRight(id, ALLELES_BASE_LENGTH_BIT_SIZE, altAllelesBaseLength);
        id = pushBitsFromRight(id, ALLELES_BIT_SIZE, encodeAlleles(altAlleles));

        return id;
    }

    /**
     * Encode a string containing alleles.
     *
     * @param alleles a string containing alleles
     * @return the encoded alleles.
     */
    protected static long encodeAlleles(String alleles) {

        long encodedAlleles = 0b0;
        for (int i = 0 ; i < alleles.length() ; i++) {

            if (!ALLELE_MAPPING.containsKey(alleles.charAt(i))) {
                throw Logger.warn(new IllegalArgumentException("Invalid allele."));
            }

            encodedAlleles = pushBitsFromRight(
                    encodedAlleles,
                    2,
                    ALLELE_MAPPING.get(alleles.charAt(i)));
        }

        // padding
        encodedAlleles = pushBitsFromRight(
                encodedAlleles,
                ALLELES_BIT_SIZE - alleles.length() * 2,
                0L);

        return encodedAlleles;
    }

    /**
     * Takes the nbBits rightmost bits of bitsToPush, and push them to the right of origBits.
     *
     * @param origBits the value to push bits into
     * @param nbBits the number of bits to push
     * @param bitsToPush the value to take the bits from
     *
     * @return the concatenation of origBits and bitsToPush
     */
    protected static long pushBitsFromRight(long origBits, int nbBits, long bitsToPush) {
        long newBits = origBits << nbBits;

        // generate mask
        long mask = getMask(nbBits);

        // get final value
        newBits |= (mask & bitsToPush);
        return newBits;
    }

    protected static long getMask(int nbBits) {
        long mask = 0b0;
        for (int i = 0 ; i < nbBits ; i++) {
            mask <<= 1;
            mask |= 0b1;
        }
        return mask;
    }

    /**
     * Returns and updates (by incrementing) the next usable clinical id.
     *
     * @return the next usable clinical id
     * @throws UnlynxException if an overflow happens and the flag bit is not set correctly
     */
    public long getNextUsableClinicalId() throws UnlynxException {
        if ((nextUsableClinicalId & (1L << 63)) != 0) {
            throw Logger.error(new UnlynxException("Clinical id overflow detected"));
        }

        return this.nextUsableClinicalId++;
    }
}
