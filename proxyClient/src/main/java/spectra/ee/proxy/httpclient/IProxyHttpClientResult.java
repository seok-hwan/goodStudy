package spectra.ee.proxy.httpclient;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public interface IProxyHttpClientResult
{
	void processResult(HttpServletResponse res, String callUrl, String charset, int statusCode, InputStream is) 
			throws IOException, UnsupportedEncodingException;
}
