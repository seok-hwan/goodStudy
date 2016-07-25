package spectra.ee.proxy.controller;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import spectra.ee.proxy.ProxyConfig;
import spectra.ee.proxy.ProxyException;
import spectra.ee.proxy.http.HttpStatus;
import spectra.ee.proxy.httpclient.IProxyHttpClientResult;
import spectra.ee.proxy.httpclient.ProxyHttpClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProxyJsonpServlet extends ProxyBaseServlet
{
	private static final long serialVersionUID = 6436409929738369258L;
    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();
	private final static Logger logger = Logger.getLogger(clazz);
	
	/* 
	 * 다음과 같이 단계별로 처리를 진행합니다.

        1.  Request/response를 이용하여 초기화된 dataMap을 만듬(initDataMap)
        2.  초기화된 dataMap/request Header를 이용하여 각 고객사별 선처리 작업을 진행(processBeforeSetParamMap)
        3.  multipart여부를 검사하여 각 타입에 맞게 parameter값을 dataMap에 세팅(getParameter)
        4.  parameter값이 세팅된 dataMap을 이용하여 각 고객사별로 선처리 작업을 진행(processAfterSetParamMap)
        5.  API 서버에 해당 데이터를 전송하고 결과 값을 받아 되돌려줌(callApi)
        6.  API호출이 끝난후 후처리에 필요한 부분을 처리(processAfterCallApi)
        
        위와 같은 작업외에도 각 단계별 문제시 발생된 ProxyException을 통합 처리함.
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException 
	{
	    Map dataMap = null;
        
        try {
            dataMap = initDataMap(req, res);
            if (ServletFileUpload.isMultipartContent(req))
            {
                throw new ProxyException("error : do not allow multipart request");
            }

            // request header를 이용한 선 처리
            processBeforeSetParamMap(req, res, dataMap);

            // 파라메터 세팅
            setParamMap(req, dataMap, false);

            // 파라메터를 이용한 후 처리
            processAfterSetParamMap(req, res, dataMap);
            
            final String callback = (String)dataMap.get(ProxyHttpClient.REQUEST_PARAM_CALLBACK);
            if (callback == null || "".equals(callback.trim()))
            {
                throw new ProxyException("error : parameter[cb] is required");
            }
            
            // API 호출
            ProxyHttpClient.callApi(req, res, (String) dataMap.get("URL"), dataMap, false, new IProxyHttpClientResult() {
                @Override
                public void processResult(HttpServletResponse res, String callUrl, String charset, int statusCode, InputStream is) 
                        throws IOException, UnsupportedEncodingException
                {
                    if (res.getContentType() == null || res.getContentType().isEmpty())
                    {
                        res.setContentType("text/plain; charset=" + charset);    
                    }
                    
                    if(statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_PARTIAL_CONTENT)
                    {
                        BufferedWriter out = new BufferedWriter(res.getWriter());
                        BufferedReader in = new BufferedReader(new InputStreamReader(is));
                        try
                        {
                            String read = null;
                            out.write("(function(cb){");
                            out.write(" var data = ");
                            while ((read = in.readLine()) != null)
                            {
                                out.write(read);
                            }
                            out.write(";");
                            out.write(" window[cb](data);");
                            out.write("})('" + callback + "');");
                            out.flush();
                        }
                        finally
                        {
                            is.close();
                            in.close();
                            out.close();
                        }
                    }
                    else
                    {
                        Writer out = new PrintWriter(new OutputStreamWriter(res.getOutputStream(), charset), true);
                        out.write(MessageFormat.format("[HttpStatus] : {0}, callURL : {1}", statusCode, callUrl));
                    }
                }
            });
        } 
        catch (ProxyException e) 
        {           
            logger.error(e.getMessage(), e);
            flushProxyError(res, getErrMsg(e));
        } 
        catch (Exception e) 
        {
            logger.error(e.getMessage(), e);

            StringBuffer sb = new StringBuffer();
            sb.append("{'errorCode':-39999,'errorMessage':'").append(e.getMessage()).append("'}");
            flushProxyError(res, sb.toString());
        } 
        finally 
        {
            // API 호출 후 처리 
            processAfterCallApi(req, res, dataMap);
        }
	}
	
	/* 
	 * jsonp의 경우 무조건 get만 허용
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException 
	{
	    doGet(req,res);
	}
	
	
	/**
	 * 초기화된 dataMap/request Header를 이용하여 각 고객사별 선처리 작업을 진행
	 * 이 프로세스는 request로부터 파라메터를 가져오기 전 과정이기 때문에 
	 * header및 cookie값을 이용하여 처리 할 부분만을 대상으로 하며 처리된 값은 dataMap에 세팅해주어야 한다.
	 * @param req
	 * @param res
	 * @param dataMap
	 */
	protected void processBeforeSetParamMap(HttpServletRequest req, HttpServletResponse res, Map dataMap)
	{
		// 여기에 고객사별 코드를 작성한다.
	}
	
	/**
	 * 고객사 별로 Parameter를 Map으로 세팅후의 처리 할수 있는 로직을 추가한다.
	 * @param req
	 * @param res
	 * @param dataMap
	 */
	protected void processAfterSetParamMap(HttpServletRequest req, HttpServletResponse res, Map dataMap) 
	{
	    Map paramMap = (Map)dataMap.get(ProxyHttpClient.REQUEST_PARAM_MAP);

	    dataMap.put(ProxyHttpClient.REQUEST_PARAM_CALLBACK, paramMap.get(ProxyHttpClient.REQUEST_PARAM_CALLBACK));
	    
		// cmd에 쌓여 오는 경우 cmd 내용을 꺼내서 paramMap에 넣어 준다.
	    if(null != paramMap.get(ProxyHttpClient.REQUEST_PARAM_CMD))
        {
            paramMap = (Map)paramMap.get(ProxyHttpClient.REQUEST_PARAM_CMD);
        }
	    
		// 세션에서 값을 가져와 세팅하는 부분을 추가한다.
		String sessionParam = ProxyConfig.getConf("SESSION_SET","");
		if(sessionParam==null || "".equals(sessionParam))
		{
			return;
		}
		
		String[] paramKeys = sessionParam.split(",");
		for (int i = 0, ilen = paramKeys.length; i < ilen; i++)
		{
			List list = Arrays.asList(paramKeys[i].split("[|]"));
			String value = (String) req.getSession().getAttribute((String) list.get(0));
			String defValue = list.size() < 3 ? "" : ProxyConfig.getDefaultStr((String) list.get(2), "");
			paramMap.put((String)list.get(1), ProxyConfig.getDefaultStr(value, defValue));
		}
	}
	
	/**
	 * 고객사 별로 CallApi처리후 로직을 추가한다.
	 * 
	 * @param req
	 * @param res
	 * @param dataMap
	 */
	protected boolean processAfterCallApi(HttpServletRequest req, HttpServletResponse res, Map dataMap) 
	{
	    return true;
	}
}
