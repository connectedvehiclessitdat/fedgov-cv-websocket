package gov.usdot.cv.websocket;

import java.io.IOException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket(maxIdleTime=0)
public abstract class BaseWebSocket {
	protected Session session;
	
	public boolean isOpen() {
		return (session != null && session.isOpen());
	}
	
	public abstract void send(String message) throws IOException;
}
