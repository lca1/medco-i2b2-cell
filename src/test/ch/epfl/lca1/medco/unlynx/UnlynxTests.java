package ch.epfl.lca1.medco.unlynx;

import ch.epfl.lca1.medco.unlynx.*;
import ch.epfl.lca1.medco.util.MedCoUtil;
import org.junit.Assert;
import org.junit.Test;

import ch.epfl.lca1.medco.util.exceptions.I2B2XMLException;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class UnlynxTests {
	
	String queryResultStringOK = 
		"<medco_query_result>\n" +
		"	<id>query ID</id>\n" +
		"	<result_mode>0</result_mode>\n" +
		"	<enc_result>encrypted result</enc_result>\n" +
		"</medco_query_result>\n";
	
	String queryResultStringError = 
			"<medco_query_result>\n" +
			"	<id>query ID</id>\n" +
			"	<error>a message error (only if error, the enc_result will be empty)</error>\n" +
			"</medco_query_result>\n";
	
	String queryResultStringMalformed = 
			"<medco_query_result>\n" +
			"	<id>query ID</id>\n" +
			"	error>a message error (only if error, the enc_result will be empty)</error>\n" +
			"<medco_query_result>\n";
	
	@Test
	public void UnlynxQueryResultsTests() { 
		/*
		try {
			UnlynxQueryResult queryResultOK = new UnlynxQueryResult(queryResultStringOK);
			assertTrue(queryResultOK.getQueryId().equals("query ID"));
			assertTrue(queryResultOK.getResultMode() == 0);
			assertTrue(queryResultOK.getEncResultB64().equals("encrypted result"));
			assertTrue(queryResultOK.getErrorMessage() == null);
			
			UnlynxQueryResult queryResultError = new UnlynxQueryResult(queryResultStringError);
			assertTrue(queryResultError.getQueryId().equals("query ID"));
			assertTrue(queryResultError.getResultMode() == -1);
			assertTrue(queryResultError.getEncResultB64() == null);
			assertTrue(queryResultError.getErrorMessage().equals("a message error (only if error, the enc_result will be empty)"));
			assertTrue(!queryResultError.getErrorMessage().equals("a message error (only if error,asdasdsad the enc_result will be empty)"));
			
			try {
				new UnlynxQueryResult(queryResultStringMalformed);
				assertTrue(false);
			} catch (I2B2XMLException e) {
				assertTrue(true);
			}
			
			UnlynxQueryResult queryResultOK2 = new UnlynxQueryResult(queryResultStringOK);
			assertTrue(queryResultOK2.getQueryId().equals("query ID"));
			assertTrue(queryResultOK2.getResultMode() == 0);
			assertTrue(queryResultOK2.getEncResultB64().equals("encrypted result"));
			assertTrue(queryResultOK2.getErrorMessage() == null);
			
			UnlynxQueryResult queryResultError2 = new UnlynxQueryResult(queryResultStringError);
			assertTrue(queryResultError2.getQueryId().equals("query ID"));
			assertTrue(queryResultError2.getResultMode() == -1);
			assertTrue(queryResultError2.getEncResultB64() == null);
			assertTrue(queryResultError2.getErrorMessage().equals("a message error (only if error, the enc_result will be empty)"));
			assertTrue(!queryResultError2.getErrorMessage().equals("a message error (only if error,asdasdsad the enc_result will be empty)"));
			
			try {
				new UnlynxQueryResult(queryResultStringMalformed);
				assertTrue(false);
			} catch (I2B2XMLException e) {
				assertTrue(true);
			}

		} catch (I2B2XMLException e) {
			System.out.println(e);
			assertTrue(false);
		}
*/
	}

	/*
	copied from go code output, with the fixed test keys:

	generated xml: &{<medco_query>
	<id>query_ID_XYZf</id>
	<predicate>(exists(v0, r) || exists(v1, r)) &amp;&amp; (exists(v2, r) || exists(v3, r)) &amp;&amp; exists(v4, r)</predicate>
	<enc_where_values>{w0, 763+X5P5yNOht7a2Oj9nMxPkgVsD2qW3ckaioF3YftXoDYlBZutwrU973+VDHKoOeyczfuuG4JmCplvvxefwgQ==, w1, BGDl65kJMTxMwBvT9rUXcSbeFP8QBuwxz+RfE+5ewbrTWf0PzcODsLvaJycj6izuVJ6FI6rb1sHTGdBLIUqEOw==, w2, YP0jHfsE8pQl6ArwvObc29Eb+88o4zdSNb2O5z8f5sCtKzEoIZNIFie24CAuC6cFCaSRg2NYQj8fbGRoV6HLAA==, w3, BfCSQ/BjveL3ABuSCOzVchCmggico/5KpDjo7cggndYaMmm3Bd4a6FAWuJqejlkrpleOnNJTrvfzNVi2ZsIryg==, w4, CD41wbfOZn5g2D7fsrfPxP+jVrmljVZYnlNpaoXauPvXyNlNye2Lo9LKPF/S+xq+NmbpstS/zl41aYxVMvOAJA==}</enc_where_values>

	<enc_patients_data>
	<patient>
	<enc_data>yN+HHN/MpLXRql+OKYTQOxuIP4+Ed5lIZhjt0b8NytKEUuU/CQSwxh+nvIVLs5/RHAZsTDBf5HX6X5NXY+LceA==</enc_data>
	<enc_data>vBA+f85xmidtT5G6BgBYDf3knZIXc/zmczCXCrcLAw22+bDFvOGuv9kUQ90j4ElTgGV7FzQG2dC/K7J5Tlpd+g==</enc_data>
	<enc_data>OQTvPM3G85sroMfQJYUXujtHyWdqUyCAZTSEp0mofUtAYdv2AvoM9DZH0pAr74BHeS8Q+aY5z5Xec709NpO8Ow==</enc_data>
	</patient>
	<patient>
	<enc_data>kdUlF3Idp3SghRlipJffMV/mZKrNCa4wgK+cN3IXnAHWbe9C9YH02GKYbtriINTU8IyU1vrjbK6nCXokcPYFMw==</enc_data>
	<enc_data>E7Ah6E5BwcDx194lM/T79EqD4i0gWKkMnm3gIGIHs5C9T9Po/3Kg4iSIhIUlTp8wqWwVUJpNgZoMulXsub3nsw==</enc_data>
	<enc_data>Bfq/9FA9d2ZrpsoefOIjkXyGCxyVqLgn/LhRYmBVWs80iA7CV1kd3eMSYcYiunba1CvRVg1z9ZzrE/WoCLxgFQ==</enc_data>
	</patient>
	<patient>
	<enc_data>1afOcTlvZHxlEU69/olhLYrcjj1m6cfFdUJXJuFN9cCIvsCXDqewEzgLAj51qMTeAOphWrhPcPfRTsiyH9jnpA==</enc_data>
	<enc_data>XMysQBNVYVzCgVSZ/89UeX1oSlf+Kr6hIvaAqK991eS2tJRjBTGw9c9DoWGKH/N9jSeEZKAGPRDHXDLhtjPu4Q==</enc_data>
	<enc_data>j/f49tDfFh9N6IPMz/iPN0pl0heV0T3g8xPnd3RQAiwqEBBkVnCrpYPFBBoJ3nJ+yMb/+mHYpq9YiAZ5v6QSoA==</enc_data>
	<enc_data>uXLMVSVXGELTVcj+XqV1ETKBd/BvLoKnpcNdjCYb4Y11a6eawOh3JmWQgL95yylpOTZnEPOj9HGMvlHHCngr/w==</enc_data>
	<enc_data>v4nX8dA5sGH7Zg7Bolwjnu6g4J0asaAhXbNHEdp6F38YyeiRLCCFcPgAPSp8zgODg33LNF5hXnr8SuYe6+TKhQ==</enc_data>
	<enc_data>Xgk+3hsR9MhAvY6MgXKULgae4f7Lj+Lq+rnTkj2uDv1ZHwWSQZrpXcwuPNR7Q+FWh7AIX1Uu1LHWdSuBU7XG2g==</enc_data>
	<enc_data>BeqHx9HauCb744Zc+w4U1BGZ9UdEbGpIWvDxCasB7CL2cwmZEakwKPh4Sa7sMPA4V+lFSOowZ1tWTo+dvK6uqw==</enc_data>
	<enc_data>9PfwXbWTEhlqFd8UdU0XpNC0Fb8ShwskwT+nKjf58LVCzSocdn2F1luJ5f67i9Eh2gctNSAhMrim5g3T+CF9qw==</enc_data>
	</patient>
	</enc_patients_data>

	<client_public_key>KwK0+6MIGqQ3JmBSS8WmJ0FSv564QZB/MfuYHQ4ANA4=</client_public_key>
	<result_mode>0</result_mode>
	</medco_query>
	 */
/*
	private UnlynxQuery getGoodQuery() {

	    List<String> encWhereValues = new ArrayList<>();
	    encWhereValues.add("763+X5P5yNOht7a2Oj9nMxPkgVsD2qW3ckaioF3YftXoDYlBZutwrU973+VDHKoOeyczfuuG4JmCplvvxefwgQ==");
        encWhereValues.add("BGDl65kJMTxMwBvT9rUXcSbeFP8QBuwxz+RfE+5ewbrTWf0PzcODsLvaJycj6izuVJ6FI6rb1sHTGdBLIUqEOw==");
        encWhereValues.add("YP0jHfsE8pQl6ArwvObc29Eb+88o4zdSNb2O5z8f5sCtKzEoIZNIFie24CAuC6cFCaSRg2NYQj8fbGRoV6HLAA==");
        encWhereValues.add("BfCSQ/BjveL3ABuSCOzVchCmggico/5KpDjo7cggndYaMmm3Bd4a6FAWuJqejlkrpleOnNJTrvfzNVi2ZsIryg==");
        encWhereValues.add("CD41wbfOZn5g2D7fsrfPxP+jVrmljVZYnlNpaoXauPvXyNlNye2Lo9LKPF/S+xq+NmbpstS/zl41aYxVMvOAJA==");

        List<String> pData1 = new ArrayList<>();
        pData1.add("yN+HHN/MpLXRql+OKYTQOxuIP4+Ed5lIZhjt0b8NytKEUuU/CQSwxh+nvIVLs5/RHAZsTDBf5HX6X5NXY+LceA==");
        pData1.add("vBA+f85xmidtT5G6BgBYDf3knZIXc/zmczCXCrcLAw22+bDFvOGuv9kUQ90j4ElTgGV7FzQG2dC/K7J5Tlpd+g==");
        pData1.add("OQTvPM3G85sroMfQJYUXujtHyWdqUyCAZTSEp0mofUtAYdv2AvoM9DZH0pAr74BHeS8Q+aY5z5Xec709NpO8Ow==");

        List<String> pData2 = new ArrayList<>();
        pData2.add("kdUlF3Idp3SghRlipJffMV/mZKrNCa4wgK+cN3IXnAHWbe9C9YH02GKYbtriINTU8IyU1vrjbK6nCXokcPYFMw==");
        pData2.add("E7Ah6E5BwcDx194lM/T79EqD4i0gWKkMnm3gIGIHs5C9T9Po/3Kg4iSIhIUlTp8wqWwVUJpNgZoMulXsub3nsw==");
        pData2.add("Bfq/9FA9d2ZrpsoefOIjkXyGCxyVqLgn/LhRYmBVWs80iA7CV1kd3eMSYcYiunba1CvRVg1z9ZzrE/WoCLxgFQ==");

        List<String> pData3 = new ArrayList<>();
        pData3.add("1afOcTlvZHxlEU69/olhLYrcjj1m6cfFdUJXJuFN9cCIvsCXDqewEzgLAj51qMTeAOphWrhPcPfRTsiyH9jnpA==");
        pData3.add("XMysQBNVYVzCgVSZ/89UeX1oSlf+Kr6hIvaAqK991eS2tJRjBTGw9c9DoWGKH/N9jSeEZKAGPRDHXDLhtjPu4Q==");
        pData3.add("j/f49tDfFh9N6IPMz/iPN0pl0heV0T3g8xPnd3RQAiwqEBBkVnCrpYPFBBoJ3nJ+yMb/+mHYpq9YiAZ5v6QSoA==");
        pData3.add("uXLMVSVXGELTVcj+XqV1ETKBd/BvLoKnpcNdjCYb4Y11a6eawOh3JmWQgL95yylpOTZnEPOj9HGMvlHHCngr/w==");
        pData3.add("v4nX8dA5sGH7Zg7Bolwjnu6g4J0asaAhXbNHEdp6F38YyeiRLCCFcPgAPSp8zgODg33LNF5hXnr8SuYe6+TKhQ==");
        pData3.add("Xgk+3hsR9MhAvY6MgXKULgae4f7Lj+Lq+rnTkj2uDv1ZHwWSQZrpXcwuPNR7Q+FWh7AIX1Uu1LHWdSuBU7XG2g==");
        pData3.add("BeqHx9HauCb744Zc+w4U1BGZ9UdEbGpIWvDxCasB7CL2cwmZEakwKPh4Sa7sMPA4V+lFSOowZ1tWTo+dvK6uqw==");
        pData3.add("9PfwXbWTEhlqFd8UdU0XpNC0Fb8ShwskwT+nKjf58LVCzSocdn2F1luJ5f67i9Eh2gctNSAhMrim5g3T+CF9qw==");

        List<List<String>> encPatientsData = new ArrayList<>();
        encPatientsData.add(pData1);
        encPatientsData.add(pData2);
        encPatientsData.add(pData3);

		UnlynxQuery q = new UnlynxQuery("query_test",
                "(exists(v0, r) || exists(v1, r)) &amp;&amp; (exists(v2, r) || exists(v3, r)) &amp;&amp; exists(v4, r)",
                encWhereValues,
                encPatientsData,
                "KwK0+6MIGqQ3JmBSS8WmJ0FSv564QZB/MfuYHQ4ANA4=",
                0,
                20
        );
		return q;
	}
	*/
	@Test
	public void UnlynxQueryTests() { 

	    // TODO
		//UnlynxQuery query = getGoodQuery();

	}

	@Test
	public void UnlynxEncryptTests() {

        // set config / TODO: not portable (embed the start unlynx somehwere)
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_BINARY_PATH_PROPERTIES, "unlynxI2b2"); // assumed in bin path
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_GROUP_FILE_PATH_PROPERTIES,
                "/home/misbach/go/src/github.com/JoaoAndreSa/MedCo/app/unlynxI2b2/test/group.toml");

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_DEBUG_LEVEL_PROPERTIES, "5");


        UnlynxEncrypt enc = new UnlynxEncrypt();
        try {
            String t = enc.encryptInt(65468);
            String t2 = enc.encryptInt(65252525468L);
            String t3 = enc.encryptInt(6546854849842555629L);
            String t4 = enc.encryptInt(6546548);

            System.out.println(t);
            System.out.println(t2);
            System.out.println(t3);
            System.out.println(t4);
            assertTrue(true);

        } catch (IOException e) {
            assertTrue(false);
        }
	}
	/*
	@Test
	public void UnlynxClientTests() throws InterruptedException {

	    // set config / TODO: not portable (embed the start unlynx somehwere)
	    MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_BINARY_PATH_PROPERTIES, "unlynxI2b2"); // assumed in bin path
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_GROUP_FILE_PATH_PROPERTIES,
                "/home/misbach/go/src/github.com/JoaoAndreSa/MedCo/app/unlynxI2b2/test/group.toml");

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_DEBUG_LEVEL_PROPERTIES, "5");
        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_PROOFS_PROPERTIES, "0");

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_ENTRY_POINT_IDX_PROPERTIES, "0");
		UnlynxClient client0 = UnlynxClient.executeQuery(getGoodQuery());
		Thread.sleep(1000);

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_ENTRY_POINT_IDX_PROPERTIES, "1");
        UnlynxClient client1 = UnlynxClient.executeQuery(getGoodQuery());
        Thread.sleep(1000);

        MedCoUtil.getTestInstance().setProperty(MedCoUtil.UNLYNX_ENTRY_POINT_IDX_PROPERTIES, "2");
        UnlynxClient client2 = UnlynxClient.executeQuery(getGoodQuery());
        Thread.sleep(1000);


		client0.join();
        client1.join();
        client2.join();

        System.out.println(client0.getQueryResult().getErrorMessage());
        System.out.println(client1.getQueryResult().getErrorMessage());
        System.out.println(client2.getQueryResult().getErrorMessage());

        assertTrue(client0.getQueryState() == UnlynxClient.QueryState.COMPLETED);
        assertTrue(client1.getQueryState() == UnlynxClient.QueryState.COMPLETED);
        assertTrue(client2.getQueryState() == UnlynxClient.QueryState.COMPLETED);

    }

*/
}
