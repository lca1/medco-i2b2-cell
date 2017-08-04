package ch.epfl.lca1.medco.util;

import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Util class containing functions related to XML handling.
 */
public class XMLUtil {

    public static final String[][] XML_CHARS_ESCAPES = {
            {"&", "&amp;"}, // must be first
            {"\"", "&quot;"},
            {"'", "&apos;"},
            {"<", "&lt;"},
            {">", "&gt;"}
    };

    /**
     * Given an InputStream, search for an XML (defined by the start and end tag) and extract it as a string.
     * Should be called only if the process is over and nothing is written further in the stream.

     * @param stdin the InputStream from which data is read
     * @throws I2B2XMLException in case of invalid XML
     */
    public static String xmlStringFromStream(InputStream stdin, String startTag, String endTag, boolean omitSurroundingTags) throws I2B2XMLException {
        Logger.debug("Scanning stdin for XML result.");
        BufferedReader buffStdIn = new BufferedReader(new InputStreamReader(stdin));

        // extract all lines containing the xml
        StringBuilder xmlSb = new StringBuilder();
        for (Iterator<String> it = buffStdIn.lines().iterator(); it.hasNext() ;) {

            String line = it.next();
            Logger.debug("stdin line: " + line);

            if (line.contains(startTag)) {
                while (!line.contains(endTag)) {
                    xmlSb.append(line);
                    try {
                        line = it.next();
                    } catch (NoSuchElementException e) {
                        throw Logger.error(new I2B2XMLException("Arrived at end of stream: could not find the end of the XML.", e));
                    }
                }
                xmlSb.append(line);
                break;
            }
        }

        // trim the xml string and return it
        int startIdx = omitSurroundingTags ?
                xmlSb.indexOf(startTag) + startTag.length():
                xmlSb.indexOf(startTag);

        int endIdx = omitSurroundingTags ?
                xmlSb.indexOf(endTag) :
                xmlSb.indexOf(endTag) + endTag.length();

        try {
            String xmlString = xmlSb.substring(startIdx, endIdx);
            if (xmlString.length() == 0) {
                throw Logger.error(new I2B2XMLException("Invalid xml string value."));
            }
            return xmlString;

        } catch (StringIndexOutOfBoundsException e) {
            throw Logger.error(new I2B2XMLException("Invalid xml string value.", e));
        }
    }

    /**
     * Escapes XML special character from a string, to make it pluggable into some XML.
     *
     * @param val the value to escape
     * @return the escaped value
     */
    public static String escapeXmlValue(String val) {
        String escapedVal = val;
        for (int i = 0 ; i < XML_CHARS_ESCAPES.length ; i++) {
            escapedVal = escapedVal.replace(XML_CHARS_ESCAPES[i][0], XML_CHARS_ESCAPES[i][1]);
        }
        return escapedVal;
    }

    public static String unEscapeXmlValue(String val) {
        String unEscapedVal = val;
        for (int i = 0 ; i < XML_CHARS_ESCAPES.length ; i++) {
            unEscapedVal = unEscapedVal.replace(XML_CHARS_ESCAPES[i][1], XML_CHARS_ESCAPES[i][0]);
        }
        return unEscapedVal;
    }
}
