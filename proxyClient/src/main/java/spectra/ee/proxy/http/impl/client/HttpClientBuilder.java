/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package spectra.ee.proxy.http.impl.client;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLContext;

import spectra.ee.proxy.http.ConnectionReuseStrategy;
import spectra.ee.proxy.http.Header;
import spectra.ee.proxy.http.HttpRequestInterceptor;
import spectra.ee.proxy.http.HttpResponseInterceptor;
import spectra.ee.proxy.http.annotation.NotThreadSafe;
import spectra.ee.proxy.http.client.CookieStore;
import spectra.ee.proxy.http.client.HttpRequestRetryHandler;
import spectra.ee.proxy.http.client.RedirectStrategy;
import spectra.ee.proxy.http.client.UserTokenHandler;
import spectra.ee.proxy.http.client.config.CookieSpecs;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.protocol.RequestAcceptEncoding;
import spectra.ee.proxy.http.client.protocol.RequestAddCookies;
import spectra.ee.proxy.http.client.protocol.RequestClientConnControl;
import spectra.ee.proxy.http.client.protocol.RequestDefaultHeaders;
import spectra.ee.proxy.http.client.protocol.RequestExpectContinue;
import spectra.ee.proxy.http.client.protocol.ResponseContentEncoding;
import spectra.ee.proxy.http.client.protocol.ResponseProcessCookies;
import spectra.ee.proxy.http.config.ConnectionConfig;
import spectra.ee.proxy.http.config.Lookup;
import spectra.ee.proxy.http.config.RegistryBuilder;
import spectra.ee.proxy.http.config.SocketConfig;
import spectra.ee.proxy.http.conn.ConnectionKeepAliveStrategy;
import spectra.ee.proxy.http.conn.HttpClientConnectionManager;
import spectra.ee.proxy.http.conn.SchemePortResolver;
import spectra.ee.proxy.http.conn.routing.HttpRoutePlanner;
import spectra.ee.proxy.http.conn.socket.ConnectionSocketFactory;
import spectra.ee.proxy.http.conn.socket.LayeredConnectionSocketFactory;
import spectra.ee.proxy.http.conn.socket.PlainConnectionSocketFactory;
import spectra.ee.proxy.http.conn.ssl.SSLConnectionSocketFactory;
import spectra.ee.proxy.http.conn.ssl.SSLContexts;
import spectra.ee.proxy.http.conn.ssl.X509HostnameVerifier;
import spectra.ee.proxy.http.cookie.CookieSpecProvider;
import spectra.ee.proxy.http.impl.DefaultConnectionReuseStrategy;
import spectra.ee.proxy.http.impl.conn.DefaultRoutePlanner;
import spectra.ee.proxy.http.impl.conn.DefaultSchemePortResolver;
import spectra.ee.proxy.http.impl.conn.PoolingHttpClientConnectionManager;
import spectra.ee.proxy.http.impl.cookie.BestMatchSpecFactory;
import spectra.ee.proxy.http.impl.cookie.BrowserCompatSpecFactory;
import spectra.ee.proxy.http.impl.cookie.IgnoreSpecFactory;
import spectra.ee.proxy.http.impl.cookie.NetscapeDraftSpecFactory;
import spectra.ee.proxy.http.impl.cookie.RFC2109SpecFactory;
import spectra.ee.proxy.http.impl.cookie.RFC2965SpecFactory;
import spectra.ee.proxy.http.impl.execchain.ClientExecChain;
import spectra.ee.proxy.http.impl.execchain.MainClientExec;
import spectra.ee.proxy.http.impl.execchain.ProtocolExec;
import spectra.ee.proxy.http.impl.execchain.RedirectExec;
import spectra.ee.proxy.http.impl.execchain.RetryExec;
import spectra.ee.proxy.http.protocol.HttpProcessor;
import spectra.ee.proxy.http.protocol.HttpProcessorBuilder;
import spectra.ee.proxy.http.protocol.HttpRequestExecutor;
import spectra.ee.proxy.http.protocol.RequestContent;
import spectra.ee.proxy.http.protocol.RequestTargetHost;
import spectra.ee.proxy.http.protocol.RequestUserAgent;
import spectra.ee.proxy.http.util.VersionInfo;

/**
 * Builder for {@link CloseableHttpClient} instances.
 * <p/>
 * When a particular component is not explicitly this class will use its default implementation. System properties will
 * be taken into account when configuring the default implementations when {@link #useSystemProperties()} method is
 * called prior to calling {@link #build()}.
 * <ul>
 * <li>ssl.TrustManagerFactory.algorithm</li>
 * <li>javax.net.ssl.trustStoreType</li>
 * <li>javax.net.ssl.trustStore</li>
 * <li>javax.net.ssl.trustStoreProvider</li>
 * <li>javax.net.ssl.trustStorePassword</li>
 * <li>ssl.KeyManagerFactory.algorithm</li>
 * <li>javax.net.ssl.keyStoreType</li>
 * <li>javax.net.ssl.keyStore</li>
 * <li>javax.net.ssl.keyStoreProvider</li>
 * <li>javax.net.ssl.keyStorePassword</li>
 * <li>https.protocols</li>
 * <li>https.cipherSuites</li>
 * <li>http.proxyHost</li>
 * <li>http.proxyPort</li>
 * <li>http.nonProxyHosts</li>
 * <li>http.keepAlive</li>
 * <li>http.maxConnections</li>
 * <li>http.agent</li>
 * </ul>
 * <p/>
 * Please note that some settings used by this class can be mutually exclusive and may not apply when building
 * {@link CloseableHttpClient} instances.
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpClientBuilder
{

    private HttpRequestExecutor requestExec;

    private X509HostnameVerifier hostnameVerifier;

    private LayeredConnectionSocketFactory sslSocketFactory;

    private SSLContext sslcontext;

    private HttpClientConnectionManager connManager;

    private SchemePortResolver schemePortResolver;

    private ConnectionReuseStrategy reuseStrategy;

    private ConnectionKeepAliveStrategy keepAliveStrategy;

    private UserTokenHandler userTokenHandler;

    private HttpProcessor httpprocessor;

    private LinkedList<HttpRequestInterceptor> requestFirst;

    private LinkedList<HttpRequestInterceptor> requestLast;

    private LinkedList<HttpResponseInterceptor> responseFirst;

    private LinkedList<HttpResponseInterceptor> responseLast;

    private HttpRequestRetryHandler retryHandler;

    private HttpRoutePlanner routePlanner;

    private RedirectStrategy redirectStrategy;

    private Lookup<CookieSpecProvider> cookieSpecRegistry;

    private CookieStore cookieStore;

    private String userAgent;

    private Collection<? extends Header> defaultHeaders;

    private SocketConfig defaultSocketConfig;

    private ConnectionConfig defaultConnectionConfig;

    private RequestConfig defaultRequestConfig;

    private boolean redirectHandlingDisabled;

    private boolean automaticRetriesDisabled;

    private boolean contentCompressionDisabled;

    private boolean cookieManagementDisabled;

    private boolean connectionStateDisabled;

    private int maxConnTotal = 0;

    private int maxConnPerRoute = 0;

    private List<Closeable> closeables;

    static final String DEFAULT_USER_AGENT;
    static
    {
        final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.http.client", HttpClientBuilder.class.getClassLoader());
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
        DEFAULT_USER_AGENT = "Apache-HttpClient/" + release + " (java 1.5)";
    }

    public static HttpClientBuilder create()
    {
        return new HttpClientBuilder();
    }

    protected HttpClientBuilder()
    {
        super();
    }

    /**
     * Assigns {@link HttpRequestExecutor} instance.
     */
    public final HttpClientBuilder setRequestExecutor(final HttpRequestExecutor requestExec)
    {
        this.requestExec = requestExec;
        return this;
    }

    /**
     * Assigns {@link X509HostnameVerifier} instance.
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} and the
     * {@link #setSSLSocketFactory(org.apache.http.conn.socket.LayeredConnectionSocketFactory)} methods.
     */
    public final HttpClientBuilder setHostnameVerifier(final X509HostnameVerifier hostnameVerifier)
    {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * Assigns {@link SSLContext} instance.
     * <p/>
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} and the
     * {@link #setSSLSocketFactory(org.apache.http.conn.socket.LayeredConnectionSocketFactory)} methods.
     */
    public final HttpClientBuilder setSslcontext(final SSLContext sslcontext)
    {
        this.sslcontext = sslcontext;
        return this;
    }

    /**
     * Assigns {@link LayeredConnectionSocketFactory} instance.
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} method.
     */
    public final HttpClientBuilder setSSLSocketFactory(final LayeredConnectionSocketFactory sslSocketFactory)
    {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    /**
     * Assigns maximum total connection value.
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} method.
     */
    public final HttpClientBuilder setMaxConnTotal(final int maxConnTotal)
    {
        this.maxConnTotal = maxConnTotal;
        return this;
    }

    /**
     * Assigns maximum connection per route value.
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} method.
     */
    public final HttpClientBuilder setMaxConnPerRoute(final int maxConnPerRoute)
    {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }

    /**
     * Assigns default {@link SocketConfig}.
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} method.
     */
    public final HttpClientBuilder setDefaultSocketConfig(final SocketConfig config)
    {
        this.defaultSocketConfig = config;
        return this;
    }

    /**
     * Assigns default {@link ConnectionConfig}.
     * <p/>
     * Please note this value can be overridden by the
     * {@link #setConnectionManager(org.apache.http.conn.HttpClientConnectionManager)} method.
     */
    public final HttpClientBuilder setDefaultConnectionConfig(final ConnectionConfig config)
    {
        this.defaultConnectionConfig = config;
        return this;
    }

    /**
     * Assigns {@link HttpClientConnectionManager} instance.
     */
    public final HttpClientBuilder setConnectionManager(final HttpClientConnectionManager connManager)
    {
        this.connManager = connManager;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     */
    public final HttpClientBuilder setConnectionReuseStrategy(final ConnectionReuseStrategy reuseStrategy)
    {
        this.reuseStrategy = reuseStrategy;
        return this;
    }

    /**
     * Assigns {@link ConnectionKeepAliveStrategy} instance.
     */
    public final HttpClientBuilder setKeepAliveStrategy(final ConnectionKeepAliveStrategy keepAliveStrategy)
    {
        this.keepAliveStrategy = keepAliveStrategy;
        return this;
    }

    /**
     * Assigns {@link UserTokenHandler} instance.
     * <p/>
     * Please note this value can be overridden by the {@link #disableConnectionState()} method.
     */
    public final HttpClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler)
    {
        this.userTokenHandler = userTokenHandler;
        return this;
    }

    /**
     * Disables connection state tracking.
     */
    public final HttpClientBuilder disableConnectionState()
    {
        connectionStateDisabled = true;
        return this;
    }

    /**
     * Assigns {@link SchemePortResolver} instance.
     */
    public final HttpClientBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver)
    {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Assigns <tt>User-Agent</tt> value.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder setUserAgent(final String userAgent)
    {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Assigns default request header values.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders)
    {
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder addInterceptorFirst(final HttpResponseInterceptor itcp)
    {
        if (itcp == null)
        {
            return this;
        }
        if (responseFirst == null)
        {
            responseFirst = new LinkedList<HttpResponseInterceptor>();
        }
        responseFirst.addFirst(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder addInterceptorLast(final HttpResponseInterceptor itcp)
    {
        if (itcp == null)
        {
            return this;
        }
        if (responseLast == null)
        {
            responseLast = new LinkedList<HttpResponseInterceptor>();
        }
        responseLast.addLast(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder addInterceptorFirst(final HttpRequestInterceptor itcp)
    {
        if (itcp == null)
        {
            return this;
        }
        if (requestFirst == null)
        {
            requestFirst = new LinkedList<HttpRequestInterceptor>();
        }
        requestFirst.addFirst(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder addInterceptorLast(final HttpRequestInterceptor itcp)
    {
        if (itcp == null)
        {
            return this;
        }
        if (requestLast == null)
        {
            requestLast = new LinkedList<HttpRequestInterceptor>();
        }
        requestLast.addLast(itcp);
        return this;
    }

    /**
     * Disables state (cookie) management.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder disableCookieManagement()
    {
        this.cookieManagementDisabled = true;
        return this;
    }

    /**
     * Disables automatic content decompression.
     * <p/>
     * Please note this value can be overridden by the {@link #setHttpProcessor(org.apache.http.protocol.HttpProcessor)}
     * method.
     */
    public final HttpClientBuilder disableContentCompression()
    {
        contentCompressionDisabled = true;
        return this;
    }

    /**
     * Assigns {@link HttpProcessor} instance.
     */
    public final HttpClientBuilder setHttpProcessor(final HttpProcessor httpprocessor)
    {
        this.httpprocessor = httpprocessor;
        return this;
    }

    /**
     * Assigns {@link HttpRequestRetryHandler} instance.
     * <p/>
     * Please note this value can be overridden by the {@link #disableAutomaticRetries()} method.
     */
    public final HttpClientBuilder setRetryHandler(final HttpRequestRetryHandler retryHandler)
    {
        this.retryHandler = retryHandler;
        return this;
    }

    /**
     * Disables automatic request recovery and re-execution.
     */
    public final HttpClientBuilder disableAutomaticRetries()
    {
        automaticRetriesDisabled = true;
        return this;
    }

    /**
     * Assigns {@link HttpRoutePlanner} instance.
     */
    public final HttpClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner)
    {
        this.routePlanner = routePlanner;
        return this;
    }

    /**
     * Assigns {@link RedirectStrategy} instance.
     * <p/>
     * Please note this value can be overridden by the {@link #disableRedirectHandling()} method. `
     */
    public final HttpClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy)
    {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    /**
     * Disables automatic redirect handling.
     */
    public final HttpClientBuilder disableRedirectHandling()
    {
        redirectHandlingDisabled = true;
        return this;
    }

    /**
     * Assigns default {@link CookieStore} instance which will be used for request execution if not explicitly set in
     * the client execution context.
     */
    public final HttpClientBuilder setDefaultCookieStore(final CookieStore cookieStore)
    {
        this.cookieStore = cookieStore;
        return this;
    }

    /**
     * Assigns default {@link org.apache.http.cookie.CookieSpec} registry which will be used for request execution if
     * not explicitly set in the client execution context.
     */
    public final HttpClientBuilder setDefaultCookieSpecRegistry(final Lookup<CookieSpecProvider> cookieSpecRegistry)
    {
        this.cookieSpecRegistry = cookieSpecRegistry;
        return this;
    }

    /**
     * Assigns default {@link RequestConfig} instance which will be used for request execution if not explicitly set in
     * the client execution context.
     */
    public final HttpClientBuilder setDefaultRequestConfig(final RequestConfig config)
    {
        this.defaultRequestConfig = config;
        return this;
    }

    /**
     * For internal use.
     */
    protected ClientExecChain decorateMainExec(final ClientExecChain mainExec)
    {
        return mainExec;
    }

    /**
     * For internal use.
     */
    protected void addCloseable(final Closeable closeable)
    {
        if (closeable == null)
        {
            return;
        }
        if (closeables == null)
        {
            closeables = new ArrayList<Closeable>();
        }
        closeables.add(closeable);
    }

    public CloseableHttpClient build()
    {
        // Create main request executor
        HttpRequestExecutor requestExec = this.requestExec;
        if (requestExec == null)
        {
            requestExec = new HttpRequestExecutor();
        }
        HttpClientConnectionManager connManager = this.connManager;
        if (connManager == null)
        {
            LayeredConnectionSocketFactory sslSocketFactory = this.sslSocketFactory;
            if (sslSocketFactory == null)
            {
                final String[] supportedProtocols = null;
                final String[] supportedCipherSuites = null;
                X509HostnameVerifier hostnameVerifier = this.hostnameVerifier;
                if (hostnameVerifier == null)
                {
                    hostnameVerifier = SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
                }
                if (sslcontext != null)
                {
                    sslSocketFactory = new SSLConnectionSocketFactory(sslcontext, supportedProtocols, supportedCipherSuites, hostnameVerifier);
                }
                else
                {
                	sslSocketFactory = new SSLConnectionSocketFactory(SSLContexts.createDefault(), hostnameVerifier);
                }
            }
            @SuppressWarnings("resource")
            final PoolingHttpClientConnectionManager poolingmgr = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory> create().register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslSocketFactory).build());
            if (defaultSocketConfig != null)
            {
                poolingmgr.setDefaultSocketConfig(defaultSocketConfig);
            }
            if (defaultConnectionConfig != null)
            {
                poolingmgr.setDefaultConnectionConfig(defaultConnectionConfig);
            }
            if (maxConnTotal > 0)
            {
                poolingmgr.setMaxTotal(maxConnTotal);
            }
            if (maxConnPerRoute > 0)
            {
                poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
            }
            connManager = poolingmgr;
        }
        ConnectionReuseStrategy reuseStrategy = this.reuseStrategy;
        if (reuseStrategy == null)
        {
        	reuseStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        }
        ConnectionKeepAliveStrategy keepAliveStrategy = this.keepAliveStrategy;
        if (keepAliveStrategy == null)
        {
            keepAliveStrategy = DefaultConnectionKeepAliveStrategy.INSTANCE;
        }

        UserTokenHandler userTokenHandler = this.userTokenHandler;
        if (userTokenHandler == null)
        {
            if (!connectionStateDisabled)
            {
                userTokenHandler = DefaultUserTokenHandler.INSTANCE;
            }
        }
        ClientExecChain execChain = new MainClientExec(requestExec, connManager, reuseStrategy, keepAliveStrategy, userTokenHandler);

        execChain = decorateMainExec(execChain);

        HttpProcessor httpprocessor = this.httpprocessor;
        if (httpprocessor == null)
        {

            String userAgent = this.userAgent;
            if (userAgent == null)
            {
            	userAgent = DEFAULT_USER_AGENT;
            }

            final HttpProcessorBuilder b = HttpProcessorBuilder.create();
            if (requestFirst != null)
            {
                for (final HttpRequestInterceptor i : requestFirst)
                {
                    b.addFirst(i);
                }
            }
            if (responseFirst != null)
            {
                for (final HttpResponseInterceptor i : responseFirst)
                {
                    b.addFirst(i);
                }
            }
            b.addAll(new RequestDefaultHeaders(defaultHeaders), new RequestContent(), new RequestTargetHost(), new RequestClientConnControl(), new RequestUserAgent(userAgent), new RequestExpectContinue());
            if (!cookieManagementDisabled)
            {
                b.add(new RequestAddCookies());
            }
            if (!contentCompressionDisabled)
            {
                b.add(new RequestAcceptEncoding());
            }

            if (!cookieManagementDisabled)
            {
                b.add(new ResponseProcessCookies());
            }
            if (!contentCompressionDisabled)
            {
                b.add(new ResponseContentEncoding());
            }
            if (requestLast != null)
            {
                for (final HttpRequestInterceptor i : requestLast)
                {
                    b.addLast(i);
                }
            }
            if (responseLast != null)
            {
                for (final HttpResponseInterceptor i : responseLast)
                {
                    b.addLast(i);
                }
            }
            httpprocessor = b.build();
        }
        execChain = new ProtocolExec(execChain, httpprocessor);

        // Add request retry executor, if not disabled
        if (!automaticRetriesDisabled)
        {
            HttpRequestRetryHandler retryHandler = this.retryHandler;
            if (retryHandler == null)
            {
                retryHandler = DefaultHttpRequestRetryHandler.INSTANCE;
            }
            execChain = new RetryExec(execChain, retryHandler);
        }

        HttpRoutePlanner routePlanner = this.routePlanner;
        if (routePlanner == null)
        {
            SchemePortResolver schemePortResolver = this.schemePortResolver;
            if (schemePortResolver == null)
            {
                schemePortResolver = DefaultSchemePortResolver.INSTANCE;
            }
            routePlanner = new DefaultRoutePlanner(schemePortResolver);
        }
        // Add redirect executor, if not disabled
        if (!redirectHandlingDisabled)
        {
            RedirectStrategy redirectStrategy = this.redirectStrategy;
            if (redirectStrategy == null)
            {
                redirectStrategy = DefaultRedirectStrategy.INSTANCE;
            }
            execChain = new RedirectExec(execChain, routePlanner, redirectStrategy);
        }

        Lookup<CookieSpecProvider> cookieSpecRegistry = this.cookieSpecRegistry;
        if (cookieSpecRegistry == null)
        {
            cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider> create().register(CookieSpecs.BEST_MATCH, new BestMatchSpecFactory()).register(CookieSpecs.STANDARD, new RFC2965SpecFactory()).register(CookieSpecs.BROWSER_COMPATIBILITY, new BrowserCompatSpecFactory()).register(CookieSpecs.NETSCAPE, new NetscapeDraftSpecFactory()).register(CookieSpecs.IGNORE_COOKIES, new IgnoreSpecFactory()).register("rfc2109", new RFC2109SpecFactory()).register("rfc2965", new RFC2965SpecFactory()).build();
        }

        CookieStore defaultCookieStore = this.cookieStore;
        if (defaultCookieStore == null)
        {
            defaultCookieStore = new BasicCookieStore();
        }

        return new InternalHttpClient(execChain, connManager, routePlanner, cookieSpecRegistry, defaultCookieStore, defaultRequestConfig != null ? defaultRequestConfig : RequestConfig.DEFAULT, closeables != null ? new ArrayList<Closeable>(closeables) : null);
    }

}
