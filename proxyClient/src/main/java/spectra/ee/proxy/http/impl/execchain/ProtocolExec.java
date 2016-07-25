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

package spectra.ee.proxy.http.impl.execchain;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;

import spectra.ee.proxy.http.annotation.Immutable;
import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpHost;
import spectra.ee.proxy.http.HttpRequest;
import spectra.ee.proxy.http.ProtocolException;
import spectra.ee.proxy.http.client.methods.CloseableHttpResponse;
import spectra.ee.proxy.http.client.methods.HttpExecutionAware;
import spectra.ee.proxy.http.client.methods.HttpRequestWrapper;
import spectra.ee.proxy.http.client.methods.HttpUriRequest;
import spectra.ee.proxy.http.client.protocol.HttpClientContext;
import spectra.ee.proxy.http.client.utils.URIUtils;
import spectra.ee.proxy.http.conn.routing.HttpRoute;
import spectra.ee.proxy.http.protocol.HttpCoreContext;
import spectra.ee.proxy.http.protocol.HttpProcessor;
import spectra.ee.proxy.http.util.Args;

/**
 * Request executor in the request execution chain that is responsible for implementation of HTTP specification
 * requirements. Internally this executor relies on a {@link HttpProcessor} to populate requisite HTTP request headers,
 * process HTTP response headers and update session state in {@link HttpClientContext}.
 * <p/>
 * Further responsibilities such as communication with the opposite endpoint is delegated to the next executor in the
 * request execution chain.
 *
 * @since 4.3
 */
@Immutable
public class ProtocolExec implements ClientExecChain
{

    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

    private final ClientExecChain requestExecutor;

    private final HttpProcessor httpProcessor;

    public ProtocolExec(final ClientExecChain requestExecutor, final HttpProcessor httpProcessor)
    {
        Args.notNull(requestExecutor, "HTTP client request executor");
        Args.notNull(httpProcessor, "HTTP protocol processor");
        this.requestExecutor = requestExecutor;
        this.httpProcessor = httpProcessor;
    }

    void rewriteRequestURI(final HttpRequestWrapper request, final HttpRoute route) throws ProtocolException
    {
        try
        {
            URI uri = request.getURI();
            if (uri != null)
            {
                // Make sure the request URI is relative
                if (uri.isAbsolute())
                {
                    uri = URIUtils.rewriteURI(uri, null, true);
                }
                else
                {
                    uri = URIUtils.rewriteURI(uri);
                }
                request.setURI(uri);
            }
        }
        catch (final URISyntaxException ex)
        {
            throw new ProtocolException("Invalid URI: " + request.getRequestLine().getUri(), ex);
        }
    }

    public CloseableHttpResponse execute(final HttpRoute route, final HttpRequestWrapper request, final HttpClientContext context, final HttpExecutionAware execAware) throws IOException, HttpException
    {
        Args.notNull(route, "HTTP route");
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpRequest original = request.getOriginal();
        URI uri = null;
        if (original instanceof HttpUriRequest)
        {
            uri = ((HttpUriRequest) original).getURI();
        }
        else
        {
            final String uriString = original.getRequestLine().getUri();
            try
            {
                uri = URI.create(uriString);
            }
            catch (final IllegalArgumentException ex)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Unable to parse '" + uriString + "' as a valid URI; " + "request URI and Host header may be inconsistent", ex);
                }
            }

        }
        request.setURI(uri);

        // Re-write request URI if needed
        rewriteRequestURI(request, route);

        HttpHost target = null;
        if (uri != null && uri.isAbsolute() && uri.getHost() != null)
        {
            target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        }
        if (target == null)
        {
            target = route.getTargetHost();
        }

        // Run request protocol interceptors
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, target);
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        this.httpProcessor.process(request, context);

        final CloseableHttpResponse response = this.requestExecutor.execute(route, request, context, execAware);
        try
        {
            // Run response protocol interceptors
            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            this.httpProcessor.process(response, context);
            return response;
        }
        catch (final RuntimeException ex)
        {
            response.close();
            throw ex;
        }
        catch (final IOException ex)
        {
            response.close();
            throw ex;
        }
        catch (final HttpException ex)
        {
            response.close();
            throw ex;
        }
    }

}
