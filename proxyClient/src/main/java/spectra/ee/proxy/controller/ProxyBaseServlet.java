package spectra.ee.proxy.controller;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.log4j.Logger;

import spectra.ee.proxy.ProxyConfig;
import spectra.ee.proxy.ProxyException;
import spectra.ee.proxy.httpclient.ProxyHttpClient;
import spectra.ee.proxy.util.ProxyUtil;

import com.google.gson.Gson;

/**
 * 
 * @author jhkim
 */
public abstract class ProxyBaseServlet extends HttpServlet
{
    /**
     * Serializable UID
     */
    private static final long serialVersionUID = 872266690770957752L;
    
	private static final Logger logger = Logger.getLogger(new Object() {/**/}.getClass().getEnclosingClass());
	
	/**
     * Proxy서버상에서 발생하는 오류의 메세지를 처리한다.
     * (API서버측에서 발생한 오류는 API서버측에서 오류메세지 형식을 만들어 리턴해준다.)
     * @param e
     * @return
     */
    protected String getErrMsg(ProxyException e)
    {
        Throwable tr = e.getCause();
        StringBuffer sb = new StringBuffer();
        if(tr instanceof FileSizeLimitExceededException)
        {
            sb.append("{\"errorCode\":-32301,\"errorMessage\":\"").append("You have exceed the file upload limit.").append("\"}");
        }
        else if(tr instanceof ConnectException)
        {
            sb.append("{\"errorCode\":-32205,'errorMessage\":\"").append("It is an error in the communication server API : [").append(tr.getMessage()).append("]\"}");
        }
        else
        {
            sb.append("{\"errorCode\":-39999,\"errorMessage\":\"").append(e.getMessage()).append("\"}");
        }
        return sb.toString();
    }
    
    /**
     * Proxy서버상에서 발생한 에러메세지를 response에 반환한다.
     * @param res
     * @param message
     */
    protected void flushProxyError(HttpServletResponse res, String message)
    {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new OutputStreamWriter(res.getOutputStream(), ProxyConfig.getConf("ENCODING")), true);
            out.write(message);
            out.flush();
        }
        catch(Exception ex)
        {
            logger.error(ex);
        }
        finally
        {
            if (out != null)
            {
                out.close();
            }
        }
    }
    
    /**
     * DataMap 초기화. 
     * Parameter와 Cookie값을 하나의 Map에 넣어 Request의 상태값을 하나의 통합 Map으로 유지함.
     * ProxySimpleServlet 프로세스 전반에 걸쳐 처리데이터를 가지는 DataMap을 생성 및 초기화 하는 작업을 함.
     * (parameter의 경우 getParameter메소드에서 별도로 초기화.)
     * @param req
     * @param res
     * @return
     */
    protected HashMap initDataMap(HttpServletRequest req, HttpServletResponse res)
    {
        HashMap dataMap = new HashMap();
        dataMap.put(ProxyHttpClient.REQUEST_PARAM_MAP, new HashMap());
        
        String fileInfo = req.getParameter("fileInfo");
        if(fileInfo!=null && !fileInfo.equals(""))
        {
            dataMap.put("URL", ProxyConfig.getConf("TALK_DOWNLOAD_API_URL"));
        }
        else
        {
            String domainId = req.getParameter("domainId");
            String serviceType = req.getParameter("serviceType");
            StringBuffer addParam = new StringBuffer();

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
        }
        
        return dataMap;
    }
    
    /**
     * 파라메터를 Map에 세팅한다.
     * Multipart일 경우 파일을 임시 저장하고 파일정보를 포함한다.
     * 이 메소드는 setParameter메소드와 형식을 맞춰야 한다.
     * 
     * @param req
     * @param dataMap
     * @param isMultipart
     */
    protected void setParamMap(HttpServletRequest req, Map dataMap, boolean isMultipart)
    {
        HashMap paramMap = (HashMap)dataMap.get(ProxyHttpClient.REQUEST_PARAM_MAP);
        
        try
        {
            req.setCharacterEncoding("UTF-8");
            paramMap.put("authId", ProxyConfig.getConf("AUTH_ID"));
            if(isMultipart)
            {
                ServletFileUpload upload = new ServletFileUpload();
                upload.setHeaderEncoding(ProxyConfig.getConf("ENCODING"));
                //upload.setFileSizeMax(getConfInt("ATTACH_MAX_SIZE"));
                FileItemIterator iter = upload.getItemIterator(req);
                while(iter.hasNext())
                {
                    FileItemStream item = iter.next();
                    if(item.isFormField())
                    {
                        String name = item.getFieldName();
                        if(name != null && ProxyHttpClient.REQUEST_PARAM_CMD.equals(name))
                        {
                            paramMap.put(name, new Gson().fromJson(URLDecoder.decode(Streams.asString(item.openStream(), ProxyConfig.getConf("ENCODING")), ProxyConfig.getConf("ENCODING")), HashMap.class));
                        }
                        else
                        {
                            paramMap.put(name, Streams.asString(item.openStream(), ProxyConfig.getConf("ENCODING")));
                        }
                    }
                    else
                    {   
                        // 파일첨부는 단일로 한정함.
                        // 실제 파일 이름
                        String strFileName = item.getName();
                        strFileName = strFileName.substring(strFileName.lastIndexOf("/") + 1);
                        strFileName = strFileName.substring(strFileName.lastIndexOf("\\") + 1);
                        // 저장할 파일 이름
                        String saveFileExt = strFileName.substring(strFileName.lastIndexOf(".") + 1);
                        String saveFileName = MessageFormat.format("{0}.{1}", ProxyUtil.getGuid(), saveFileExt);
                        String saveFileFullPath = ProxyConfig.getConf("UPLOAD_HOME") + "/" + saveFileName;
                        // 파일 생성
                        long lFileSize = Streams.copy(item.openStream(), new BufferedOutputStream(new FileOutputStream(saveFileFullPath)), true);
                        // 파일 정보 세팅
                        Map fileInfo = new HashMap();

                        fileInfo.put("fileName", strFileName);
                        fileInfo.put("savePath", saveFileFullPath);
                        fileInfo.put("fileSize", lFileSize);

                        paramMap.put(ProxyHttpClient.REQUEST_PARAM_FILE_INFO_MAP, fileInfo);
                    }
                }
            }
            else
            {   
                Enumeration paramNames = req.getParameterNames();
                while(paramNames.hasMoreElements())
                {
                    String name = paramNames.nextElement().toString();
                    String value = req.getParameter(name);
                    
                    if(name != null && ProxyHttpClient.REQUEST_PARAM_CMD.equals(name))
                    {
                        HashMap hMap = new Gson().fromJson(value, HashMap.class);
                        String message = (String) hMap.get("message");
                        if (message != null)
                        {
                            //message = message.replaceAll("[%]", "%25");
                            //message = message.replaceAll("[+]", "%2B");
                            hMap.put("message", message);
                        }
                        
                        String jsonString = new Gson().toJson(hMap);
                        
                        String fileInfo = (String) hMap.get("fileInfo");
                        if (fileInfo != null)
                        {
                            jsonString = jsonString.replaceAll("[+]", "_X_X_");
                            paramMap.put(name, new Gson().fromJson(URLDecoder.decode(jsonString, ProxyConfig.getConf("ENCODING")), HashMap.class));
                        }
                        else
                        {
                            paramMap.put(name, new Gson().fromJson(jsonString, HashMap.class));
                        }
                    }
                    else
                    {
                        //value = value.replaceAll("[+]", "_X_X_");
                        //paramMap.put(name, URLDecoder.decode(value, ProxyConfig.getConf("ENCODING")));
                        paramMap.put(name, value);
                    }
                }
            }
            
        }
        catch(Exception e)
        {
            if(e.getCause() instanceof FileSizeLimitExceededException)
            {
                throw new ProxyException(e.getCause());
            }
            else
            {
                throw new ProxyException(e);
            }
        }
    }
}
