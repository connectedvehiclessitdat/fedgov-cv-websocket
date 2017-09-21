package gov.usdot.cv.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;

import gov.usdot.cv.resources.PrivateResourceLoader;

public class WebSocketClient {
	private static final Logger logger = Logger.getLogger(WebSocketClient.class);
	
	private URI serverUri;
	private org.eclipse.jetty.websocket.client.WebSocketClient client;
	private ClientWebSocket socket;
	private List<WebSocketMessageProcessor> messageProcessors = new CopyOnWriteArrayList<WebSocketMessageProcessor>();
	private boolean reconnecting = false;
	private boolean stop = false;
	
	public WebSocketClient(String serverUri) throws URISyntaxException {
		this(serverUri, null);
	}
	
	public WebSocketClient(String serverUri, SslContextFactory sslContextFactory) throws URISyntaxException {
		this.serverUri = new URI(serverUri);
		if(sslContextFactory != null) {
			this.client = new org.eclipse.jetty.websocket.client.WebSocketClient(sslContextFactory);
		}
		else {
			this.client = new org.eclipse.jetty.websocket.client.WebSocketClient();
		}
		this.socket = new ClientWebSocket();
		
		try {
			client.start();
		} catch (Exception e) {
			logger.error("Failed to start WebSocket Client", e);
		}
	}
	
	public void addMessageProcessor(WebSocketMessageProcessor messageProcessor) {
		messageProcessors.add(messageProcessor);
	}
	
	public void send(String message) throws IOException {
		socket.send(message);
	}
	
	public boolean isConnected() {
		return (client != null && socket.isOpen());
	}
	
	public void connect() throws IOException {
		socket.connect();
		stop = false;
	}
	
	public void close() {
		stop = true;
		socket.close();
		try {
			client.stop();
		} catch (Exception e) {
			logger.error("Failed to stop WebSocket Client", e);
		}
	}
	
	private synchronized void reconnect() {
		if (!reconnecting) {
			ReconnectClient reconnect = new ReconnectClient();
			Thread thread = new Thread(reconnect);
			thread.setDaemon(true);
			thread.start();
		}
	}
	
	@WebSocket
	public class ClientWebSocket extends BaseWebSocket {
		
		@OnWebSocketConnect
		public void onOpen(Session session) {
			this.session = session;
			this.session.setIdleTimeout(0);		// Don't timeout
			
			logger.info("Connection opened to " + serverUri.toString());
		}

		@OnWebSocketClose
		public void onClose(int code, String reason) {
			logger.info("Connection to " + serverUri.toString() + " closed.");
			reconnect();
		}

		@OnWebSocketMessage
		public void onMessage(String message) {
			logger.debug("Received message: " + message);
			for (WebSocketMessageProcessor messageProcessor: messageProcessors) {
				messageProcessor.processMessage(null, message);
			}
		}

		@OnWebSocketError
		public void onError(Throwable t) {
			logger.error("Error:", t);
		}
		
		public void connect() {
			ClientUpgradeRequest request = new ClientUpgradeRequest();
	        try {
				client.connect(socket, serverUri, request);
			} catch (IOException e) {
				logger.error("Failed to connect socket to Server URI " + serverUri + ".", e);
			}
		}

		public void send(String message) throws IOException {
			if(isOpen()) {
				try {
					// If messages are attempted to be sent by multiple threads(for example, multiple clients)
					// to the same RemoteEndpoint, it can lead to blocking and throws the error:
					//     java.lang.IllegalStateException: Blocking message pending 10000 for BLOCKING
					// To alleviate this, use asynchronous, non-blocking methods that require us to check
					// if the send was successful.
					// https://bugs.eclipse.org/bugs/show_bug.cgi?id=474488
					Future<Void> sendFuture = session.getRemote().sendStringByFuture(message);
					sendFuture.get(3, TimeUnit.SECONDS);	// Wait for completion
				} catch (Exception e) {
					throw new IOException("Message failed to send.", e);
				}
			}
			else {
				throw new IOException("No session is open.");
			}
		}

		public void close() {
			if(isOpen()) {
				session.close();
				session = null;
			}
		}
	}
	
	private class ReconnectClient implements Runnable {
		public void run() {
			reconnecting = true;
			while(!stop && !socket.isOpen()) {
				socket.close();
				try {
					if(isResolvable(serverUri)) {
						socket = new ClientWebSocket();
						logger.warn("Attempting to reconnect to " + serverUri.toString());
						socket.connect();
					}
				} catch (Exception e) {
					logger.error("Failed to reconnect to " + serverUri.toString());
				}
				try { if (!stop) Thread.sleep(5000); } catch (InterruptedException e) {}
			}
			reconnecting = false;
		}
	}
	
	private boolean isResolvable(URI serverUri) {
		String host = null;
		int port = 0;
		try {
			host = serverUri.getHost();
			port = serverUri.getPort();
		} catch (Exception e) {
			logger.warn(e.getMessage());
			return false;
		}
		
		InetSocketAddress addr = new InetSocketAddress(host, port);
		if (addr.isUnresolved()) {
			logger.warn("Cannot resolve host: " + host);
			return false;
		} else {
			return true;
		}
	}
	
	public static void main(String[] args) throws URISyntaxException, InterruptedException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		@SuppressWarnings("rawtypes")
		Enumeration appenders = LogManager.getRootLogger().getAllAppenders();
        if (!appenders.hasMoreElements()) {
        	Logger rootLogger = Logger.getRootLogger();
        	rootLogger.setLevel(Level.INFO);
        	
        	PatternLayout layout = new PatternLayout("%d{ISO8601} %5p %c{1}:%L - %m%n");
        	rootLogger.addAppender(new ConsoleAppender(layout));
        	rootLogger.info("Log4J not configured! Setting up default console configuration");
        }
        
        SslContextFactory sslContextFactory = WebSocketSSLHelper
        										.buildClientSslContextFactory(
        												"@keystores/keystore.with_go_daddy_certs@", 
        												PrivateResourceLoader.getProperty("@websocket/keystore.password@"));
		
		WebSocketClient wsClient = new WebSocketClient("wss://ec2-54-81-80-218.compute-1.amazonaws.com:8445", sslContextFactory);
		wsClient.connect();
		Thread.sleep(1000 * 60);
		int i=0;
		while (true) {
			if (wsClient.isConnected()) {
				wsClient.send("Test message " + i);
				i++;
				Thread.sleep(1000);
			} else {
				System.out.println("Not connected");
				Thread.sleep(1000);
			}
		}
	}
}
