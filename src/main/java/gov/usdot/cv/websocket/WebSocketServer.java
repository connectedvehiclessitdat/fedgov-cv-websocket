package gov.usdot.cv.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import gov.usdot.cv.resources.PrivateResourceLoader;

public class WebSocketServer {

	private static final Logger logger = Logger.getLogger(WebSocketServer.class);
	
	private Server server;
	private static List<WebSocketMessageProcessor> messageProcessors = new CopyOnWriteArrayList<WebSocketMessageProcessor>();
	private static final Map<String, ServerWebSocket> webSocketConnectionMap = new ConcurrentHashMap<String, ServerWebSocket>(16, 0.9f, 1);
	
	public WebSocketServer(int port) {
		this(port, null);
	}
	
	public WebSocketServer(int port, SslContextFactory sslContextFactory) {
		InetSocketAddress wildcardInetAddress = new InetSocketAddress(port);
		
		if (sslContextFactory != null) {
			server = new Server();
			
			// Set up SSL & HTTP Connection Factories
			SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
			HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(new HttpConfiguration());
			
			// Configure a connector for the server to use the SSL Connection Factory 
			ServerConnector sslConnector = new ServerConnector(server, sslConnectionFactory, httpConnectionFactory);
			sslConnector.setHost(wildcardInetAddress.getHostName());
			sslConnector.setPort(port);
			server.addConnector(sslConnector);
		}
		else {
			server = new Server(wildcardInetAddress);
		}
		
		// Register our WebSocket Class to be used by the the server
		WebSocketHandler wsHandler = new WebSocketHandler() {
			@Override
			public void configure(WebSocketServletFactory factory) {
				factory.register(ServerWebSocket.class);
			}
		};
		server.setHandler(wsHandler);
	}
	
	public void start() {
		try {
			server.start();
		} catch (Exception e) {
			logger.error("Failed to start WebSocket Server.", e);
		}
	}
	
	public void stop() {
		try {
			server.stop();
			messageProcessors.clear();
		} catch (Exception e) {
			logger.error("Failed to stop WebSocket Server.", e);
		}
	}
	
	public void addMessageProcessor(WebSocketMessageProcessor messageProcessor) {
		messageProcessors.add(messageProcessor);
	}
	
	public void sendMessage(String message) {
		for(ServerWebSocket socket : webSocketConnectionMap.values()) {
			sendMessage(socket, message);
		}
	}
	
	public void sendMessage(BaseWebSocket socket, String message) {
		if(message != null) {
			if(socket.isOpen()) {
				try {
					socket.send(message);
				} catch (Exception e) {
					logger.error("Failed to send message to session: " + ((ServerWebSocket)socket).webSocketID + " error: " + e , e);
				}
			}
			else {
				logger.warn("WebSocket connection " + ((ServerWebSocket)socket).webSocketID + " is closed");
			}
		}
	}
	
	public Collection<ServerWebSocket> connections() {
		return webSocketConnectionMap.values();
	}
	
	@WebSocket
	public static class ServerWebSocket extends BaseWebSocket {
		private String webSocketID;
		
		// Required for reflection
		public ServerWebSocket() { }
		
		@OnWebSocketConnect
		public void onOpen(Session session) {
			this.webSocketID = String.format("l(%s)<->r(%s)", 
												session.getLocalAddress().toString().split("/")[1],
												session.getRemoteAddress().toString().split("/")[1]);
			this.session = session;
			this.session.setIdleTimeout(0);		// Don't timeout
			
			logger.info(webSocketID + " connected");
			webSocketConnectionMap.put(webSocketID, this);
			logger.info("WebSocket connection count is " + webSocketConnectionMap.size());
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
		
		@OnWebSocketMessage
		public void onMessage(String message) {
			logger.debug("Received message: " + message);
			// TODO need to multi thread this otherwise 1 client can block all
			for (WebSocketMessageProcessor messageProcessor: messageProcessors) {
				messageProcessor.processMessage(this, message);
			}
		}
		
		@OnWebSocketError
		public void onError(Throwable t) {
			logger.error("WebSocket Server error for connection " + webSocketID, t);
		}
		
		@OnWebSocketClose
		public void onClose(int statusCode, String reason) {
			if(isOpen()) {
				session.close();
				session = null;
			}
			logger.info(webSocketID + " closed");
			webSocketConnectionMap.remove(webSocketID);
			logger.info("WebSocket connection count is " + webSocketConnectionMap.size());
		}
	}
	
	public static void main(String[] args) throws InterruptedException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
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
        										.buildServerSslContextFactory(
													"@keystores/keystore.with_go_daddy_certs@", 
													PrivateResourceLoader.getProperty("@websocket/keystore.password@"));
        
		WebSocketServer wsServer = new WebSocketServer(8445, sslContextFactory);
		wsServer.start();
		while (true) {
			Thread.sleep(1000);
		}
	}
}
