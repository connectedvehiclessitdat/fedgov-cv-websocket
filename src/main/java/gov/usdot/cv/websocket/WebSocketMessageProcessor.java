package gov.usdot.cv.websocket;

public interface WebSocketMessageProcessor {

	public void processMessage(BaseWebSocket socket, String message);
}
