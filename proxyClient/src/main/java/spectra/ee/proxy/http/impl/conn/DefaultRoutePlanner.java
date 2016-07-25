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

package spectra.ee.proxy.http.impl.conn;

import java.net.InetAddress;

import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpHost;
import spectra.ee.proxy.http.HttpRequest;
import spectra.ee.proxy.http.ProtocolException;
import spectra.ee.proxy.http.annotation.Immutable;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.protocol.HttpClientContext;
import spectra.ee.proxy.http.conn.SchemePortResolver;
import spectra.ee.proxy.http.conn.UnsupportedSchemeException;
import spectra.ee.proxy.http.conn.routing.HttpRoute;
import spectra.ee.proxy.http.conn.routing.HttpRoutePlanner;
import spectra.ee.proxy.http.protocol.HttpContext;
import spectra.ee.proxy.http.util.Args;

/**
 * Default implementation of an {@link HttpRoutePlanner}. It will not make use of any Java system properties, nor of
 * system or browser proxy settings.
 *
 * @since 4.3
 */
@Immutable
public class DefaultRoutePlanner implements HttpRoutePlanner
{

    private final SchemePortResolver schemePortResolver;

    public DefaultRoutePlanner(final SchemePortResolver schemePortResolver)
    {
        super();
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
    }

    public HttpRoute determineRoute(final HttpHost host, final HttpRequest request, final HttpContext context) throws HttpException
    {
        Args.notNull(request, "Request");
        if (host == null)
        {
            throw new ProtocolException("Target host is not specified");
        }
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final RequestConfig config = clientContext.getRequestConfig();
        final InetAddress local = config.getLocalAddress();

        final HttpHost target;
        if (host.getPort() <= 0)
        {
            try
            {
                target = new HttpHost(host.getHostName(), this.schemePortResolver.resolve(host), host.getSchemeName());
            }
            catch (final UnsupportedSchemeException ex)
            {
                throw new HttpException(ex.getMessage());
            }
        }
        else
        {
            target = host;
        }
        final boolean secure = target.getSchemeName().equalsIgnoreCase("https");
        return new HttpRoute(target, local, secure);
    }
}
