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
import java.io.IOException;
import java.util.List;

import org.apache.log4j.Logger;

import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpHost;
import spectra.ee.proxy.http.HttpRequest;
import spectra.ee.proxy.http.annotation.ThreadSafe;
import spectra.ee.proxy.http.client.ClientProtocolException;
import spectra.ee.proxy.http.client.CookieStore;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.methods.CloseableHttpResponse;
import spectra.ee.proxy.http.client.methods.HttpExecutionAware;
import spectra.ee.proxy.http.client.methods.HttpRequestWrapper;
import spectra.ee.proxy.http.client.protocol.HttpClientContext;
import spectra.ee.proxy.http.config.Lookup;
import spectra.ee.proxy.http.conn.HttpClientConnectionManager;
import spectra.ee.proxy.http.conn.routing.HttpRoute;
import spectra.ee.proxy.http.conn.routing.HttpRoutePlanner;
import spectra.ee.proxy.http.cookie.CookieSpecProvider;
import spectra.ee.proxy.http.impl.execchain.ClientExecChain;
import spectra.ee.proxy.http.protocol.BasicHttpContext;
import spectra.ee.proxy.http.protocol.HttpContext;
import spectra.ee.proxy.http.util.Args;

/**
 * Internal class.
 *
 * @since 4.3
 */
@ThreadSafe
class InternalHttpClient extends CloseableHttpClient
{

    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

    private final ClientExecChain execChain;

    private final HttpClientConnectionManager connManager;

    private final HttpRoutePlanner routePlanner;

    private final Lookup<CookieSpecProvider> cookieSpecRegistry;

    private final CookieStore cookieStore;

    private final RequestConfig defaultConfig;

    private final List<Closeable> closeables;

    public InternalHttpClient(final ClientExecChain execChain, final HttpClientConnectionManager connManager, final HttpRoutePlanner routePlanner, final Lookup<CookieSpecProvider> cookieSpecRegistry, final CookieStore cookieStore, final RequestConfig defaultConfig, final List<Closeable> closeables)
    {
        super();
        Args.notNull(execChain, "HTTP client exec chain");
        Args.notNull(connManager, "HTTP connection manager");
        Args.notNull(routePlanner, "HTTP route planner");
        this.execChain = execChain;
        this.connManager = connManager;
        this.routePlanner = routePlanner;
        this.cookieSpecRegistry = cookieSpecRegistry;
        this.cookieStore = cookieStore;
        this.defaultConfig = defaultConfig;
        this.closeables = closeables;
    }

    private HttpRoute determineRoute(final HttpHost target, final HttpRequest request, final HttpContext context) throws HttpException
    {
        return this.routePlanner.determineRoute(target, request, context);
    }

    private void setupContext(final HttpClientContext context)
    {
        if (context.getAttribute(HttpClientContext.COOKIESPEC_REGISTRY) == null)
        {
            context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);
        }
        if (context.getAttribute(HttpClientContext.COOKIE_STORE) == null)
        {
            context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        }
        if (context.getAttribute(HttpClientContext.REQUEST_CONFIG) == null)
        {
            context.setAttribute(HttpClientContext.REQUEST_CONFIG, this.defaultConfig);
        }
    }

    @Override
    protected CloseableHttpResponse doExecute(final HttpHost target, final HttpRequest request, final HttpContext context) throws IOException, ClientProtocolException
    {
        Args.notNull(request, "HTTP request");
        HttpExecutionAware execAware = null;
        if (request instanceof HttpExecutionAware)
        {
            execAware = (HttpExecutionAware) request;
        }
        try
        {
            final HttpRequestWrapper wrapper = HttpRequestWrapper.wrap(request);
            final HttpClientContext localcontext = HttpClientContext.adapt(context != null ? context : new BasicHttpContext());

            setupContext(localcontext);
            final HttpRoute route = determineRoute(target, wrapper, localcontext);

            return this.execChain.execute(route, wrapper, localcontext, execAware);
        }
        catch (final HttpException httpException)
        {
            throw new ClientProtocolException(httpException);
        }
    }

    public void close()
    {
        this.connManager.shutdown();
        if (this.closeables != null)
        {
            for (final Closeable closeable : this.closeables)
            {
                try
                {
                    closeable.close();
                }
                catch (final IOException ex)
                {
                    logger.error(ex.getMessage(), ex);
                }
            }
        }
    }
}
