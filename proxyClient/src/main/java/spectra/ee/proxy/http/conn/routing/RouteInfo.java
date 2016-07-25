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

/**
 * Read-only interface for route information.
 *
 * @since 4.0
 */
public interface RouteInfo
{
    /**
     * Obtains the target host.
     *
     * @return the target host
     */
    HttpHost getTargetHost();

    /**
     * Obtains the local address to connect from.
     *
     * @return the local address, or <code>null</code>
     */
    InetAddress getLocalAddress();

    /**
     * Checks whether this route is secure.
     *
     * @return <code>true</code> if secure, <code>false</code> otherwise
     */
    boolean isSecure();

}
