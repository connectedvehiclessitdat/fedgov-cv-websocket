package gov.usdot.cv.websocket;


public class WebSocketConfig {

	public String systemName;
	public String websocketURL;
	public String keystoreFile;
	public String keystorePassword;
	
	public WebSocketConfig() {
		super();
	}

	@Override
	public String toString() {
		return "WebSocketConfig [systemName=" + systemName + ", websocketURL="
				+ websocketURL + ", keystoreFile=" + keystoreFile
				+ ", keystorePassword=" + keystorePassword + "]";
	}
}
