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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import spectra.ee.proxy.http.HttpClientConnection;
import spectra.ee.proxy.http.annotation.ThreadSafe;
import spectra.ee.proxy.http.concurrent.Cancellable;
import spectra.ee.proxy.http.conn.ConnectionReleaseTrigger;
import spectra.ee.proxy.http.conn.HttpClientConnectionManager;

/**
 * Internal connection holder.
 *
 * @since 4.3
 */
@ThreadSafe
class ConnectionHolder implements ConnectionReleaseTrigger, Cancellable, Closeable
{
    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

    private final HttpClientConnectionManager manager;

    private final HttpClientConnection managedConn;

    private volatile boolean reusable;

    private volatile Object state;

    private volatile long validDuration;

    private volatile TimeUnit tunit;

    private volatile boolean released;

    public ConnectionHolder(final HttpClientConnectionManager manager, final HttpClientConnection managedConn)
    {
        super();
        this.manager = manager;
        this.managedConn = managedConn;
    }

    public boolean isReusable()
    {
        return this.reusable;
    }

    public void markReusable()
    {
        this.reusable = true;
    }

    public void markNonReusable()
    {
        this.reusable = false;
    }

    public void setState(final Object state)
    {
        this.state = state;
    }

    public void setValidFor(final long duration, final TimeUnit tunit)
    {
        synchronized (this.managedConn)
        {
            this.validDuration = duration;
            this.tunit = tunit;
        }
    }

    public void releaseConnection()
    {
        synchronized (this.managedConn)
        {
            if (this.released)
            {
                return;
            }
            this.released = true;
            if (this.reusable)
            {
                this.manager.releaseConnection(this.managedConn, this.state, this.validDuration, this.tunit);
            }
            else
            {
                try
                {
                    this.managedConn.close();
                    logger.debug("Connection discarded");
                }
                catch (final IOException ex)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug(ex.getMessage(), ex);
                    }
                }
                finally
                {
                    this.manager.releaseConnection(this.managedConn, null, 0, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    public void abortConnection()
    {
        synchronized (this.managedConn)
        {
            if (this.released)
            {
                return;
            }
            this.released = true;
            try
            {
                this.managedConn.shutdown();
                logger.debug("Connection discarded");
            }
            catch (final IOException ex)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug(ex.getMessage(), ex);
                }
            }
            finally
            {
                this.manager.releaseConnection(this.managedConn, null, 0, TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean cancel()
    {
        final boolean alreadyReleased = this.released;
        logger.debug("Cancelling request execution");
        abortConnection();
        return !alreadyReleased;
    }

    public boolean isReleased()
    {
        return this.released;
    }

    public void close() throws IOException
    {
        abortConnection();
    }

}
