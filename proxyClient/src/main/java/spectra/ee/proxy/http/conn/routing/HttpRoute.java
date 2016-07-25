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

package spectra.ee.proxy.http.conn.routing;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import spectra.ee.proxy.http.HttpHost;
import spectra.ee.proxy.http.annotation.Immutable;
import spectra.ee.proxy.http.util.Args;
import spectra.ee.proxy.http.util.LangUtils;

/**
 * The route for a request.
 *
 * @since 4.0
 */
@Immutable
public final class HttpRoute implements RouteInfo, Cloneable
{

    /** The target host to connect to. */
    private final HttpHost targetHost;

    /**
     * The local address to connect from. <code>null</code> indicates that the default should be used.
     */
    private final InetAddress localAddress;

    /** Whether the route is (supposed to be) secure. */
    private final boolean secure;

    public HttpRoute(final HttpHost target, final InetAddress local, final boolean secure)
    {
        Args.notNull(target, "Target host");
        this.targetHost = target;
        this.localAddress = local;
        this.secure = secure;

    }

    public final HttpHost getTargetHost()
    {
        return this.targetHost;
    }

    public final InetAddress getLocalAddress()
    {
        return this.localAddress;
    }

    public final InetSocketAddress getLocalSocketAddress()
    {
        return this.localAddress != null ? new InetSocketAddress(this.localAddress, 0) : null;
    }

    public final boolean isSecure()
    {
        return this.secure;
    }

    /**
     * Compares this route to another.
     *
     * @param obj the object to compare with
     * @return <code>true</code> if the argument is the same route, <code>false</code>
     */
    @Override
    public final boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj instanceof HttpRoute)
        {
            final HttpRoute that = (HttpRoute) obj;
            return
            // Do the cheapest tests first
            (this.secure == that.secure) && LangUtils.equals(this.targetHost, that.targetHost) && LangUtils.equals(this.localAddress, that.localAddress);
        }
        else
        {
            return false;
        }
    }

    /**
     * Generates a hash code for this route.
     *
     * @return the hash code
     */
    @Override
    public final int hashCode()
    {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.targetHost);
        hash = LangUtils.hashCode(hash, this.localAddress);
        hash = LangUtils.hashCode(hash, this.secure);
        return hash;
    }

    /**
     * Obtains a description of this route.
     *
     * @return a human-readable representation of this route
     */
    @Override
    public final String toString()
    {
        final StringBuilder cab = new StringBuilder(80);
        if (this.localAddress != null)
        {
            cab.append(this.localAddress);
            cab.append("->");
        }
        cab.append('{');
        if (this.secure)
        {
            cab.append('s');
        }
        cab.append("}->");
        cab.append(this.targetHost);
        return cab.toString();
    }

    // default implementation of clone() is sufficient
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }

}
