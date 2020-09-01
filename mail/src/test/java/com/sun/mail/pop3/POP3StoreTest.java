/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.mail.pop3;

import com.sun.mail.test.TestServer;
import org.junit.Test;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Test POP3Store.
 *
 * @author sbo
 * @author Bill Shannon
 */
public final class POP3StoreTest {

    /**
     * Check is connected.
     */
    @Test
    public void testIsConnected() {
        TestServer server = null;
        try {
            final POP3Handler handler = new POP3HandlerNoopErr();
            server = new TestServer(handler);
            server.start();

            final Properties properties = new Properties();
            properties.setProperty("mail.pop3.host", "localhost");
            properties.setProperty("mail.pop3.port", "" + server.getPort());
            final Session session = Session.getInstance(properties);
            //session.setDebug(true);

            final Store store = session.getStore("pop3");
            try {
                store.connect("test", "test");
                final Folder folder = store.getFolder("INBOX");
                folder.open(Folder.READ_ONLY);

                // Check
                assertFalse(folder.isOpen());
            } finally {
                store.close();
            }
        } catch (final Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (server != null) {
                server.quit();
            }
        }
    }

    /**
     * Check that enabling APOP with a server that doesn't support APOP
     * (and doesn't return any information in the greeting) doesn't fail.
     */
    @Test
    public void testApopNotSupported() {
        TestServer server = null;
        try {
            final POP3Handler handler = new POP3HandlerNoGreeting();
            server = new TestServer(handler);
            server.start();

            final Properties properties = new Properties();
            properties.setProperty("mail.pop3.host", "localhost");
            properties.setProperty("mail.pop3.port", "" + server.getPort());
            properties.setProperty("mail.pop3.apop.enable", "true");
            final Session session = Session.getInstance(properties);
            //session.setDebug(true);

            final Store store = session.getStore("pop3");
            try {
                store.connect("test", "test");
		// success!
            } finally {
                store.close();
            }
        } catch (final Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (server != null) {
                server.quit();
            }
        }
    }

    /**
     * Check whether POP3 XOAUTH2 connection can be established
     */
    @Test
    public void testXOAUTH2POP3Connection() {
        TestServer server = null;

        try {
            final POP3Handler handler = new POP3HandlerXOAUTH();
            server = new TestServer(handler);
            server.start();

            final Properties properties = new Properties();
            properties.setProperty("mail.pop3.host", "localhost");
            properties.setProperty("mail.pop3.port", "" + server.getPort());
            properties.setProperty("mail.pop3.auth.mechanisms", "XOAUTH2");

            final Session session = Session.getInstance(properties);

            final POP3Store store = (POP3Store) session.getStore("pop3");
            try {
                store.protocolConnect("localhost", server.getPort(), "test", "test");
            } catch (Exception ex) {
                System.out.println(ex);
                ex.printStackTrace();
                fail(ex.toString());
            } finally {
                store.close();
            }
        } catch (final Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (server != null) {
                server.quit();
            }
        }
    }

    /**
     * Check whether POP3 XOAUTH2 connection can be established using fallback with two lanes authentication format
     */
    @Test
    public void testXOAUTH2POP3ConnectionFallback() {
        TestServer server = null;

        try {
            final POP3Handler handler = new POP3HandlerXOAUTHFallback();
            server = new TestServer(handler);
            server.start();

            final Properties properties = new Properties();
            properties.setProperty("mail.pop3.host", "localhost");
            properties.setProperty("mail.pop3.port", "" + server.getPort());
            properties.setProperty("mail.pop3.auth.mechanisms", "XOAUTH2");
            properties.setProperty("mail.pop3.disablecapa", "false");

            final Session session = Session.getInstance(properties);

            final POP3Store store = (POP3Store) session.getStore("pop3");
            try {
                store.protocolConnect("localhost", server.getPort(), "test", "test");
            } catch (Exception ex) {
                System.out.println(ex);
                ex.printStackTrace();
                fail(ex.toString());
            } finally {
                store.close();
            }
        } catch (final Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (server != null) {
                server.quit();
            }
        }
    }

    /**
     * Custom handler of AUTH command. Returns error during the first attempt to use run fallback path.
     *
     * @author Mateusz Marzęcki
     */
    private static class POP3HandlerXOAUTH extends POP3Handler {
        @Override
        public void auth() throws IOException {
            this.println("+OK POP3 server ready");
        }

        @Override
        public void capa() throws IOException {
            this.writer.println("+ OK");
            this.writer.println("SASL PLAIN XOAUTH2");
            this.println(".");
        }
    }

    /**
     * Custom handler of AUTH command. Returns error during the first attempt to use run fallback path.
     *
     * @author Mateusz Marzęcki
     */
    private static final class POP3HandlerXOAUTHFallback extends POP3HandlerXOAUTH {
        private static int execution = 0;

        @Override
        public void auth() throws IOException {
            if (execution == 0) {
                // returning ERR to go the fallback
                this.println("-ERR Connection dropped");
            } else if (execution == 1) {
                this.println("+OK AUTH XOAUTH2 ....");
            }

            execution += 1;
        }
    }

    /**
     * Custom handler. Returns ERR for NOOP.
     *
     * @author sbo
     */
    private static final class POP3HandlerNoopErr extends POP3Handler {

        /**
         * {@inheritDoc}
         */
	@Override
        public void noop() throws IOException {
            this.println("-ERR");
        }
    }

    /**
     * Custom handler.  Don't include any extra information in the greeting.
     *
     * @author Bill Shannon
     */
    private static final class POP3HandlerNoGreeting extends POP3Handler {

        /**
         * {@inheritDoc}
         */
	@Override
        public void sendGreetings() throws IOException {
            this.println("+OK");
        }
    }
}
