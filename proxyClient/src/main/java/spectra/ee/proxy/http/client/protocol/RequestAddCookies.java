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

package spectra.ee.proxy.http.client.protocol;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import spectra.ee.proxy.http.Header;
import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpHost;
import spectra.ee.proxy.http.HttpRequest;
import spectra.ee.proxy.http.HttpRequestInterceptor;
import spectra.ee.proxy.http.annotation.Immutable;
import spectra.ee.proxy.http.client.CookieStore;
import spectra.ee.proxy.http.client.config.CookieSpecs;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.methods.HttpUriRequest;
import spectra.ee.proxy.http.config.Lookup;
import spectra.ee.proxy.http.conn.routing.RouteInfo;
import spectra.ee.proxy.http.cookie.Cookie;
import spectra.ee.proxy.http.cookie.CookieOrigin;
import spectra.ee.proxy.http.cookie.CookieSpec;
import spectra.ee.proxy.http.cookie.CookieSpecProvider;
import spectra.ee.proxy.http.cookie.SetCookie2;
import spectra.ee.proxy.http.protocol.HttpContext;
import spectra.ee.proxy.http.util.Args;
import spectra.ee.proxy.http.util.TextUtils;

/**
 * Request interceptor that matches cookies available in the current {@link CookieStore} to the request being executed
 * and generates corresponding <code>Cookie</code> request headers.
 *
 * @since 4.0
 */
@Immutable
public class RequestAddCookies implements HttpRequestInterceptor
{

    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

    public RequestAddCookies()
    {
        super();
    }

    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException
    {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase("CONNECT"))
        {
            return;
        }

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        // Obtain cookie store
        final CookieStore cookieStore = clientContext.getCookieStore();
        if (cookieStore == null)
        {
            logger.debug("Cookie store not specified in HTTP context");
            return;
        }

        // Obtain the registry of cookie specs
        final Lookup<CookieSpecProvider> registry = clientContext.getCookieSpecRegistry();
        if (registry == null)
        {
            logger.debug("CookieSpec registry not specified in HTTP context");
            return;
        }

        // Obtain the target host, possibly virtual (required)
        final HttpHost targetHost = clientContext.getTargetHost();
        if (targetHost == null)
        {
            logger.debug("Target host not set in the context");
            return;
        }

        // Obtain the route (required)
        final RouteInfo route = clientContext.getHttpRoute();
        if (route == null)
        {
            logger.debug("Connection route not set in the context");
            return;
        }

        final RequestConfig config = clientContext.getRequestConfig();
        String policy = config.getCookieSpec();
        if (policy == null)
        {
            policy = CookieSpecs.BEST_MATCH;
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("CookieSpec selected: " + policy);
        }

        URI requestURI = null;
        if (request instanceof HttpUriRequest)
        {
            requestURI = ((HttpUriRequest) request).getURI();
        }
        else
        {
            try
            {
                requestURI = new URI(request.getRequestLine().getUri());
            }
            catch (final URISyntaxException ignore)
            {
            }
        }
        final String path = requestURI != null ? requestURI.getPath() : null;
        final String hostName = targetHost.getHostName();
        int port = targetHost.getPort();
        if (port < 0)
        {
            port = route.getTargetHost().getPort();
        }

        final CookieOrigin cookieOrigin = new CookieOrigin(hostName, port >= 0 ? port : 0, !TextUtils.isEmpty(path) ? path : "/", route.isSecure());

        // Get an instance of the selected cookie policy
        final CookieSpecProvider provider = registry.lookup(policy);
        if (provider == null)
        {
            throw new HttpException("Unsupported cookie policy: " + policy);
        }
        final CookieSpec cookieSpec = provider.create(clientContext);
        // Get all cookies available in the HTTP state
        final List<Cookie> cookies = new ArrayList<Cookie>(cookieStore.getCookies());
        // Find cookies matching the given origin
        final List<Cookie> matchedCookies = new ArrayList<Cookie>();
        final Date now = new Date();
        for (final Cookie cookie : cookies)
        {
            if (!cookie.isExpired(now))
            {
                if (cookieSpec.match(cookie, cookieOrigin))
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Cookie " + cookie + " match " + cookieOrigin);
                    }
                    matchedCookies.add(cookie);
                }
            }
            else
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Cookie " + cookie + " expired");
                }
            }
        }
        // Generate Cookie request headers
        if (!matchedCookies.isEmpty())
        {
            final List<Header> headers = cookieSpec.formatCookies(matchedCookies);
            for (final Header header : headers)
            {
                request.addHeader(header);
            }
        }

        final int ver = cookieSpec.getVersion();
        if (ver > 0)
        {
            boolean needVersionHeader = false;
            for (final Cookie cookie : matchedCookies)
            {
                if (ver != cookie.getVersion() || !(cookie instanceof SetCookie2))
                {
                    needVersionHeader = true;
                }
            }

            if (needVersionHeader)
            {
                final Header header = cookieSpec.getVersionHeader();
                if (header != null)
                {
                    // Advertise cookie version support
                    request.addHeader(header);
                }
            }
        }

        // Stick the CookieSpec and CookieOrigin instances to the HTTP context
        // so they could be obtained by the response interceptor
        context.setAttribute(HttpClientContext.COOKIE_SPEC, cookieSpec);
        context.setAttribute(HttpClientContext.COOKIE_ORIGIN, cookieOrigin);
    }

}
