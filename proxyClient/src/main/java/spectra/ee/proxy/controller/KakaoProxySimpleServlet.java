package spectra.ee.proxy.controller;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import spectra.ee.proxy.ProxyConfig;
import spectra.ee.proxy.ProxyException;
import spectra.ee.proxy.http.HttpStatus;
import spectra.ee.proxy.httpclient.IProxyHttpClientResult;
import spectra.ee.proxy.httpclient.ProxyHttpClient;
import spectra.ee.proxy.lang.time.FastDateFormat;
import spectra.ee.proxy.util.FileDownloadUtil;
import spectra.ee.proxy.util.ProxyUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.ConnectException;
import java.util.*;

public class KakaoProxySimpleServlet extends ProxyBaseServlet
{
    /**
     * Serializable UID
     */
    private static final long serialVersionUID = 7721019571764394697L;

    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

//    private static final String PARAM_CMD = "cmd";

    private static final String PARAM_TOKEN = "token";

    private static final String PARAM_PLUS_USER_KEY = "plus_user_key";

//    private static final String PARAM_COUNTRY_ISO = "country_iso";

    private static final String PARAM_MESSAGE = "message";

    private static final String PARAM_ATTACHMENT = "attachment";

    private static final String PARAM_EVENT = "event";

    private static final String PARAM_ERROR = "error";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
    {
        doPost(req, res);
    }

    /*
     * 다음과 같이 단계별로 처리를 진행합니다. 
     * 1. Request/response를 이용하여 초기화된 dataMap을 만듬(initDataMap) 
     * 2. 초기화된 dataMap/request Header를 이용하여 각 고객사별 선처리 작업을 진행(processBeforeSetParamMap) 
     * 3. multipart여부를 검사하여 각 타입에 맞게 parameter값을 dataMap에 세팅(setParamMap) 
     * 4. parameter값이 세팅된 dataMap을 이용하여 각 고객사별로 선처리 작업을 진행(processAfterSetParamMap) 
     * 5. API 서버에 해당 데이터를 전송하고 결과 값을 받아 되돌려줌(callApi) 
     * 6. API호출이 끝난후 후처리에 필요한 부분을 처리(processAfterCallApi) 위와 같은 작업외에도 각 단계별 문제시 발생된 ProxyException을 통합 처리함.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
    {
        try
        {
            // res.setContentType("application/json; charset=utf-8");
            // res.setCharacterEncoding(getConf("KAKAO_ENCODING"));
            HashMap dataMap = initDataMap(req, res);
            boolean isMultipart = false;

            // request header를 이용한 선 처리
            processBeforeSetParamMap(req, res, dataMap, isMultipart);
            
            // 파라메터 세팅
            setParamMap(req, dataMap, isMultipart);

            HashMap paramMap = (HashMap) dataMap.get(ProxyHttpClient.REQUEST_PARAM_MAP);
            HashMap paramCmdMap = (HashMap) paramMap.get(ProxyHttpClient.REQUEST_PARAM_CMD);
            if (paramCmdMap.get(PARAM_ATTACHMENT) != null)
            {
                isMultipart = true;
            }

            // 파라메터를 이용한 후 처리
            processAfterSetParamMap(req, res, dataMap, isMultipart);

            // API 호출
            ProxyHttpClient.callApi(req, res, (String) dataMap.get("URL"), dataMap, isMultipart, new IProxyHttpClientResult() {
				@Override
				public void processResult(HttpServletResponse res, String callUrl, String charset, int statusCode, InputStream is) 
						throws IOException, UnsupportedEncodingException
				{
		            if (statusCode == HttpStatus.SC_OK)
		            {
		            	BufferedReader in = new BufferedReader(new InputStreamReader(is, ProxyConfig.getConf("KAKAO_ENCODING")));
		            	try
		            	{
			                StringBuilder reponseMsg = new StringBuilder();
			                while (true)
			                {
			                    String line = in.readLine();
			                    if (line == null)
			                    {
			                        break;
			                    }
			                    reponseMsg.append(line);
			                }

			                KakaoResponseBean kakaoResponseBean = convertKakaoResponseBean(reponseMsg.toString());
			                writeForKakao(res, kakaoResponseBean);
		            	}
		            	finally
		            	{
		            		in.close();
		            	}
		            }
		            else
		            {
		                KakaoResponseBean kakaoResponseBean = new KakaoResponseBean();
		                kakaoResponseBean.setAvailable(false);
		                kakaoResponseBean.setMessage("[HttpStatus] : " + statusCode + ", callURL : " + callUrl);
		                writeForKakao(res, kakaoResponseBean);
		            }					
				}
            });

            // API 호출 후 처리
            processAfterCallApi(req, res, dataMap, isMultipart);
        }
        catch (ProxyException e)
        {
            flushProxyError(res, getErrMsg(e));
        }
        catch (Exception e)
        {
            flushProxyError(res, e.getMessage());
        }
    }

    /**
     * Kakao Proxy서버상에서 발생하는 오류의 메세지를 처리한다. (API서버측에서 발생한 오류는 API서버측에서 오류메세지 형식을 만들어 리턴해준다.)
     * 
     * @param e
     * @return
     */
    protected String getErrMsg(ProxyException e)
    {
        Throwable tr = e.getCause();
        StringBuffer sb = new StringBuffer();
        if (tr instanceof ConnectException)
        {
            sb.append("It is an error in the communication server API : [").append(tr.getMessage()).append("]");
        }
        else
        {
            sb.append(e.getMessage());
        }
        return sb.toString();
    }

    /**
     * Proxy서버상에서 발생한 에러메세지를 response에 반환한다.
     * 
     * @param res
     * @param message
     */
    protected void flushProxyError(HttpServletResponse res, String message)
    {
        logger.error("flushProxyError text : " + message);
        KakaoResponseBean kakaoResponseBean = new KakaoResponseBean();
        kakaoResponseBean.setAvailable(false);
        kakaoResponseBean.setMessage(message);
        writeForKakao(res, kakaoResponseBean);
    }

    /**
     * DataMap 초기화. Parameter와 Cookie값을 하나의 Map에 넣어 Request의 상태값을 하나의 통합 Map으로 유지함. ProxySimpleServlet 프로세스 전반에 걸쳐
     * 처리데이터를 가지는 DataMap을 생성 및 초기화 하는 작업을 함. (parameter의 경우 setParamMap 메소드에서 별도로 초기화.)
     * 
     * @param req
     * @param res
     * @return
     */
    protected HashMap initDataMap(HttpServletRequest req, HttpServletResponse res)
    {
        HashMap dataMap = new HashMap();
        dataMap.put(ProxyHttpClient.REQUEST_PARAM_MAP, new HashMap());
        
        StringBuffer addParam = new StringBuffer();
        String domainId = getDomainIdFromRequestURI(req.getRequestURI(), ProxyConfig.getConf("KAKAO_DOMAIN_ID"));
        String serviceType = ProxyUtil.defaultIfBlank(req.getParameter("serviceType"), ProxyConfig.getConf("KAKAO_SERVICE_TYPE"));
        
        if(domainId != null && !"".equals(domainId))
        {
            addParam.append("?domainId=" + domainId);
        }

        if(serviceType != null && !"".equals(serviceType))
        {
            addParam.append(addParam.toString().isEmpty() ? "?" : "&");
            addParam.append("serviceType=" + serviceType);
        }
        
        dataMap.put("URL", ProxyConfig.getConf("API_URL") + addParam.toString());

        return dataMap;
    }

    /**
     * 초기화된 dataMap/request Header를 이용하여 각 고객사별 선처리 작업을 진행 이 프로세스는 request로부터 파라메터를 가져오기 전 과정이기 때문에 header및 cookie값을
     * 이용하여 처리 할 부분만을 대상으로 하며 처리된 값은 dataMap에 세팅해주어야 한다.
     * 
     * @param req
     * @param res
     * @param isMultipart
     */
    protected void processBeforeSetParamMap(HttpServletRequest req, HttpServletResponse res, HashMap dataMap, boolean isMultipart)
    {
        // 여기에 고객사별 코드를 작성한다.
    }

    /**
     * 파라메터를 Map에 세팅한다. 이 메소드는 setParameter메소드와 형식을 맞춰야 한다.
     * 
     * @param req
     * @param dataMap
     * @param isMultipart
     */
    protected void setParamMap(HttpServletRequest req, HashMap dataMap, boolean isMultipart)
    {
        try
        {
            req.setCharacterEncoding("UTF-8");
            
            HashMap paramMap = (HashMap) dataMap.get(ProxyHttpClient.REQUEST_PARAM_MAP);
            paramMap.put("authId", ProxyConfig.getConf("AUTH_ID"));
            
            // 카카오 서버로부터 넘어온 JSON 파라미터 값 설정
            String paramJsonString = getJSONString(req);    //.replaceAll("[+]", "_X_X_");
            HashMap<String, String> paramJsonMap = new Gson().fromJson(paramJsonString, HashMap.class);
            if(paramJsonMap == null) 
            {
                paramJsonMap = new HashMap();
            }
            
            // URL 패턴에서 추출한 노드아이디 설정
            paramJsonMap.put("domainId", getDomainIdFromRequestURI(req.getRequestURI(), ProxyConfig.getConf("KAKAO_DOMAIN_ID")));
            paramJsonMap.put("nodeId", getNodeIdFromRequestURI(req.getRequestURI(), ProxyConfig.getConf("KAKAO_NODE_ID")));
            
            // properties 에 설정된 서비스타입 설정
            paramJsonMap.put("serviceType", ProxyUtil.defaultIfBlank(req.getParameter("serviceType"), ProxyConfig.getConf("KAKAO_SERVICE_TYPE")));
            
            logger.debug(paramJsonMap);

            // 카카오 파라미터 validation
            if (!isValidParam(paramJsonMap))
            {
                throw new ProxyException("Valid required parameters are missed.");
            }

            // 3. 고정 파라미터 값 설정
            paramMap.put("channel", "KAKAO"); // 카카오로부터 전달되었다는 파라미터 추가
            paramMap.put(ProxyHttpClient.REQUEST_PARAM_CMD, paramJsonMap);
        }
        catch (Exception e)
        {
            throw new ProxyException(e);
        }
    }

    /**
     * 고객사 별로 Parameter를 Map으로 세팅후의 처리 할수 있는 로직을 추가한다.
     * 
     * @param req
     * @param res
     * @param dataMap
     * @param isMultipart
     */
    protected void processAfterSetParamMap(HttpServletRequest req, HttpServletResponse res, HashMap dataMap, boolean isMultipart)
    {
        if (isMultipart)
        {
            HashMap paramMap = (HashMap) dataMap.get(ProxyHttpClient.REQUEST_PARAM_MAP);
            HashMap paramCmdMap = (HashMap) paramMap.get(ProxyHttpClient.REQUEST_PARAM_CMD);
            
            // 첨부파일 처리
            try
            {
                long startTime = System.currentTimeMillis();
                logger.info("startTime = " + format(new Date(), "HH:mm:ss:SSS", null, null));
                
                Hashtable fileTable = FileDownloadUtil.fileDownLoad((String) paramCmdMap.get(PARAM_ATTACHMENT), ProxyConfig.getConf("UPLOAD_HOME"));

                long endTime = System.currentTimeMillis();
                logger.info("endTime = " + format(new Date(), "HH:mm:ss:SSS", null, null));
                logger.info("diff = " + (endTime - startTime));
                
                String saveFileFullPath = (String) fileTable.get("savePath");
                String strFileName = (String) fileTable.get("fileName");
                long lFileSize = ((Long) fileTable.get("fileSize")).longValue();

                Map fileInfo = new HashMap();
                fileInfo.put("fileName", strFileName);
                fileInfo.put("savePath", saveFileFullPath);
                fileInfo.put("fileSize", lFileSize);

                paramMap.put(ProxyHttpClient.REQUEST_PARAM_FILE_INFO_MAP, fileInfo);
            }
            catch (Exception e)
            {
                throw new ProxyException(e);
            }
        }
    }

    /**
     * 고객사 별로 CallApi처리후 로직을 추가한다.
     * 
     * @param req
     * @param res
     * @param dataMap
     * @param isMultipart
     */
    protected void processAfterCallApi(HttpServletRequest req, HttpServletResponse res, HashMap dataMap, boolean isMultipart)
    {
        HashMap paramMap = (HashMap)dataMap.get(ProxyHttpClient.REQUEST_PARAM_MAP);
        HashMap fileInfo = (HashMap)paramMap.get(ProxyHttpClient.REQUEST_PARAM_FILE_INFO_MAP);
        
        if( fileInfo != null && isMultipart )
        {
            // 임시로 올렸던 파일을 삭제해준다.
            String savePath = (String)fileInfo.get("savePath");
            File f = new File(savePath!=null?savePath:"");
            if(f.exists())
            {
                f.delete();
            }
        }
    }

    private String getJSONString(HttpServletRequest request) throws IOException
    {
        StringBuffer buf = new StringBuffer();

        BufferedReader br = null;
        try
        {
            br = request.getReader();

            String str = null;

            while ((str = br.readLine()) != null)
            {
                buf.append(str);
            }

            return buf.toString();

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
    }

    private boolean isValidParam(HashMap paramMap)
    {
        String token = (String) paramMap.get(PARAM_TOKEN);
        if (token == null || token.isEmpty())
        {
            return false;
        }
        
        String plusUserKey = (String) paramMap.get(PARAM_PLUS_USER_KEY);
        if (plusUserKey == null || plusUserKey.isEmpty())
        {
            return false;
        }
        
        String message = (String) paramMap.get(PARAM_MESSAGE);
        String attachment = (String) paramMap.get(PARAM_ATTACHMENT);
        String event = (String) paramMap.get(PARAM_EVENT);
        String error = (String) paramMap.get(PARAM_ERROR);
        if (message == null && attachment == null && event == null && error == null)
        {
            return false;
        }
        return true;
    }
    
    private String getDomainIdFromRequestURI(String requestURI, String defaultValue)
    {
        int sIndex = requestURI.lastIndexOf("/") + 1;
        String result = "";
        try 
        {
            result = requestURI.substring(sIndex, sIndex + 29).split("_")[0]; // 도메인아이디_노드아이디
        }
        catch(Exception e)
        {
            result = defaultValue;
        }
        
        return result;
    }
    
    private String getNodeIdFromRequestURI(String requestURI, String defaultValue)
    {
        int sIndex = requestURI.lastIndexOf("/") + 1;
        String result = "";
        try 
        {
            result = requestURI.substring(sIndex, sIndex + 29).split("_")[1]; // 도메인아이디_노드아이디
        }
        catch(Exception e)
        {
            result = defaultValue;
        }
        
        return result;
    }
    
    /**
     * RestApi 의 응답결과를 카카오 응답에 맞도록 변환하는 함수
     * 단, errorCode 가 성공(0) 이 아닌 경우는 모두 실패여야 하지만, 
     * 실패 응답을 보내게 되면 상담도 종료되게 된다.
     * 예외로 첨부할 수 없는 확장자, 첨부파일 용량초과, 첨부파일 허용불가는 성공으로 응답을 보낸다. 
     * 
     * @param apiResponse
     * @return
     */
    private KakaoResponseBean convertKakaoResponseBean(String apiResponse)
    {
        Gson gson = new Gson();
        HashMap map = gson.fromJson(apiResponse, HashMap.class);
        int errorCode = ((Number) map.get("errorCode")).intValue();
        String errorMessage = (String) map.get("errorMessage");
        
        
        String lastMessage = null;
        try
        {
            Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
            if (dataMap != null)
            {
                lastMessage = (String) dataMap.get("lastMessage");
            }
        }
        catch(Exception e)
        {
            logger.error(e.getMessage());
        }
        logger.debug("errorCode : " + errorCode + " , errorMessage : " + errorMessage + ", lastMessage : " + lastMessage);
        
        List availableErrorCodeList = new ArrayList();
        availableErrorCodeList.add(0);      // 성공
        availableErrorCodeList.add(-1006);  // 첨부할 수 없는 확장자
        availableErrorCodeList.add(-1007);  // 첨부파일 용량초과
        availableErrorCodeList.add(-1026);  // 첨부파일 허용불가
        availableErrorCodeList.add(1009);   // 상담 인입 차단안함(상담인입안내 메세지 사용)
        
        boolean available = availableErrorCodeList.contains(errorCode);
        KakaoResponseBean kakaoResponseBean = new KakaoResponseBean();
        kakaoResponseBean.setAvailable(available);
        kakaoResponseBean.setMessage(lastMessage != null ? lastMessage : errorMessage);
        kakaoResponseBean.setText(lastMessage != null ? lastMessage : errorMessage);
        
        return kakaoResponseBean;
    }

    private void writeForKakao(HttpServletResponse response, Object model)
    {
        BufferedOutputStream bout = null;

        try
        {
            Gson gson = new Gson();
            bout = new BufferedOutputStream(response.getOutputStream());
            byte[] b = gson.toJson(model).getBytes(ProxyConfig.getConf("KAKAO_ENCODING"));

            int contentLength = 0;

            if (b != null)
            {
                contentLength = b.length;
            }

            response.setContentLength(contentLength);
            
            bout.write(b);
            bout.flush();
        }
        catch (IOException ioe)
        {
            logger.error(ioe);
            throw new ProxyException(ioe);
        }
        finally
        {
            if (bout != null)
            {
                try
                {
                    bout.close();
                }
                catch (IOException e)
                {
                    logger.error(e);
                }
            }
        }
    }

    private String format(Date date, String pattern, TimeZone timeZone, Locale locale) {
        FastDateFormat df = FastDateFormat.getInstance(pattern, timeZone, locale);
        return df.format(date);
    }


    public class KakaoResponseBean
    {
        private boolean available;

        private String message;
        
        private String text;

        public boolean isAvailable()
        {
            return available;
        }

        public void setAvailable(boolean available)
        {
            this.available = available;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }

        public String toString()
        {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
}