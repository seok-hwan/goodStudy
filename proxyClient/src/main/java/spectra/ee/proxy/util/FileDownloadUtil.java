package spectra.ee.proxy.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Hashtable;

import org.apache.commons.fileupload.util.Streams;

public class FileDownloadUtil
{
    public static Hashtable fileDownLoad(String url, String uploadHome) throws Exception
    {
        return saveFile(url, uploadHome);
    }

    private static Hashtable saveFile(String strAttachUrl, String uploadHome) throws Exception
    {
        URLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;

        Hashtable retTable = new Hashtable();

        try
        {
            URL url = new URL(strAttachUrl);
            conn = url.openConnection();
            in = conn.getInputStream();

            // 파일명 추출
            int index = -1;
            String strFileName = null;
            String disposition = conn.getHeaderField("Content-Disposition");

            if (disposition != null)
            {
                index = disposition.indexOf("filename=");
            }
            
            if (disposition == null || index == -1)
            {
                String urlFileName = strAttachUrl.substring(strAttachUrl.lastIndexOf("/")+1, strAttachUrl.length());
                strFileName = urlFileName;
            }
            else
            {
                strFileName = disposition.substring(index + 9, disposition.length());
                strFileName = strFileName.replaceAll("\"", "");
            }

            // 상담톡 파일 첨부 디렉토리 정보 얻어오기
            // attachpath : /talk/2013/09/13/
            String localAttachPath = "/talk/" + getDateDir();
            String localFileUploadPath = uploadHome + localAttachPath;

            // 디렉토리 경로 보안 검증
            if (localFileUploadPath.indexOf("../") > -1 || localFileUploadPath.indexOf("%00") > -1)
            {
                throw new Exception("invalid directory path");
            }

            // 디렉토리 생성
            // FBPass 별도의 디렉토리 경로 보안 검증 실시
            File dirPath = new File(localFileUploadPath);
            if (!dirPath.exists())
            {
                dirPath.mkdirs();
            }

            String guid = ProxyUtil.getGuid();
            String localSaveFileExt = strFileName.substring(strFileName.lastIndexOf(".") + 1);
            // 파일 작성
            String localSaveFileName = new StringBuffer().append(guid + "." + localSaveFileExt).toString();
            String strFileDownloadFullPath = localFileUploadPath + "/" + localSaveFileName;

            // 파일 경로 보안 검증
            if (strFileDownloadFullPath.indexOf("../") > -1 || strFileDownloadFullPath.indexOf("%00") > -1)
            {
                throw new Exception("invalid file download path");
            }

            File downLoadFile = new File(strFileDownloadFullPath);
            long lFileSize = Streams.copy(conn.getInputStream(), new BufferedOutputStream(new FileOutputStream(downLoadFile)), true);

            if (lFileSize <= 0)
            {
                String message = "invalid file size.(" + lFileSize + ")";
                throw new Exception(message);
            }

            // retTable.put("fileFullPath", strFileDownloadFullPath);
            retTable.put("fileName", strFileName);
            retTable.put("savePath", strFileDownloadFullPath);
            retTable.put("fileExt", localSaveFileExt);
            retTable.put("fileSize", lFileSize);

            return retTable;
        }
        catch (MalformedURLException e)
        {
            throw e;
        }
        catch (IOException ie)
        {
            throw ie;
        }
        finally
        {
            try
            {
                if (in != null)
                {
                    in.close();
                }
                if (out != null)
                {
                    out.close();
                }
            }
            catch (IOException ioe)
            {
                ioe.printStackTrace();  //NOPMD:RWVIEWED: by cichung on 14.07.16 오후 1:24
            }
        }

    }

    protected static String getDateDir()
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
        return formatter.format(new java.util.Date());
    }
}
