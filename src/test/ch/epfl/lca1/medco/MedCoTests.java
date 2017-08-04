package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.i2b2.crc.I2B2QueryRequest;
import ch.epfl.lca1.medco.unlynx.UnlynxClient;
import ch.epfl.lca1.medco.unlynx.UnlynxDecrypt;
import ch.epfl.lca1.medco.unlynx.UnlynxQuery;
import ch.epfl.lca1.medco.util.Logger;
import ch.epfl.lca1.medco.util.MedCoUtil;
import ch.epfl.lca1.medco.util.exceptions.MedCoException;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class MedCoTests extends ConfiguredTests {

    String clientPubKey = "eQviK90cvJ2lRx8ox6GgQKFmOtbgoG9RXa7UnmemtRA=";
    String clientSeckey = "iqLQz3zMlRjCyBrg4+303hsxL7F5vDtIaBxO0oc7gQA=";

    /**
     * Example query:
     * PRETREATMENT_HISTORY = NO -- MEDCO_CLEAR:3 -> 220 patients
     * ICD_O_3_SITE = C34.2 -- MEDCO_ENC:23 ->
     *
     */

    // query that contains 1 panel with 1 clear item + 1 panel with 1 encrypted item
    // todo once OK: get other queries more complex, in files, + error queries + manual computation
    // todo: generate the test queries from sylvain
    String testQuery =
            "<ns6:request xmlns:ns2=\"http://www.i2b2.org/xsd/hive/pdo/1.1/\" xmlns:ns4=\"http://www.i2b2.org/xsd/cell/crc/psm/1.1/\" xmlns:ns3=\"http://www.i2b2.org/xsd/cell/crc/pdo/1.1/\" xmlns:ns5=\"http://www.i2b2.org/xsd/hive/plugin/\" xmlns:ns6=\"http://www.i2b2.org/xsd/hive/msg/1.1/\" xmlns:ns7=\"http://www.i2b2.org/xsd/cell/ont/1.1/\" xmlns:ns8=\"http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/\">\n" +
            "    <message_header>\n" +
            "        <i2b2_version_compatible>1.1</i2b2_version_compatible>\n" +
            "        <sending_application>\n" +
            "            <application_name>i2b2 Query Tool</application_name>\n" +
            "            <application_version>1.3</application_version>\n" +
            "        </sending_application>\n" +
            "        <sending_facility>\n" +
            "            <facility_name>i2b2 Hive</facility_name>\n" +
            "        </sending_facility>\n" +
            "        <receiving_application>\n" +
            "            <application_name>CRC Cell</application_name>\n" +
            "            <application_version>1.3</application_version>\n" +
            "        </receiving_application>\n" +
            "        <receiving_facility>\n" +
            "            <facility_name>i2b2 Hive</facility_name>\n" +
            "        </receiving_facility>\n" +
            "        <datetime_of_message>2010-02-17T13:29:01.804-05:00</datetime_of_message>\n" +
            "        <security>\n" +
            "            <domain>i2b2demotest</domain>\n" +
            "            <username>demo</username>\n" +
            "            <password>demouser</password>\n" +
            "        </security>\n" +
            "        <message_type>\n" +
            "            <message_code>Q04</message_code>\n" +
            "            <event_type>EQQ</event_type>\n" +
            "        </message_type>\n" +
            "        <message_control_id>\n" +
            "            <message_num>yfMqNOUXQxzVpLkBJk9m</message_num>\n" +
            "            <instance_num>0</instance_num>\n" +
            "        </message_control_id>\n" +
            "        <processing_id>\n" +
            "            <processing_id>P</processing_id>\n" +
            "            <processing_mode>I</processing_mode>\n" +
            "        </processing_id>\n" +
            "        <accept_acknowledgement_type>AL</accept_acknowledgement_type>\n" +
            "        <application_acknowledgement_type>AL</application_acknowledgement_type>\n" +
            "        <country_code>US</country_code>\n" +
            "        <project_id>Demo</project_id>\n" +
            "    </message_header>\n" +
            "    <request_header>\n" +
            "        <result_waittime_ms>180000</result_waittime_ms>\n" +
            "    </request_header>\n" +
            "    <message_body>\n" +
            "        <ns4:psmheader>\n" +
            "            <user group=\"i2b2demo\" login=\"demo\">demo</user>\n" +
            "            <estimated_time>0</estimated_time>\n" +
            "            <request_type>CRC_QRY_runQueryInstance_fromQueryDefinition</request_type>\n" +
            "        </ns4:psmheader>\n" +
            "        <ns4:request xsi:type=\"ns4:query_definition_requestType\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
            "            <query_definition>\n" +
            "                <query_name>Some test query 1</query_name>\n" +
            "                <specificity_scale>0</specificity_scale>\n" +
            "                <panel>\n" +
            "                    <panel_number>1</panel_number>\n" +
            "                    <panel_accuracy_scale>0</panel_accuracy_scale>\n" +
            "                    <total_item_occurrences>1</total_item_occurrences>\n" +
            "                    <item>\n" +
            "\t\t\t\t\t\t<hlevel>4</hlevel>\n" +
            "\t\t\t\t\t\t<item_name>NO</item_name>\n" +
            "\t\t\t\t\t\t<item_key>\\\\CLINICAL_NON_SENSITIVE\\medco\\clinical\\nonsensitive\\PRETREATMENT_HISTORY\\NO\\</item_key>\n" +
            "\t\t\t\t\t\t<tooltip>NO</tooltip>\n" +
            "\t\t\t\t\t\t<class>ENC</class>\n" +
            "\t\t\t\t\t\t<item_icon>FA</item_icon>\n" +
            "\t\t\t\t\t\t<item_is_synonym>false</item_is_synonym>\n" +
            "                    </item>\n" +
            "                </panel>\n" +
            "                <panel>\n" +
            "                    <panel_number>2</panel_number>\n" +
            "                    <panel_accuracy_scale>0</panel_accuracy_scale>\n" +
            "                    <total_item_occurrences>1</total_item_occurrences>\n" +
            "                    <item>\n" +
            "\t\t\t\t\t\t<hlevel>0</hlevel>\n" +
            "\t\t\t\t\t\t<item_name>X</item_name>\n" +
            "\t\t\t\t\t\t<item_key>MEDCO_ENC:bCQM+Q+ttScOnp5+OHXcL/V6cb6/Bi9iEQlihe8Q2imsAi53Kudg1MuBhkDHR8i4B+3CPBbWxFtIsF/4ZHSt+w==</item_key>\n" +
            "\t\t\t\t\t\t<tooltip>X</tooltip>\n" +
            "\t\t\t\t\t\t<class>ENC</class>\n" +
            "\t\t\t\t\t\t<item_icon>FA</item_icon>\n" +
            "\t\t\t\t\t\t<item_is_synonym>false</item_is_synonym>\n" +
            "                    </item>\n" +
            "                </panel>\n" +
            "            </query_definition>\n" +
            "            <result_output_list>\n" +
            "                <result_output priority_index=\"1\" name=\"PATIENT_COUNT_XML\"/>\n" +
            "            </result_output_list>\n" +
            "        </ns4:request>\n" +
            "    </message_body>\n" +
            "</ns6:request>\n";


	@Test
	public void MedCoQueryTests() throws InterruptedException, I2B2Exception, MedCoException {

        // call the clients
        Thread query1Thread = new Thread(() -> {
            try {
                loadSrv1Conf();
                I2B2QueryRequest i2b2Req = new I2B2QueryRequest(testQuery);
                MedCoQuery medCoQuery = new MedCoQuery(i2b2Req);
                String result1 = medCoQuery.executeQuery(0, clientPubKey, 180).getEncCountResult();
                Logger.info(result1);
                UnlynxDecrypt decrypt = new UnlynxDecrypt();
                Logger.info(decrypt.decryptInt(result1, clientSeckey) + "");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        query1Thread.start();
        Thread.sleep(10000);

        Thread query2Thread = new Thread(() -> {
            try {
                loadSrv3Conf();
                I2B2QueryRequest i2b2Req = new I2B2QueryRequest(testQuery);
                MedCoQuery medCoQuery = new MedCoQuery(i2b2Req);
                String result1 = medCoQuery.executeQuery(0, clientPubKey, 180).getEncCountResult();
                Logger.info(result1);
                UnlynxDecrypt decrypt = new UnlynxDecrypt();
                Logger.info(decrypt.decryptInt(result1, clientSeckey) + "");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        query2Thread.start();
        Thread.sleep(10000);

        Thread query3Thread = new Thread(() -> {
            try {
                loadSrv5Conf();
                I2B2QueryRequest i2b2Req = new I2B2QueryRequest(testQuery);
                MedCoQuery medCoQuery = new MedCoQuery(i2b2Req);
                String result1 = medCoQuery.executeQuery(0, clientPubKey, 180).getEncCountResult();
                Logger.info(result1);
                UnlynxDecrypt decrypt = new UnlynxDecrypt();
                Logger.info(decrypt.decryptInt(result1, clientSeckey) + "");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        query3Thread.start();
        Thread.sleep(10000);

        query1Thread.join();
        query2Thread.join();
        query3Thread.join();

    }


}
