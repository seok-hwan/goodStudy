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

package spectra.ee.proxy.http.impl.io;

import java.io.IOException;

import spectra.ee.proxy.http.Header;
import spectra.ee.proxy.http.HeaderIterator;
import spectra.ee.proxy.http.HttpException;
import spectra.ee.proxy.http.HttpMessage;
import spectra.ee.proxy.http.annotation.NotThreadSafe;
import spectra.ee.proxy.http.io.HttpMessageWriter;
import spectra.ee.proxy.http.io.SessionOutputBuffer;
import spectra.ee.proxy.http.message.BasicLineFormatter;
import spectra.ee.proxy.http.message.LineFormatter;
import spectra.ee.proxy.http.util.Args;
import spectra.ee.proxy.http.util.CharArrayBuffer;

/**
 * Abstract base class for HTTP message writers that serialize output to an instance of {@link SessionOutputBuffer}.
 *
 * @since 4.0
 */
@NotThreadSafe
public abstract class AbstractMessageWriter<T extends HttpMessage> implements HttpMessageWriter<T>
{

    protected final SessionOutputBuffer sessionBuffer;

    protected final CharArrayBuffer lineBuf;

    protected final LineFormatter lineFormatter;

    /**
     * Creates an instance of AbstractMessageWriter.
     *
     * @param buffer the session output buffer.
     * @param formatter the line formatter If <code>null</code> {@link BasicLineFormatter#INSTANCE} will be used.
     * @since 4.3
     */
    public AbstractMessageWriter(final SessionOutputBuffer buffer, final LineFormatter formatter)
    {
        super();
        this.sessionBuffer = Args.notNull(buffer, "Session input buffer");
        this.lineFormatter = (formatter != null) ? formatter : BasicLineFormatter.INSTANCE;
        this.lineBuf = new CharArrayBuffer(128);
    }

    /**
     * Subclasses must override this method to write out the first header line based on the {@link HttpMessage} passed
     * as a parameter.
     *
     * @param message the message whose first line is to be written out.
     * @throws IOException in case of an I/O error.
     */
    protected abstract void writeHeadLine(T message) throws IOException;

    public void write(final T message) throws IOException, HttpException
    {
        Args.notNull(message, "HTTP message");
        writeHeadLine(message);
        for (final HeaderIterator it = message.headerIterator(); it.hasNext();)
        {
            final Header header = it.nextHeader();
            this.sessionBuffer.writeLine(lineFormatter.formatHeader(this.lineBuf, header));
        }
        this.lineBuf.clear();
        this.sessionBuffer.writeLine(this.lineBuf);
    }

}
