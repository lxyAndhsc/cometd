/*
 * Copyright (c) 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cometd.server;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.common.JettyJSONContextClient;
import org.cometd.server.transport.JSONTransport;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.servlet.FilterHolder;
import org.junit.Assert;
import org.junit.Test;

public class SlowConnectionTest extends AbstractBayeuxClientServerTest
{
    @Test
    public void testSessionSweptDoesNotSendReconnectNoneAdvice() throws Exception
    {
        long maxInterval = 1000;
        Map<String, String> options = new HashMap<>();
        options.put(AbstractServerTransport.MAX_INTERVAL_OPTION, String.valueOf(maxInterval));
        startServer(options);

        final CountDownLatch sweeperLatch = new CountDownLatch(1);
        bayeux.addListener(new BayeuxServer.SessionListener()
        {
            public void sessionAdded(ServerSession session)
            {
            }

            public void sessionRemoved(ServerSession session, boolean timedout)
            {
                if (timedout)
                    sweeperLatch.countDown();
            }
        });

        Request handshake = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/handshake\"," +
                "\"version\": \"1.0\"," +
                "\"minimumVersion\": \"1.0\"," +
                "\"supportedConnectionTypes\": [\"long-polling\"]" +
                "}]");
        ContentResponse response = handshake.send();
        Assert.assertEquals(200, response.getStatus());

        String clientId = extractClientId(response);

        Request connect1 = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]");
        response = connect1.send();
        Assert.assertEquals(200, response.getStatus());

        // Do not send the second connect, so the sweeper can do its job
        Assert.assertTrue(sweeperLatch.await(2 * maxInterval, TimeUnit.MILLISECONDS));

        // Send the second connect, we should not get the reconnect:"none" advice
        Request connect2 = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]");
        response = connect2.send();
        Assert.assertEquals(200, response.getStatus());

        Message.Mutable reply = new JettyJSONContextClient().parse(response.getContentAsString())[0];
        Assert.assertEquals(Channel.META_CONNECT, reply.getChannel());
        Map<String, Object> advice = reply.getAdvice(false);
        if (advice != null)
            Assert.assertFalse(Message.RECONNECT_NONE_VALUE.equals(advice.get(Message.RECONNECT_FIELD)));
    }

    @Test
    public void testSessionSweptConcurrentlyDoesNotSendReconnectNoneAdvice() throws Exception
    {
        final long maxInterval = 1000;
        Map<String, String> options = new HashMap<>();
        options.put(AbstractServerTransport.MAX_INTERVAL_OPTION, String.valueOf(maxInterval));
        startServer(options);

        final CountDownLatch sweeperLatch = new CountDownLatch(1);
        bayeux.addListener(new BayeuxServer.SessionListener()
        {
            public void sessionAdded(ServerSession session)
            {
            }

            public void sessionRemoved(ServerSession session, boolean timedout)
            {
                if (timedout)
                    sweeperLatch.countDown();
            }
        });

        bayeux.getChannel(Channel.META_CONNECT).addListener(new ServerChannel.MessageListener()
        {
            public boolean onMessage(ServerSession from, ServerChannel channel, ServerMessage.Mutable message)
            {
                try
                {
                    // Wait to make the sweeper sweep this session
                    TimeUnit.MILLISECONDS.sleep(2 * maxInterval);
                    return true;
                }
                catch (InterruptedException x)
                {
                    return false;
                }
            }
        });

        Request handshake = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/handshake\"," +
                "\"version\": \"1.0\"," +
                "\"minimumVersion\": \"1.0\"," +
                "\"supportedConnectionTypes\": [\"long-polling\"]" +
                "}]");
        ContentResponse response = handshake.send();
        Assert.assertEquals(200, response.getStatus());

        String clientId = extractClientId(response);

        Request connect1 = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]");
        response = connect1.send();
        Assert.assertEquals(200, response.getStatus());

        Assert.assertTrue(sweeperLatch.await(maxInterval, TimeUnit.MILLISECONDS));

        Message.Mutable reply = new JettyJSONContextClient().parse(response.getContentAsString())[0];
        Assert.assertEquals(Channel.META_CONNECT, reply.getChannel());
        Map<String, Object> advice = reply.getAdvice(false);
        if (advice != null)
            Assert.assertFalse(Message.RECONNECT_NONE_VALUE.equals(advice.get(Message.RECONNECT_FIELD)));
    }

    @Test
    public void testSessionSweptWhileWritingQueueDoesNotSendReconnectNoneAdvice() throws Exception
    {
        final long maxInterval = 1000;
        Map<String, String> options = new HashMap<>();
        options.put(AbstractServerTransport.MAX_INTERVAL_OPTION, String.valueOf(maxInterval));
        startServer(options);

        final String channelName = "/test";
        bayeux.setTransports(new JSONTransport(bayeux)
        {
            {
                init();
            }

            @Override
            protected PrintWriter writeMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter writer, ServerSessionImpl session, ServerMessage message) throws IOException
            {
                try
                {
                    if (channelName.equals(message.getChannel()))
                    {
                        session.startIntervalTimeout(0);
                        TimeUnit.MILLISECONDS.sleep(2 * maxInterval);
                    }
                    return super.writeMessage(request, response, writer, session, message);
                }
                catch (InterruptedException x)
                {
                    throw new InterruptedIOException();
                }
            }
        });

        final CountDownLatch sweeperLatch = new CountDownLatch(1);
        bayeux.addListener(new BayeuxServer.SessionListener()
        {
            public void sessionAdded(ServerSession session)
            {
                ServerMessage.Mutable message = bayeux.newMessage();
                message.setChannel(channelName);
                message.setData("test");
                ((ServerSessionImpl)session).addMessage(message);
            }

            public void sessionRemoved(ServerSession session, boolean timedout)
            {
                if (timedout)
                    sweeperLatch.countDown();
            }
        });

        Request handshake = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/handshake\"," +
                "\"version\": \"1.0\"," +
                "\"minimumVersion\": \"1.0\"," +
                "\"supportedConnectionTypes\": [\"long-polling\"]" +
                "}]");
        ContentResponse response = handshake.send();
        Assert.assertEquals(200, response.getStatus());

        String clientId = extractClientId(response);

        Request connect1 = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]");
        response = connect1.send();
        Assert.assertEquals(200, response.getStatus());

        Assert.assertTrue(sweeperLatch.await(maxInterval, TimeUnit.MILLISECONDS));

        Message.Mutable[] replies = new JettyJSONContextClient().parse(response.getContentAsString());
        Message.Mutable reply = replies[replies.length - 1];
        Assert.assertEquals(Channel.META_CONNECT, reply.getChannel());
        Map<String, Object> advice = reply.getAdvice(false);
        if (advice != null)
            Assert.assertFalse(Message.RECONNECT_NONE_VALUE.equals(advice.get(Message.RECONNECT_FIELD)));
    }

    @Test
    public void testSlowConnection() throws Exception
    {
        startServer(null);
        context.stop();
        CountDownLatch exceptionLatch = new CountDownLatch(1);
        Filter filter = new ExceptionDetectorFilter(exceptionLatch);
        context.addFilter(new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST));
        context.start();
        bayeux = cometdServlet.getBayeux();

        final CountDownLatch sendLatch = new CountDownLatch(1);
        final CountDownLatch closeLatch = new CountDownLatch(1);
        final JSONTransport transport = new JSONTransport(bayeux)
        {
            {
                init();
            }

            @Override
            protected PrintWriter writeMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter writer, ServerSessionImpl session, ServerMessage message) throws IOException
            {
                if (message.getData() != null)
                {
                    sendLatch.countDown();
                    await(closeLatch);
                    // Simulate that an exception is being thrown while writing
                    throw new EofException("test_exception");
                }
                return super.writeMessage(request, response, writer, session, message);
            }
        };
        bayeux.setTransports(transport);
        long maxInterval = 5000L;
        transport.setMaxInterval(maxInterval);

        Request handshake = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/handshake\"," +
                "\"version\": \"1.0\"," +
                "\"minimumVersion\": \"1.0\"," +
                "\"supportedConnectionTypes\": [\"long-polling\"]" +
                "}]");
        ContentResponse response = handshake.send();
        Assert.assertEquals(200, response.getStatus());

        String clientId = extractClientId(response);

        String channelName = "/foo";
        Request subscribe = newBayeuxRequest("[{" +
                "\"clientId\": \"" + clientId + "\"," +
                "\"channel\": \"/meta/subscribe\"," +
                "\"subscription\": \"" + channelName + "\"" +
                "}]");
        response = subscribe.send();
        Assert.assertEquals(200, response.getStatus());

        Request connect1 = newBayeuxRequest("[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]");
        response = connect1.send();
        Assert.assertEquals(200, response.getStatus());

        // Send a server-side message so it gets written to the client
        bayeux.getChannel(channelName).publish(null, "x");

        Socket socket = new Socket("localhost", port);
        OutputStream output = socket.getOutputStream();
        byte[] content = ("[{" +
                "\"channel\": \"/meta/connect\"," +
                "\"clientId\": \"" + clientId + "\"," +
                "\"connectionType\": \"long-polling\"" +
                "}]").getBytes("UTF-8");
        String request = "" +
                "POST " + new URI(cometdURL).getPath() + "/connect HTTP/1.1\r\n" +
                "Host: localhost:" + port + "\r\n" +
                "Content-Type: application/json;charset=UTF-8\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "\r\n";
        output.write(request.getBytes("UTF-8"));
        output.write(content);
        output.flush();

        final CountDownLatch removeLatch = new CountDownLatch(1);
        ServerSession session = bayeux.getSession(clientId);
        session.addListener(new ServerSession.RemoveListener()
        {
            public void removed(ServerSession session, boolean timeout)
            {
                removeLatch.countDown();
            }
        });

        // Wait for messages to be written, but close the connection instead
        Assert.assertTrue(sendLatch.await(5, TimeUnit.SECONDS));
        socket.close();
        closeLatch.countDown();

        // Wait for the exception to be thrown while writing to a closed connection
        Assert.assertTrue(exceptionLatch.await(5, TimeUnit.SECONDS));

        // The session must be swept even if the server could not write a response
        // to the connect because of the exception.
        Assert.assertTrue(removeLatch.await(2 * maxInterval, TimeUnit.MILLISECONDS));
    }

    private void await(CountDownLatch latch)
    {
        try
        {
            latch.await();
        }
        catch (InterruptedException x)
        {
            Thread.currentThread().interrupt();
        }
    }

    public static class ExceptionDetectorFilter implements Filter
    {
        private final CountDownLatch exceptionLatch;

        public ExceptionDetectorFilter(CountDownLatch exceptionLatch)
        {
            this.exceptionLatch = exceptionLatch;
        }

        public void init(FilterConfig filterConfig) throws ServletException
        {
        }

        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
        {
            try
            {
                chain.doFilter(request, response);
            }
            catch (EofException x)
            {
                exceptionLatch.countDown();
                throw x;
            }
        }

        public void destroy()
        {
        }
    }
}
