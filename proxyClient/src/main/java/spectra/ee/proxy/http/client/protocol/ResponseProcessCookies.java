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
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import spectra.ee.proxy.http.Header;
import spectra.ee.proxy.http.HeaderIterator;
import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpResponse;
import spectra.ee.proxy.http.HttpResponseInterceptor;
import spectra.ee.proxy.http.annotation.Immutable;
import spectra.ee.proxy.http.client.CookieStore;
import spectra.ee.proxy.http.cookie.Cookie;
import spectra.ee.proxy.http.cookie.CookieOrigin;
import spectra.ee.proxy.http.cookie.CookieSpec;
import spectra.ee.proxy.http.cookie.MalformedCookieException;
import spectra.ee.proxy.http.cookie.SM;
import spectra.ee.proxy.http.protocol.HttpContext;
import spectra.ee.proxy.http.util.Args;

/**
 * Response interceptor that populates the current {@link CookieStore} with data contained in response cookies received
 * in the given the HTTP response.
 *
 * @since 4.0
 */
@Immutable
public class ResponseProcessCookies implements HttpResponseInterceptor
{

    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

    public ResponseProcessCookies()
    {
        super();
    }

    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException
    {
        Args.notNull(response, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        // Obtain actual CookieSpec instance
        final CookieSpec cookieSpec = clientContext.getCookieSpec();
        if (cookieSpec == null)
        {
            logger.debug("Cookie spec not specified in HTTP context");
            return;
        }
        // Obtain cookie store
        final CookieStore cookieStore = clientContext.getCookieStore();
        if (cookieStore == null)
        {
            logger.debug("Cookie store not specified in HTTP context");
            return;
        }
        // Obtain actual CookieOrigin instance
        final CookieOrigin cookieOrigin = clientContext.getCookieOrigin();
        if (cookieOrigin == null)
        {
            logger.debug("Cookie origin not specified in HTTP context");
            return;
        }
        HeaderIterator it = response.headerIterator(SM.SET_COOKIE);
        processCookies(it, cookieSpec, cookieOrigin, cookieStore);

        // see if the cookie spec supports cookie versioning.
        if (cookieSpec.getVersion() > 0)
        {
            // process set-cookie2 headers.
            // Cookie2 will replace equivalent Cookie instances
            it = response.headerIterator(SM.SET_COOKIE2);
            processCookies(it, cookieSpec, cookieOrigin, cookieStore);
        }
    }

    private void processCookies(final HeaderIterator iterator, final CookieSpec cookieSpec, final CookieOrigin cookieOrigin, final CookieStore cookieStore)
    {
        while (iterator.hasNext())
        {
            final Header header = iterator.nextHeader();
            try
            {
                final List<Cookie> cookies = cookieSpec.parse(header, cookieOrigin);
                for (final Cookie cookie : cookies)
                {
                    try
                    {
                        cookieSpec.validate(cookie, cookieOrigin);
                        cookieStore.addCookie(cookie);

                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Cookie accepted [" + formatCooke(cookie) + "]");
                        }
                    }
                    catch (final MalformedCookieException ex)
                    {
                        if (logger.isEnabledFor(Level.WARN))
                        {
                            logger.warn("Cookie rejected [" + formatCooke(cookie) + "] " + ex.getMessage());
                        }
                    }
                }
            }
            catch (final MalformedCookieException ex)
            {
                if (logger.isEnabledFor(Level.WARN))
                {
                    logger.warn("Invalid cookie header: \"" + header + "\". " + ex.getMessage());
                }
            }
        }
    }

    private static String formatCooke(final Cookie cookie)
    {
        final StringBuilder buf = new StringBuilder();
        buf.append(cookie.getName());
        buf.append("=\"");
        String v = cookie.getValue();
        if (v != null)
        {
            if (v.length() > 100)
            {
                v = v.substring(0, 100) + "...";
            }
            buf.append(v);
        }
        buf.append("\"");
        buf.append(", version:");
        buf.append(Integer.toString(cookie.getVersion()));
        buf.append(", domain:");
        buf.append(cookie.getDomain());
        buf.append(", path:");
        buf.append(cookie.getPath());
        buf.append(", expiry:");
        buf.append(cookie.getExpiryDate());
        return buf.toString();
    }

}
