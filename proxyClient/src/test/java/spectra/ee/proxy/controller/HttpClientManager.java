package spectra.ee.proxy.controller;

import org.apache.commons.io.IOUtils;
import spectra.ee.proxy.http.HttpEntity;
import spectra.ee.proxy.http.HttpStatus;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.methods.CloseableHttpResponse;
import spectra.ee.proxy.http.client.methods.HttpGet;
import spectra.ee.proxy.http.client.protocol.HttpClientContext;
import spectra.ee.proxy.http.conn.ssl.AllowAllHostnameVerifier;
import spectra.ee.proxy.http.conn.ssl.SSLConnectionSocketFactory;
import spectra.ee.proxy.http.conn.ssl.SSLContexts;
import spectra.ee.proxy.http.conn.ssl.TrustSelfSignedStrategy;
import spectra.ee.proxy.http.impl.client.CloseableHttpClient;
import spectra.ee.proxy.http.impl.client.HttpClientBuilder;
import spectra.ee.proxy.http.impl.conn.PoolingHttpClientConnectionManager;
import spectra.ee.proxy.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HttpClientManager
{

    private static volatile HttpClientBuilder builder;

    protected String httpCall(String callUrl, int timeout)
    {
        String responseContents = "";
        CloseableHttpResponse response = null;
        try
        {
            CloseableHttpClient httpClient = getCloseableHttpClient(callUrl.startsWith("https"), timeout);
            HttpContext context = HttpClientContext.create();
            HttpGet httpget = new HttpGet(callUrl);
            response = httpClient.execute(httpget, context);
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_PARTIAL_CONTENT)
            {
                try
                {
                    InputStreamReader reader = new InputStreamReader(is);
                    byte[] buffer = IOUtils.toByteArray(reader);
                    responseContents = new String(buffer, "utf-8");
                }
                finally
                {
                    is.close();
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            releaseConnection(response);
        }
        return responseContents;
    }

    private void releaseConnection(Closeable closable)
    {
        try
        {
            if (closable != null)
            {
                closable.close();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static CloseableHttpClient getCloseableHttpClient(boolean isHttps, int timeout)
    {
        if (builder == null) // NOPMD
        {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(timeout)
                    .setConnectionRequestTimeout(timeout)
                    .setSocketTimeout(timeout)
                    .build();

            builder = HttpClientBuilder.create().setDefaultRequestConfig(config);

            PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
            connMgr.setMaxTotal(50);
            connMgr.setDefaultMaxPerRoute(20);

            builder = builder.setConnectionManager(connMgr);
        }

        return isHttps ?
                builder.setSSLSocketFactory(getSSLSocketFactory()).build() :
                builder.build();
    }

    private static SSLConnectionSocketFactory getSSLSocketFactory()
    {
        SSLConnectionSocketFactory connectionFactory = null;
        try
        {
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
            connectionFactory = new SSLConnectionSocketFactory(sslContext, new AllowAllHostnameVerifier());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return connectionFactory;
    }
}
