package ch.epfl.lca1.medco.unlynx;

import java.util.List;

/**
 * Container class for an Unlynx query.
 * Given java type input, format into an Unlynx XML Query.
 */
public class UnlynxQuery {
	
	/** Name / ID of the query. */
	private String queryId;
	
	/** Predicate to be queried. */
	private String predicate;
	
	/** Ordered list of the encrypted where values of the query. */
	private List<String> encWhereValuesB64;
	
	/** List of patients, each patients having rows of encrypted (and base64 encoded) data. */
	private List<List<String>> encPatientsDataB64;
	
	/** Public key of the client, encoded in base64. */
	private String clientPublicKeyB64;
	
	/** Result mode of the query (0: 1 result / DP, 1: all DP have same aggregation). */
	private int resultMode;
	
	/** Maximum time (in second) the query should run. */
	private long timeoutSeconds;
	
	// TODO: easier constructor
	//public UnlynxQuery(I2B2QueryRequest i2b2Query) {
		//this(i2b2Query.getName(), i2b2Query.generateUnlynxPredicate)
		//this.encWhereValues = encWhereValues;
		//this.aggregatingAttributes = "s1";
		//this.countFlag = "false";
	//}

	/**
	 * Default constructor, filling all fields of the query.
     *
	 * @param queryId the name of the query
	 * @param predicate the predicate
	 * @param encWhereValuesB64 the encrypted where values (base64 encoded)
	 * @param encPatientsDataB64 the encrypted patients data (base64 encoded)
	 * @param clientPublicKeyB64 the public key of the querier (base64 encoded)
	 * @param resultMode the result mode of the query (0 or 1)
	 * @param timeoutSeconds the maximum waiting time in seconds for the query
	 */
	public UnlynxQuery(String queryId, String predicate, List<String> encWhereValuesB64, List<List<String>> encPatientsDataB64,
			String clientPublicKeyB64, int resultMode, long timeoutSeconds) {

		this.queryId = queryId;
		this.predicate = predicate;
		this.encWhereValuesB64 = encWhereValuesB64;
		this.encPatientsDataB64 = encPatientsDataB64;
		this.clientPublicKeyB64 = clientPublicKeyB64;
		this.resultMode = resultMode;
		this.timeoutSeconds = timeoutSeconds;
	}

	/**
	 * Constructs from the encrypted where query values the string with the appropriate Unlynx format.
	 *
	 * @return unlynx - i2b2 formatted where query values.
	 */
	private String formatEncWhereValues() {
		StringBuilder  sb = new StringBuilder();
		
		sb.append("{");
		for (int i = 0 ; i < encWhereValuesB64.size() ; i++) {
			sb.append("w" + (i+1) + ", ");
			sb.append(encWhereValuesB64.get(i));
			
			if (i < encWhereValuesB64.size() - 1) {
				sb.append(", ");
			}
		}
		
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Construct a string containing the query in XML format.
	 * Example:
	 *
     * {@code
	 	<medco_query>
			<id>query ID</id>
			<predicate>some predicate</predicate>
			<enc_where_values>encrypted where query values</enc_where_values>
		
			<enc_patients_data>
				<patient>
					<enc_data>enc</enc_data>
					<enc_data>enc</enc_data>
					<enc_data>enc</enc_data>
				</patient>
				<patient>
					<enc_data>enc</enc_data>
					<enc_data>enc</enc_data>
					<enc_data>enc</enc_data>
				</patient>
			</enc_patients_data>
		
			<client_public_key>base64 encoded key</client_public_key>
			<result_mode> result mode (0 or 1)</result_mode>
		</medco_query>
     * }
	 * 
	 * @return query in Unlynx-I2b2 XML format
	 */
    String toUnlynxI2b2XML() {
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("<medco_query>\n");
			sb.append("<id>" + getQueryID() + "</id>\n");
			sb.append("<predicate>" + getPredicate() + "</predicate>\n");
			sb.append("<enc_where_values>" + formatEncWhereValues() + "</enc_where_values>\n");
			
			sb.append("<enc_patients_data>\n");
			
			for (List<String> patientData : encPatientsDataB64) {
				sb.append("<patient>\n");
				for (String dataB64 : patientData) {
					sb.append("<enc_data>" + dataB64 + "</enc_data>\n");
				}
				sb.append("</patient>\n");
			}
			
			sb.append("</enc_patients_data>\n");

			sb.append("<client_public_key>" + getClientPublicKeyB64() + "</client_public_key>\n");
			sb.append("<result_mode>" + getResultMode() + "</result_mode>\n");
		sb.append("</medco_query>\n");

		return sb.toString();
	}
	
	@Override
	public String toString() {
		return toUnlynxI2b2XML();
	}

    /**
     * @return the query ID
     */
	public String getQueryID() {
		return queryId;
	}

    /**
     * @return the predicate
     */
	public String getPredicate() {
		return predicate;
	}

    /**
     * @return the encrypted where values
     */
	public List<String> getEncWhereValues() {
		return encWhereValuesB64;
	}

    /**
     * @return the encrypted patients data
     */
	public List<List<String>> getEncPatientsData() {
		return encPatientsDataB64;
	}

    /**
     * @return the querier's public key
     */
	public String getClientPublicKeyB64() {
		return clientPublicKeyB64;
	}

    /**
     * @return the desired result mode of the query
     */
	public int getResultMode() {
		return resultMode;
	}

    /**
     * @return the maximum waiting time in seconds to wait for the query execution
     */
	public long getTimeoutSeconds() {
		return timeoutSeconds;
	}
}
