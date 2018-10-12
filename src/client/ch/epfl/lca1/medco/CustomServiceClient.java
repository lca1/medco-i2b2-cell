package ch.epfl.lca1.medco;

import ch.epfl.lca1.medco.util.Logger;
import edu.harvard.i2b2.common.exception.I2B2Exception;
import edu.harvard.i2b2.common.exception.StackTraceUtil;
import edu.harvard.i2b2.common.util.axis2.ServiceClient;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.context.ServiceContext;

import org.apache.axis2.java.security.SSLProtocolSocketFactory;
import org.apache.axis2.java.security.TrustAllTrustManager;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.Security;

/**
 * Created by misbach on 22.07.17.
 */
public class CustomServiceClient extends ServiceClient {

    /**
     * Custom high timeout
     * all certificates accepted!!!! (to be used only in client for dev!!)
     *
     * @param restEPR
     * @param request
     * @return
     * @throws Exception
     */
    public static String sendRESTCustomTimeout(String restEPR, OMElement request) throws Exception{

        String response = null;
        org.apache.axis2.client.ServiceClient serviceClient = null;
        try {

            serviceClient = new org.apache.axis2.client.ServiceClient();

            ServiceContext context = serviceClient.getServiceContext();
            MultiThreadedHttpConnectionManager connManager = (MultiThreadedHttpConnectionManager)context.getProperty(HTTPConstants.MULTITHREAD_HTTP_CONNECTION_MANAGER);

            if(connManager == null) {
                connManager = new MultiThreadedHttpConnectionManager();
                context.setProperty(HTTPConstants.MULTITHREAD_HTTP_CONNECTION_MANAGER, connManager);
                connManager.getParams().setMaxTotalConnections(100);
                connManager.getParams().setMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION, 100);
            }
            HttpClient httpClient = new HttpClient(connManager);

            Options options = new Options();
            options.setTo(new EndpointReference(restEPR));
            options.setTransportInProtocol(Constants.TRANSPORT_HTTP);
            options.setProperty(Constants.Configuration.ENABLE_REST, Constants.VALUE_TRUE);
            options.setProperty(HTTPConstants.CACHED_HTTP_CLIENT, httpClient);
            options.setProperty(HTTPConstants.REUSE_HTTP_CLIENT, Constants.VALUE_TRUE);

            options.setProperty(HTTPConstants.SO_TIMEOUT, 6000000);
            options.setProperty(HTTPConstants.CONNECTION_TIMEOUT, 6000000);
            options.setTimeOutInMilliSeconds(6000000L);

            // options to accept all certificates
//            SSLContext sslCtx = SSLContext.getInstance("ssl");
//            sslCtx.init(null, new TrustManager[] {new TrustAllTrustManager()}, null);
//            options.setProperty(HTTPConstants.CUSTOM_PROTOCOL_HANDLER,
//                    new Protocol("https",(ProtocolSocketFactory)new SSLProtocolSocketFactory(sslCtx),443));


            serviceClient.setOptions(options);

            OMElement result = serviceClient.sendReceive(request);
            if (result != null) {
                response = result.toString();
                Logger.debug(response);
            }
        } catch (Exception e) {
            Logger.debug("Cleanup Error .",
                    e);
            e.printStackTrace();
            throw new I2B2Exception("" + StackTraceUtil.getStackTrace(e));
        } finally {
            if (serviceClient != null) {
                try{
                    serviceClient.cleanupTransport();
                    serviceClient.cleanup();
                } catch (AxisFault e) {
                    Logger.debug("Error .", e);
                }
            }
        }


        return response;
    }
}
