package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.i2b2.MessagesUtil;
import ch.epfl.lca1.medco.util.Constants;
import ch.epfl.lca1.medco.util.XMLUtil;
import edu.harvard.i2b2.common.util.jaxb.JAXBUtilException;
import edu.harvard.i2b2.crc.datavo.i2b2message.BodyType;
import edu.harvard.i2b2.crc.datavo.i2b2message.ResponseMessageType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.MasterInstanceResultResponseType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.QueryResultInstanceType;
import edu.harvard.i2b2.crc.datavo.setfinder.query.XmlValueType;
import org.junit.Test;

import javax.xml.bind.JAXBElement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by misbach on 20.07.17.
 */
public class Sandbox {

    @Test
    public void xmlValueTypeTest() throws JAXBUtilException {
        edu.harvard.i2b2.crc.datavo.setfinder.query.ObjectFactory querySetFinderOF =
                new edu.harvard.i2b2.crc.datavo.setfinder.query.ObjectFactory();
        edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory i2b2OF =
                new edu.harvard.i2b2.crc.datavo.i2b2message.ObjectFactory();

        XmlValueType xml = querySetFinderOF.createXmlValueType();
        xml.getContent().add("teststring");
        xml.getContent().add("teststring2");


        MasterInstanceResultResponseType res = querySetFinderOF.createMasterInstanceResultResponseType();
        QueryResultInstanceType queryRes = querySetFinderOF.createQueryResultInstanceType();
        res.getQueryResultInstance().add(queryRes);
        queryRes.setMessage(xml);

        ResponseMessageType resp = i2b2OF.createResponseMessageType();
        BodyType body = i2b2OF.createBodyType();
        body.getAny().add(res);
        resp.setMessageBody(body);
        JAXBElement<ResponseMessageType> respJAXB = i2b2OF.createResponse(resp);

        MasterInstanceResultResponseType parsed =
        (MasterInstanceResultResponseType) MessagesUtil.getUnwrapHelper().getObjectByClass(
                respJAXB.getValue().getMessageBody().getAny(), MasterInstanceResultResponseType.class);

        System.out.println(parsed.getQueryResultInstance().get(0).getMessage().getContent().get(0));
        System.out.println(parsed.getQueryResultInstance().get(0).getMessage().getContent().get(1));

    }

    @Test
    public void parsingXmlResultTest() {
        String s = "&lt;json_results>{\"pub_key\":\"eQviK90cvJ2lRx8ox6GgQKFmOtbgoG9RXa7UnmemtRA=\",\"enc_count_result\":\"rvpNFNyDaZSafu94h06/RKW83E7hbb1sgN+NexlWotmkTqZxv5Z5/Pz7iU1jyUa1/Bd5J1rAszqU2ZL8DhqIAQ==\",\"times\":{\"Overall (axis2 in/out)\":85398,\"Query parsing/splitting\":1,\"Clear query: i2b2 query\":342,\"Clear query: patient set retrieval\":87,\"Patient set encrypted data retrieval\":471,\"Unlynx query\":84491,\"Unlynx execution time\":11874,\"Unlynx communication time\":72437,\"Unlynx DDT execution time\":10342,\"DDT communication time\":21444}}&lt;/json_results>";
        String unescaped = XMLUtil.unEscapeXmlValue(s);

        String sub = unescaped.substring(unescaped.indexOf("<json_results>") + "<json_results>".length(),
                unescaped.lastIndexOf("</json_results>"));
        System.out.println(sub);
    }

    @Test
    public void testRegex() {
        String okayValue = "\\\\CLINICAL_SENSITIVE\\medco\\encrypted\\C_ENC:HgtzHGZTHjkuj7778=\\";

        Matcher medcoKeyMatcher = Constants.REGEX_QUERY_KEY_ENC.matcher(okayValue);
        if (medcoKeyMatcher.matches()) {
            System.out.println(medcoKeyMatcher.group(2));
        }

    }
}
