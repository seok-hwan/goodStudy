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

import spectra.ee.proxy.http.HttpHost;
import spectra.ee.proxy.http.annotation.NotThreadSafe;
import spectra.ee.proxy.http.util.Args;
import spectra.ee.proxy.http.util.Asserts;
import spectra.ee.proxy.http.util.LangUtils;

/**
 * Helps tracking the steps in establishing a route.
 *
 * @since 4.0
 */
@NotThreadSafe
public final class RouteTracker implements RouteInfo, Cloneable
{

    /** The target host to connect to. */
    private final HttpHost targetHost;

    /**
     * The local address to connect from. <code>null</code> indicates that the default should be used.
     */
    private final InetAddress localAddress;

    // the attributes above are fixed at construction time
    // now follow attributes that indicate the established route

    /** Whether the first hop of the route is established. */
    private boolean connected;

    /** Whether the route is secure. */
    private boolean secure;

    /**
     * Creates a new route tracker. The target and origin need to be specified at creation time.
     *
     * @param target the host to which to route
     * @param local the local address to route from, or <code>null</code> for the default
     */
    public RouteTracker(final HttpHost target, final InetAddress local)
    {
        Args.notNull(target, "Target host");
        this.targetHost = target;
        this.localAddress = local;
    }

    /**
     * @since 4.2
     */
    public void reset()
    {
        this.connected = false;
        this.secure = false;
    }

    /**
     * Creates a new tracker for the given route. Only target and origin are taken from the route, everything else
     * remains to be tracked.
     *
     * @param route the route to track
     */
    public RouteTracker(final HttpRoute route)
    {
        this(route.getTargetHost(), route.getLocalAddress());
    }

    /**
     * Tracks connecting to the target.
     *
     * @param secure <code>true</code> if the route is secure, <code>false</code> otherwise
     */
    public final void connectTarget(final boolean secure)
    {
        Asserts.check(!this.connected, "Already connected");
        this.connected = true;
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

    public final int getHopCount()
    {
        int hops = 0;
        if (this.connected)
        {
        	hops = 1;
        }
        return hops;
    }

    public final HttpHost getHopTarget(final int hop)
    {
        Args.notNegative(hop, "Hop index");
        final int hopcount = getHopCount();
        Args.check(hop < hopcount, "Hop index exceeds tracked route length");

        return this.targetHost;
    }

    public final boolean isConnected()
    {
        return this.connected;
    }

    public final boolean isSecure()
    {
        return this.secure;
    }

    /**
     * Obtains the tracked route. If a route has been tracked, it is {@link #isConnected connected}. If not connected,
     * nothing has been tracked so far.
     *
     * @return the tracked route, or <code>null</code> if nothing has been tracked so far
     */
    public final HttpRoute toRoute()
    {
        return !this.connected ? null : new HttpRoute(this.targetHost, this.localAddress, this.secure);
    }

    /**
     * Compares this tracked route to another.
     *
     * @param o the object to compare with
     * @return <code>true</code> if the argument is the same tracked route, <code>false</code>
     */
    @Override
    public final boolean equals(final Object o)
    {
        if (o == this)
        {
            return true;
        }
        if (!(o instanceof RouteTracker))
        {
            return false;
        }

        final RouteTracker that = (RouteTracker) o;
        return
        // Do the cheapest checks first
        (this.connected == that.connected) && (this.secure == that.secure) && LangUtils.equals(this.targetHost, that.targetHost) && LangUtils.equals(this.localAddress, that.localAddress);
    }

    /**
     * Generates a hash code for this tracked route. Route trackers are modifiable and should therefore not be used as
     * lookup keys. Use {@link #toRoute toRoute} to obtain an unmodifiable representation of the tracked route.
     *
     * @return the hash code
     */
    @Override
    public final int hashCode()
    {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.targetHost);
        hash = LangUtils.hashCode(hash, this.localAddress);
        hash = LangUtils.hashCode(hash, this.connected);
        hash = LangUtils.hashCode(hash, this.secure);
        return hash;
    }

    /**
     * Obtains a description of the tracked route.
     *
     * @return a human-readable representation of the tracked route
     */
    @Override
    public final String toString()
    {
        final StringBuilder cab = new StringBuilder(50 + getHopCount() * 30);

        cab.append("RouteTracker[");
        if (this.localAddress != null)
        {
            cab.append(this.localAddress);
            cab.append("->");
        }
        cab.append('{');
        if (this.connected)
        {
            cab.append('c');
        }
        if (this.secure)
        {
            cab.append('s');
        }
        cab.append("}->");
        cab.append(this.targetHost);
        cab.append(']');

        return cab.toString();
    }

    // default implementation of clone() is sufficient
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        return super.clone();
    }

}
