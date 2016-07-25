package spectra.ee.proxy.httpclient;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import spectra.ee.proxy.ProxyConfig;
import spectra.ee.proxy.ProxyException;
import spectra.ee.proxy.http.Header;
import spectra.ee.proxy.http.HttpEntity;
import spectra.ee.proxy.http.HttpHeaders;
import spectra.ee.proxy.http.NameValuePair;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.entity.UrlEncodedFormEntity;
import spectra.ee.proxy.http.client.methods.CloseableHttpResponse;
import spectra.ee.proxy.http.client.methods.HttpPost;
import spectra.ee.proxy.http.conn.HttpClientConnectionManager;
import spectra.ee.proxy.http.conn.ssl.AllowAllHostnameVerifier;
import spectra.ee.proxy.http.conn.ssl.SSLConnectionSocketFactory;
import spectra.ee.proxy.http.conn.ssl.SSLContexts;
import spectra.ee.proxy.http.conn.ssl.TrustSelfSignedStrategy;
import spectra.ee.proxy.http.entity.ContentType;
import spectra.ee.proxy.http.entity.mime.HttpMultipartMode;
import spectra.ee.proxy.http.entity.mime.MultipartEntityBuilder;
import spectra.ee.proxy.http.impl.client.CloseableHttpClient;
import spectra.ee.proxy.http.impl.client.HttpClientBuilder;
import spectra.ee.proxy.http.impl.conn.PoolingHttpClientConnectionManager;
import spectra.ee.proxy.http.message.BasicHeader;
import spectra.ee.proxy.http.message.BasicNameValuePair;
import spectra.ee.proxy.http.message.HeaderGroup;
import spectra.ee.proxy.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ProxyHttpClient
{
	private final static Logger logger = Logger.getLogger(new Object() {/**/}.getClass().getEnclosingClass());
	
	public static final String REQUEST_PARAM_MAP = "paramMap";
    public static final String REQUEST_PARAM_FILE_INFO_MAP = "fileInfoMap";
    public static final String REQUEST_PARAM_CMD = "cmd";
    public static final String REQUEST_PARAM_CALLBACK = "cb";   // jsonp 타입일 경우 사용되는 callback

    private static boolean connectPerRequest = true;
    private static volatile HttpClientBuilder builder;
	/**
	 * These are the "hop-by-hop" headers that should not be copied.
	 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html I use an
	 * HttpClient HeaderGroup class instead of Set<String> because this approach
	 * does case insensitive lookup faster.
	 */
    protected static final HeaderGroup hopByHopReqHeaders;
    protected static final HeaderGroup hopByHopResHeaders;
    
    static {
        hopByHopReqHeaders = new HeaderGroup();
        String[] reqHeaders = new String[] { 
                "Connection", "Keep-Alive", "Proxy-Authenticate", 
                "Proxy-Authorization", "TE", "Trailers", 
                "Transfer-Encoding", "Upgrade", "Content-Type", "Cookie", "Host"};
        for (String header : reqHeaders) {
            hopByHopReqHeaders.addHeader(new BasicHeader(header, null));
        }

        hopByHopResHeaders = new HeaderGroup();
        String[] resHeaders = new String[] { 
                "Connection", "Keep-Alive", "Proxy-Authenticate", 
                "Proxy-Authorization", "TE", "Trailers", 
                "Transfer-Encoding", "Upgrade"};
        for (String header : resHeaders) {
            hopByHopResHeaders.addHeader(new BasicHeader(header, null));
        }
	}

	/**
	 * 	API 서버에 해당 데이터를 전송하고 결과 값을 받아 되돌려줌
		HttpClient를 이용하여 호출하며 전송 결과 값을 받아 response에 뿌려준다.
		API 서버 호출 상태에 따라 별도의 오류 메시지를 뿌리기도 하며 그 외에 Exception 발생시 ProxyException을 상위에 던져준다.
		
		이 메소드 안에서는 다음과 같이 주요 메소드 2개가 호출된다.
		
		- setParameter : parameter값을 호출하는 HttpClient에 세팅해준다.
		- setResponse : response를 flush하기 이전에 response에 추가적으로 넣을 값을 세팅할 수 있다.

	 * @param req
	 * @param res
	 * @param callUrl
	 * @param dataMap
	 * @param isMultipart
	 */
	public static void callApi(HttpServletRequest req, HttpServletResponse res, String callUrl, Map<String, Object> dataMap, boolean isMultipart, IProxyHttpClientResult result)
	{
		boolean debug = ProxyConfig.getConf("DEBUG_FLAG").equals("Y");

	    if(debug)
	    {
	        logger.debug("param dataMap : " + dataMap);
	    }

		String charset = ProxyConfig.getConf("ENCODING");
		int timeout = ProxyConfig.getConfInt("TIMEOUT");

		CloseableHttpClient httpclient = null;
        try
        {
        	httpclient = getCloseableHttpClient(callUrl.startsWith("https"), timeout);

            HttpPost httpPost = new HttpPost(callUrl);
            
            // set request header
            setRequestHeader(req, httpPost);

            // set request body
            setRequestParameter(httpPost, dataMap, isMultipart, charset);
            
            // execute
            CloseableHttpResponse response = httpclient.execute(httpPost);
            
            // set response header
            setResponseHeader(res, response);

            try
            {
            	int statusCode = response.getStatusLine().getStatusCode();
                if(debug)
                {
    	            logger.debug("result statusCode : " + statusCode);
                }

        		HttpEntity entity = response.getEntity();

        		result.processResult(res, callUrl, charset, statusCode, entity.getContent());

                // do something useful with the response body
                // and ensure it is fully consumed
                EntityUtils.consume(entity);
            }
            finally
            {
            	releaseConnection(response);
            }
        }
        catch (Exception e)
        {            
        	throw new ProxyException(e);
        }
        finally
        {
            if (connectPerRequest)
            {
                releaseConnection(httpclient);  
            }   
        }
	}


	/**
	 * HttpServletRequest의 Header를 HttpPost 에 셋팅한다. 
	 */
	private static void setRequestHeader(HttpServletRequest req, HttpPost httpPost)
	{
		// Get an Enumeration of all of the header names sent by the client
		Enumeration<String> enumerationOfHeaderNames = req.getHeaderNames();
		while (enumerationOfHeaderNames.hasMoreElements()) {
			String headerName = (String) enumerationOfHeaderNames.nextElement();

			// Instead the content-length is effectively set via InputStreamEntity
			if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
			{
				continue;
			}
			
			if (hopByHopReqHeaders.containsHeader(headerName))
			{
				continue;
			}

			Enumeration<String> headers = req.getHeaders(headerName);
			while (headers.hasMoreElements()) {// sometimes more than one value
				String headerValue = (String) headers.nextElement();
				httpPost.addHeader(headerName, headerValue);
			}
		}
		
		setCustomizingParameters(req, httpPost);
		
	}
	
	//고객사의 내부 연계 parameter를 셋팅하여 넘긴다.
	private static void setCustomizingParameters(HttpServletRequest req, HttpPost httpPost)
	{
	    httpPost.addHeader("eccx-customer_ip", getCustomerIp(req));
	}

	/**
	 * 고객의 IP를 얻는다. 
	 */
	private static String getCustomerIp(HttpServletRequest req)
	{
		String[] headerNames = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
		boolean found = false;
		String ip = null;
		
		for (String headerName: headerNames)
		{
			ip = req.getHeader(headerName);
			if (ip != null && ip.length() > 0 && !"unknown".equalsIgnoreCase(ip))
			{
				found = true;
				break;
			}
		}

		return found ? ip : req.getRemoteAddr();
	}
	
	/**
	 * 호출 결과 헤더를 셋팅한다.
	 */
	private static void setResponseHeader(HttpServletResponse res, CloseableHttpResponse response)
	{
		Header[] headers = response.getAllHeaders();
		if (headers != null)
		{	
	        for (Header e: headers)
	        {
	        	if (hopByHopResHeaders.containsHeader(e.getName()))
				{
					continue;
				}

	        	res.setHeader(e.getName(), e.getValue());
	        }
		}
	}


	/**
	 * 파라메터/쿠키를 PostMethod에 추가한다.
	 * Multipart일 경우 임시저장한 파일을 추가한다.
	 * 이 메소드는 getParameter메소드와 형식을 맞춰야 한다.
	 * @param httpPost
	 * @param dataMap
	 * @param isMultipart
	 * @param charset
	 * @throws UnsupportedEncodingException 
	 */
	private static void setRequestParameter(HttpPost httpPost, Map<String, Object> dataMap, boolean isMultipart, String charset) throws UnsupportedEncodingException
	{
        if(dataMap == null || dataMap.isEmpty())
        {
            return;
        }
        
        Map<String, Object> paramMap = (Map<String, Object>) dataMap.get(REQUEST_PARAM_MAP);

        if(isMultipart)
        {
        	MultipartEntityBuilder meb = MultipartEntityBuilder.create();
        	meb.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        	meb.setCharset(Charset.forName(charset));
        	
        	for (Map.Entry<String, Object> entry: paramMap.entrySet())
        	{
        		String key = entry.getKey();
        		Object value = entry.getValue();
                
                if(REQUEST_PARAM_FILE_INFO_MAP.equals(key))
                {
                	Map<String, String> fileInfoMap = (Map<String, String>) value;
                	meb.addBinaryBody("file", new File(fileInfoMap.get("savePath")), ContentType.APPLICATION_OCTET_STREAM, URLEncoder.encode(fileInfoMap.get("fileName"), charset));
                	continue;
                }
                else if(REQUEST_PARAM_CMD.equals(key))
                {
                	meb.addTextBody(REQUEST_PARAM_CMD, new Gson().toJson((HashMap) value, HashMap.class), ContentType.create("text/plain", Charset.forName(charset)));
                	continue;
                }                
                else if(value instanceof String)
                {
                	meb.addTextBody(key, (String) value, ContentType.create("text/plain", Charset.forName(charset)));
                }
        	}
        	
        	HttpEntity me = meb.build();
        	httpPost.setEntity(me);
        }
        else
        {
        	httpPost.setEntity(new UrlEncodedFormEntity(getNameValueFairList(paramMap), charset));
        }
	}
	
	/**
	 * getCloseableHttpClient
	 * 
	 * @throws KeyStoreException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyManagementException 
	 */
	private static CloseableHttpClient getCloseableHttpClient(boolean isHttps, int timeout) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException
	{
	    if (builder == null) // NOPMD
        {
	        synchronized (ProxyHttpClient.class)
	        {
	            if (builder == null)
	            {
	                RequestConfig config = RequestConfig.custom()
	                        .setConnectTimeout(timeout)
	                        .setConnectionRequestTimeout(timeout)
	                        .setSocketTimeout(timeout)
	                        .build();
	                
	                builder = HttpClientBuilder.create().setDefaultRequestConfig(config);

	                if (!connectPerRequest)
	                {
	                    PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
	                    connMgr.setMaxTotal(getSystemProperty("proxy.pool.maxTotal", 50));
	                    connMgr.setDefaultMaxPerRoute(getSystemProperty("proxy.pool.defaultMaxPerRoute", 20));
	                
	                    builder = builder.setConnectionManager(connMgr);
	                    
	                    IdleConnectionMonitorThread staleMonitor = new IdleConnectionMonitorThread(connMgr);
	                    staleMonitor.start();
	                }
	            }
	        }
        }

        return isHttps ? 
                builder.setSSLSocketFactory(getSSLSocketFactory()).build() :
                builder.build();
	}

	private static class IdleConnectionMonitorThread extends Thread
    {
        private final HttpClientConnectionManager connMgr;
        private volatile boolean shutdown;

        public IdleConnectionMonitorThread(PoolingHttpClientConnectionManager connMgr)
        {
            super();
            this.setDaemon(true);
            this.connMgr = connMgr;
        }

        @Override
        public void run()
        {
            int checkIntervalClose = getSystemProperty("proxy.pool.checkIntervalClose", 5000);
            try {
                while (!shutdown)
                {
                    synchronized (this)
                    {
                        wait(checkIntervalClose);
                        connMgr.closeExpiredConnections();
                        connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            }
            catch (InterruptedException ex)
            {
                shutdown();
            }
        }

        public void shutdown()
        {
            shutdown = true;
            synchronized (this)
            {
                notifyAll();
            }
        }
    }
    
    private static int getSystemProperty(String key, int defaultValue)
    {
        String value = System.getProperty(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

	/**
	 * SSL 사용 시 사설 인증서일 경우 예외 발생 방지룰 위해 
	 */
	private static SSLConnectionSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException
	{
		SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
		SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, new AllowAllHostnameVerifier());

		return connectionFactory;
	}
	
    /**
     * Map을 List<NameValuePair> 로 변환한다.
     */
	private static List<NameValuePair> getNameValueFairList(Map<String, Object> params)
	{
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		
		for (Map.Entry<String, Object> entry: params.entrySet())
		{
			String key = entry.getKey();
			Object value = entry.getValue();
			
		    if(REQUEST_PARAM_CMD.equals(key))
		    {
		    	value = new Gson().toJson((HashMap) value, HashMap.class);
		    }
			nvps.add(new BasicNameValuePair(entry.getKey(), (String) value));
		}
		return nvps;
	}
    
    /**
     * 자원을 해제한다.
     **/
    private static void releaseConnection(Closeable closable)
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
            logger.error("Exception occur !!! ", e);
        }
    }
}
