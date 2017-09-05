package ch.epfl.lca1.medco.loader;

import ch.epfl.lca1.medco.util.exceptions.UnlynxException;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class EncryptedIdsUnlynxTests {

    @Test
    public void PushBitsTest() {

        long orig = 0b1110_1011;

        Assert.assertEquals(
                EncryptedIdentifiersManager.pushBitsFromRight(orig, 0, 0b101),
                0b1110_1011
        );

        Assert.assertEquals(
                EncryptedIdentifiersManager.pushBitsFromRight(orig, 1, 0b1111101),
                0b1110_1011_1
        );

        Assert.assertEquals(
                EncryptedIdentifiersManager.pushBitsFromRight(orig, 3, 0b101),
                0b11101011101
        );

        Assert.assertEquals(
                EncryptedIdentifiersManager.pushBitsFromRight(orig, 8, 0b101011110101011),
                0b1110_1011_1010_1011
        );
    }

    @Test
    public void RegexTests() {
        Assert.assertTrue(
                "3".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertTrue(
                "11".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertTrue(
                "20".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertTrue(
                "X".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertFalse(
                "33".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertFalse(
                "V".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertFalse(
                "XX".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertFalse(
                "0".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );
        Assert.assertFalse(
                "M2".matches(EncryptedIdentifiersManager.CHROMOSOME_ID_REGEX)
        );

        Assert.assertTrue(
                "ATGGG".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertTrue(
                "-".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertTrue(
                "GATGGG".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertFalse(
                "ATGGGGGG".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertFalse(
                "D".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertFalse(
                "3".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertTrue(
                "-".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
        Assert.assertFalse(
                "G-".matches(EncryptedIdentifiersManager.ALLELES_REGEX)
        );
    }

    @Test
    public void GetIDTests() {

        try {
            long id = EncryptedIdentifiersManager.getVariantId(
                    "3",
                    33366L,
                    "ATCC",
                    "-");

            long check = new BigInteger(
                    "1" +
                    "00011" +
                    "0000000000001000001001010110" +
                    "100" +
                    "000111110000" +
                    "000" +
                    "000000000000", 2).longValue();
            System.out.println(id);
            Assert.assertEquals(id, check);

            long id2 = EncryptedIdentifiersManager.getVariantId(
                    "M",
                    33366L,
                    "-",
                    "AAAAAA");

            long check2 = new BigInteger(
                    "1" +
                            "11010" +
                            "0000000000001000001001010110" +
                            "000" +
                            "000000000000" +
                            "110" +
                            "000000000000", 2).longValue();

            Assert.assertEquals(id2, check2);

        } catch (UnlynxException e) {
            Assert.fail(e.getMessage());
        }


        try {
            EncryptedIdentifiersManager.getVariantId("3",
                    33366L,
                    "ATCCCCC",
                    "-");
            Assert.fail();
        } catch (UnlynxException e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
        try {
            EncryptedIdentifiersManager.getVariantId(
                    "XX",
                    33366L,
                    "ATCC",
                    "-");
            Assert.fail();
        } catch (UnlynxException e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }        try {
            EncryptedIdentifiersManager.getVariantId(
                    "3",
                    33366L,
                    "XATCC",
                    "-");
            Assert.fail();
        } catch (UnlynxException e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }        try {
            EncryptedIdentifiersManager.getVariantId(
                    "3",
                    -33366L,
                    "ATCC",
                    "-");
            Assert.fail();
        } catch (UnlynxException e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
    }
}
