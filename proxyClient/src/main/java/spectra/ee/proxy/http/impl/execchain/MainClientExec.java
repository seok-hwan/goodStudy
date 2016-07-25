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
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import spectra.ee.proxy.http.annotation.Immutable;
import spectra.ee.proxy.http.ConnectionReuseStrategy;
import spectra.ee.proxy.http.HttpClientConnection;
import spectra.ee.proxy.http.HttpEntity;
import spectra.ee.proxy.http.HttpEntityEnclosingRequest;
import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpRequest;
import spectra.ee.proxy.http.HttpResponse;
import spectra.ee.proxy.http.client.UserTokenHandler;
import spectra.ee.proxy.http.client.config.RequestConfig;
import spectra.ee.proxy.http.client.methods.CloseableHttpResponse;
import spectra.ee.proxy.http.client.methods.HttpExecutionAware;
import spectra.ee.proxy.http.client.methods.HttpRequestWrapper;
import spectra.ee.proxy.http.client.protocol.HttpClientContext;
import spectra.ee.proxy.http.conn.ConnectionKeepAliveStrategy;
import spectra.ee.proxy.http.conn.ConnectionRequest;
import spectra.ee.proxy.http.conn.HttpClientConnectionManager;
import spectra.ee.proxy.http.conn.routing.BasicRouteDirector;
import spectra.ee.proxy.http.conn.routing.HttpRoute;
import spectra.ee.proxy.http.conn.routing.HttpRouteDirector;
import spectra.ee.proxy.http.conn.routing.RouteTracker;
import spectra.ee.proxy.http.impl.conn.ConnectionShutdownException;
import spectra.ee.proxy.http.protocol.HttpCoreContext;
import spectra.ee.proxy.http.protocol.HttpRequestExecutor;
import spectra.ee.proxy.http.util.Args;

/**
 * The last request executor in the HTTP request execution chain that is responsible for execution of request / response
 * exchanges with the opposite endpoint. This executor will automatically retry the request in case of an authentication
 * challenge by an intermediate proxy or by the target server.
 *
 * @since 4.3
 */
@Immutable
public class MainClientExec implements ClientExecChain
{

    private final static Class<?> clazz = new Object() {/**/}.getClass().getEnclosingClass();

    private static final Logger logger = Logger.getLogger(clazz);

    private final HttpRequestExecutor requestExecutor;

    private final HttpClientConnectionManager connManager;

    private final ConnectionReuseStrategy reuseStrategy;

    private final ConnectionKeepAliveStrategy keepAliveStrategy;

    private final UserTokenHandler userTokenHandler;

    private final HttpRouteDirector routeDirector;

    public MainClientExec(final HttpRequestExecutor requestExecutor, final HttpClientConnectionManager connManager, final ConnectionReuseStrategy reuseStrategy, final ConnectionKeepAliveStrategy keepAliveStrategy, final UserTokenHandler userTokenHandler)
    {
        Args.notNull(requestExecutor, "HTTP request executor");
        Args.notNull(connManager, "Client connection manager");
        Args.notNull(reuseStrategy, "Connection reuse strategy");
        Args.notNull(keepAliveStrategy, "Connection keep alive strategy");
        Args.notNull(userTokenHandler, "User token handler");
        this.routeDirector = new BasicRouteDirector();
        this.requestExecutor = requestExecutor;
        this.connManager = connManager;
        this.reuseStrategy = reuseStrategy;
        this.keepAliveStrategy = keepAliveStrategy;
        this.userTokenHandler = userTokenHandler;
    }

    public CloseableHttpResponse execute(final HttpRoute route, final HttpRequestWrapper request, final HttpClientContext context, final HttpExecutionAware execAware) throws IOException, HttpException
    {
        Args.notNull(route, "HTTP route");
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        if (request instanceof HttpEntityEnclosingRequest)
        {
            RequestEntityProxy.enhance((HttpEntityEnclosingRequest) request);
        }

        Object userToken = context.getUserToken();

        final ConnectionRequest connRequest = connManager.requestConnection(route, userToken);
        if (execAware != null)
        {
            if (execAware.isAborted())
            {
                connRequest.cancel();
                throw new RequestAbortedException("Request aborted");
            }
            else
            {
                execAware.setCancellable(connRequest);
            }
        }

        final RequestConfig config = context.getRequestConfig();

        final HttpClientConnection managedConn;
        try
        {
            final int timeout = config.getConnectionRequestTimeout();
            managedConn = connRequest.get(timeout > 0 ? timeout : 0, TimeUnit.MILLISECONDS);
        }
        catch (final InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new RequestAbortedException("Request aborted", interrupted);
        }
        catch (final ExecutionException ex)
        {
            Throwable cause = ex.getCause();
            if (cause == null)
            {
                cause = ex;
            }
            throw new RequestAbortedException("Request execution failed", cause);
        }

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, managedConn);

        if (config.isStaleConnectionCheckEnabled())
        {
            // validate connection
            if (managedConn.isOpen())
            {
                logger.debug("Stale connection check");
                if (managedConn.isStale())
                {
                    logger.debug("Stale connection detected");
                    managedConn.close();
                }
            }
        }

        final ConnectionHolder connHolder = new ConnectionHolder(this.connManager, managedConn);
        try
        {
            if (execAware != null)
            {
                execAware.setCancellable(connHolder);
            }

            HttpResponse response;

            if (execAware != null && execAware.isAborted())
            {
                throw new RequestAbortedException("Request aborted");
            }

            if (!managedConn.isOpen())
            {
                logger.debug("Opening connection " + route);
                establishRoute(managedConn, route, request, context);
            }
            final int timeout = config.getSocketTimeout();
            if (timeout >= 0)
            {
                managedConn.setSocketTimeout(timeout);
            }

            if (execAware != null && execAware.isAborted())
            {
                throw new RequestAbortedException("Request aborted");
            }

            if (logger.isDebugEnabled())
            {
                logger.debug("Executing request " + request.getRequestLine());
            }

            response = requestExecutor.execute(request, managedConn, context);

            // The connection is in or can be brought to a re-usable state.
            if (reuseStrategy.keepAlive(response, context))
            {
                // Set the idle duration of this connection
                final long duration = keepAliveStrategy.getKeepAliveDuration(response, context);
                if (logger.isDebugEnabled())
                {
                    final String s;
                    if (duration > 0)
                    {
                        s = "for " + duration + " " + TimeUnit.MILLISECONDS;
                    }
                    else
                    {
                        s = "indefinitely";
                    }
                    logger.debug("Connection can be kept alive " + s);
                }
                connHolder.setValidFor(duration, TimeUnit.MILLISECONDS);
                connHolder.markReusable();
            }
            else
            {
                connHolder.markNonReusable();
            }

            if (userToken == null)
            {
                userToken = userTokenHandler.getUserToken(context);
                context.setAttribute(HttpClientContext.USER_TOKEN, userToken);
            }
            if (userToken != null)
            {
                connHolder.setState(userToken);
            }

            // check for entity, release connection if possible
            final HttpEntity entity = response.getEntity();
            if (entity == null || !entity.isStreaming())
            {
                // connection not needed and (assumed to be) in re-usable state
                connHolder.releaseConnection();
                return new HttpResponseProxy(response, null);
            }
            else
            {
                return new HttpResponseProxy(response, connHolder);
            }
        }
        catch (final ConnectionShutdownException ex)
        {
            final InterruptedIOException ioex = new InterruptedIOException("Connection has been shut down");
            ioex.initCause(ex);
            throw ioex;
        }
        catch (final HttpException ex)
        {
            connHolder.abortConnection();
            throw ex;
        }
        catch (final IOException ex)
        {
            connHolder.abortConnection();
            throw ex;
        }
        catch (final RuntimeException ex)
        {
            connHolder.abortConnection();
            throw ex;
        }
    }

    /**
     * Establishes the target route.
     */
    void establishRoute(final HttpClientConnection managedConn, final HttpRoute route, final HttpRequest request, final HttpClientContext context) throws HttpException, IOException
    {
        final RequestConfig config = context.getRequestConfig();
        final int timeout = config.getConnectTimeout();
        final RouteTracker tracker = new RouteTracker(route);

        int step;
        do
        {
            final HttpRoute fact = tracker.toRoute();
            step = this.routeDirector.nextStep(route, fact);
            switch (step)
            {

                case HttpRouteDirector.CONNECT_TARGET :
                    this.connManager.connect(managedConn, route, timeout > 0 ? timeout : 0, context);
                    tracker.connectTarget(route.isSecure());
                    break;

                case HttpRouteDirector.UNREACHABLE :
                    throw new HttpException("Unable to establish route: " + "planned = " + route + "; current = " + fact);
                case HttpRouteDirector.COMPLETE :
                    this.connManager.routeComplete(managedConn, route, context);
                    break;
                default :
                    throw new IllegalStateException("Unknown step indicator " + step + " from RouteDirector.");
            }

        }
        while (step > HttpRouteDirector.COMPLETE);
    }

}
