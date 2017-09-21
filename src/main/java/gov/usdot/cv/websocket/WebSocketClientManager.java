package gov.usdot.cv.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class WebSocketClientManager {

	private static final Logger logger = Logger.getLogger(WebSocketClientManager.class);
	
	private List<WebSocketConfig> wsConfigs;
	private Map<String, WebSocketClient> clientMap = new HashMap<String, WebSocketClient>();
	private boolean connected = false;
	
	public WebSocketClientManager(List<WebSocketConfig> wsConfigs) {
		this.wsConfigs = wsConfigs;
		connect();
	}
	
	public void connect() {
		if (!connected) {
			for (WebSocketConfig config: wsConfigs) {
				try {
					SslContextFactory sslContextFactory = null;
					if (config.websocketURL.startsWith("wss")) {
						sslContextFactory = WebSocketSSLHelper.buildClientSslContextFactory(config.keystoreFile, config.keystorePassword);
					}
					WebSocketClient depositClient = new WebSocketClient(config.websocketURL, sslContextFactory);
					
					logger.info("Opening WebSocket connection to: " + config.websocketURL);
					depositClient.connect();
					clientMap.put(config.systemName, depositClient);
					connected = true;
				} catch (Exception e) {
					logger.error("Failed to connect WebSocket Server, config: " + config, e);
				}
			}
			
			// Give a delay of up to 3 seconds to allow all the web sockets to connect
			// since the connection occurs in a background thread.
			boolean allConnected = false;
			int attemptCount = 0;
			while(!allConnected) {
				int notConnectedCount = 0;
				for(WebSocketClient depositClient : clientMap.values()) {
					if(!depositClient.isConnected()) {
						notConnectedCount++;
					}
				}

				if(notConnectedCount == 0) {
					allConnected = true;
				}
				else {
					try{ Thread.sleep(1000); } catch (InterruptedException ignore) {};
					attemptCount++;
					
					if(attemptCount == 3) {
						logger.warn("Some WebSocket connections are taking longer than expected to connect.");
						break;
					}
				}
			}
			
		}
	}
	
	public void close() {
		if (connected) {
			for (WebSocketClient depositClient: clientMap.values()) {
				depositClient.close();
			}
			clientMap.clear();
			connected = false;
		}
	}
	
	public void send(String systemName, String message) {
		connect();
		WebSocketClient wsClient = clientMap.get(systemName);
		if (wsClient != null) {
			try {
				wsClient.send(message);
			} catch (IOException e) {
				logger.error("Failed to send message to WebSocket client for System: " + systemName, e);
			}
		} else {
			logger.error("No WebSocket Client for system: " + systemName);
		}
	}
	
	public void send(String message) {
		connect();
		for (Entry<String, WebSocketClient> clientEntry : clientMap.entrySet()) {
			String systemName = clientEntry.getKey();
			WebSocketClient depositClient = clientEntry.getValue();
			try {
				depositClient.send(message);
			} catch (IOException e) {
				logger.error("Failed to send message to WebSocket client for System: " + systemName, e);
			}
		}
	}
	
	public Set<String> getSystemNames() {
		return clientMap.keySet();
	}
}
