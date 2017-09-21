package gov.usdot.cv.websocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import gov.usdot.cv.resources.PrivateResourceLoader;
import gov.usdot.cv.websocket.WebSocketServer.ServerWebSocket;

public class WebSocketClientServerTest {

	private static final Logger logger = Logger.getLogger(WebSocketClientServerTest.class);

	@SuppressWarnings("rawtypes")
	@BeforeClass
	public static void initLogger() {
		Enumeration appenders = LogManager.getRootLogger().getAllAppenders();
        if (!appenders.hasMoreElements()) {
        	Logger rootLogger = Logger.getRootLogger();
        	rootLogger.setLevel(Level.INFO);
        	
        	PatternLayout layout = new PatternLayout("%d{ISO8601} %5p %c{1}:%L - %m%n");
        	rootLogger.addAppender(new ConsoleAppender(layout));
        	rootLogger.info("Log4J not configured! Setting up default console configuration");
        }
	}
	
	@Test
	public void testClientServerUnsecure() throws URISyntaxException, InterruptedException, IOException {
		WebSocketServer server = new WebSocketServer(80);
		TestMessageProcessor serverProcessor = new TestMessageProcessor();
		server.addMessageProcessor(serverProcessor);
		server.start();
		Thread.sleep(500);
		assertEquals(0, server.connections().size());
		
		WebSocketClient client = new WebSocketClient("ws://localhost:80");
		TestMessageProcessor clientProcessor = new TestMessageProcessor();
		client.addMessageProcessor(clientProcessor);
		assertFalse(client.isConnected());
		client.connect();
		Thread.sleep(500);
		assertTrue(client.isConnected());
		
		assertEquals(1, server.connections().size());
		
		client.send("Test message from client");
		for (ServerWebSocket ws: server.connections()) {
			ws.send("Test message from server");
		}
		
		Thread.sleep(500);
		assertEquals(1, serverProcessor.getMessagesReceived().size());
		assertEquals("Test message from client", serverProcessor.getMessagesReceived().get(0));
		
		assertEquals(1, clientProcessor.getMessagesReceived().size());
		assertEquals("Test message from server", clientProcessor.getMessagesReceived().get(0));
		client.close();
		server.stop();
		Thread.sleep(5000);
	}
	
	@Test
	public void testClientServerSecure() throws URISyntaxException, InterruptedException, KeyManagementException, 
		KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
		
		SslContextFactory serverSslContextFactory =
							WebSocketSSLHelper.buildServerSslContextFactory(
									"@keystores/keystore.with_go_daddy_certs@", 
									PrivateResourceLoader.getProperty("@websocket/keystore.password@"));
		WebSocketServer server = new WebSocketServer(443, serverSslContextFactory);
		TestMessageProcessor serverProcessor = new TestMessageProcessor();
		server.addMessageProcessor(serverProcessor);
		server.start();
		Thread.sleep(500);
		assertEquals(0, server.connections().size());
		
		SslContextFactory clientSslContextFactory =
							WebSocketSSLHelper.buildClientSslContextFactory(
									"@keystores/keystore.with_go_daddy_certs@", 
									PrivateResourceLoader.getProperty("@websocket/keystore.password@"));
		WebSocketClient client = new WebSocketClient("wss://localhost:443", clientSslContextFactory);
		TestMessageProcessor clientProcessor = new TestMessageProcessor();
		client.addMessageProcessor(clientProcessor);
		assertFalse(client.isConnected());
		client.connect();
		Thread.sleep(500);
		assertTrue(client.isConnected());
		
		assertEquals(1, server.connections().size());
		
		client.send("Test message from client");
		for (ServerWebSocket ws: server.connections()) {
			ws.send("Test message from server");
		}
		
		Thread.sleep(500);
		assertEquals(1, serverProcessor.getMessagesReceived().size());
		assertEquals("Test message from client", serverProcessor.getMessagesReceived().get(0));
		
		assertEquals(1, clientProcessor.getMessagesReceived().size());
		assertEquals("Test message from server", clientProcessor.getMessagesReceived().get(0));
		client.close();
		server.stop();
		Thread.sleep(5000);
	}
	
	@Test
	public void testClientReconnect() throws URISyntaxException, InterruptedException, KeyManagementException, 
		KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
		
		SslContextFactory serverSslContextFactory =
							WebSocketSSLHelper.buildServerSslContextFactory(
									"@keystores/keystore.with_go_daddy_certs@", 
									PrivateResourceLoader.getProperty("@websocket/keystore.password@"));
		WebSocketServer server = new WebSocketServer(443, serverSslContextFactory);
		TestMessageProcessor serverProcessor = new TestMessageProcessor();
		server.addMessageProcessor(serverProcessor);
		server.start();
		Thread.sleep(500);
		assertEquals(0, server.connections().size());
		
		SslContextFactory clientSslContextFactory =
							WebSocketSSLHelper.buildClientSslContextFactory(
									"@keystores/keystore.with_go_daddy_certs@", 
									PrivateResourceLoader.getProperty("@websocket/keystore.password@"));
		WebSocketClient client = new WebSocketClient("wss://localhost:443", clientSslContextFactory);
		TestMessageProcessor clientProcessor = new TestMessageProcessor();
		client.addMessageProcessor(clientProcessor);
		assertFalse(client.isConnected());
		client.connect();
		Thread.sleep(500);
		assertTrue(client.isConnected());
		
		assertEquals(1, server.connections().size());
		
		client.send("Test message from client");
		for (ServerWebSocket ws: server.connections()) {
			ws.send("Test message from server");
		}
		
		Thread.sleep(500);
		assertEquals(1, serverProcessor.getMessagesReceived().size());
		assertEquals("Test message from client", serverProcessor.getMessagesReceived().get(0));
		
		assertEquals(1, clientProcessor.getMessagesReceived().size());
		assertEquals("Test message from server", clientProcessor.getMessagesReceived().get(0));
		
		// now kill the server
		server.stop();
		Thread.sleep(6000);
		assertFalse("Client must be disconnected", client.isConnected());
		try {
			client.send("Test message from client while server is down");
			assertTrue("IOException should have been thrown", true);
		} catch (IOException ignore) {
			assertTrue("IOException should have been thrown", true);
		}
		assertEquals(1, serverProcessor.getMessagesReceived().size());
		
		server = new WebSocketServer(443, serverSslContextFactory);
		server.addMessageProcessor(serverProcessor);
		server.start();
		
		Thread.sleep(6000);
		assertTrue(client.isConnected());
		client.send("Test message from client while server is up");
		Thread.sleep(500);
		assertEquals(2, serverProcessor.getMessagesReceived().size());
		assertEquals("Test message from client while server is up", serverProcessor.getMessagesReceived().get(1));
		client.close();
		server.stop();
		Thread.sleep(500);
	}
	
	private class TestMessageProcessor implements WebSocketMessageProcessor {

		private List<String> messagesReceived = new ArrayList<String>();
		
		public void processMessage(BaseWebSocket socket, String message) {
			logger.info("Received message: " + message);
			messagesReceived.add(message);
		}

		public List<String> getMessagesReceived() {
			return messagesReceived;
		}
		
	}
}
