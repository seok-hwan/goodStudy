package spectra.ee.proxy.controller;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import spectra.ee.proxy.ProxyConfig;

public class HttpTestStart extends HttpClientManager {

    //private ProxySimpleServlet proxySimpleServlet;

    @Before
    public void setup()
    {
        //loadProxyConfig();
        //proxySimpleServlet = new ProxySimpleServlet();
    }

    @Test
    public void doProxyHttpCall()
    {
        loadProxyConfig();
        ProxySimpleServlet proxySimpleServlet = new ProxySimpleServlet();
        // mock에서 서블릿을 지원안해서 spring mock 사용
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        String cmd = "{\"command\":\"FaqList\",\"includeSubNode\":\"Y\",\"removeLinkKbIdFlag\":\"Y\",\"domainId\":\"NODE0000000001\",\"nodeIds\":\"NODE0000000003\",\"pageNo\":1}";
        req.setParameter("cmd", cmd);
        try
        {
            proxySimpleServlet.doPost(req, res);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    @Test
    public void doSimpleHttpCall()
    {
        int timeout = 6000;
        String url = "http://211.63.24.86:7060/restapi/api";
        httpCall(url, timeout);
        url = "http://www.spectra.co.kr";
        httpCall(url, timeout);

    }

    private final void loadProxyConfig()
    {
        String filePath = System.getProperty("user.dir") + "/proxySimple.properties";
        try
        {
            ProxyConfig.load(filePath);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }
}
