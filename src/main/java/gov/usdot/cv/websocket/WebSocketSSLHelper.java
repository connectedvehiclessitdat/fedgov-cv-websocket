package gov.usdot.cv.websocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.log4j.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import gov.usdot.cv.resources.PrivateResourceLoader;

public class WebSocketSSLHelper {

	private static final Logger logger = Logger.getLogger(WebSocketSSLHelper.class);
	
	public static SslContextFactory buildServerSslContextFactory(String keystoreFile,
			String password) throws KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
		
		SSLContext sslContext;
	    KeyManagerFactory keyMgrFactory;
	    KeyStore keyStore;
	    InputStream fin = null;
	    SslContextFactory sslContextFactory = new SslContextFactory();
	    try {
			sslContext = SSLContext.getInstance("TLS");
		    keyMgrFactory = KeyManagerFactory.getInstance("SunX509");
		    fin = getFileAsStream(keystoreFile);
		    keyStore = KeyStore.getInstance("JKS");
		    keyStore.load(fin, password.toCharArray());
			keyMgrFactory.init(keyStore, password.toCharArray());
			sslContext.init(keyMgrFactory.getKeyManagers(), null, null);
			
			sslContextFactory.setSslContext(sslContext);
			
			return sslContextFactory;
	    } finally {
	    	if (fin != null)
	    		fin.close();
		}
	}
	
	public static SslContextFactory buildClientSslContextFactory(String keystoreFile, String password)
					throws KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		SSLContext sslContext;
		TrustManagerFactory trustMgrFactory;
	    KeyStore keyStore;
	    InputStream fin = null;
	    SslContextFactory sslContextFactory = new SslContextFactory();
	    try {
			sslContext = SSLContext.getInstance("TLS");
			trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		    fin = getFileAsStream(keystoreFile);
		    keyStore = KeyStore.getInstance("JKS");
		    keyStore.load(fin, password.toCharArray());		    
		    trustMgrFactory.init(keyStore);
			sslContext.init(null, trustMgrFactory.getTrustManagers(), null);
			
			sslContextFactory.setSslContext(sslContext);
			
			return sslContextFactory;
	    } finally {
	    	if (fin != null)
	    		fin.close();
		}
	}
	
	public static InputStream getFileAsStream(String fileName)  {
		InputStream is = null;
		
		if(PrivateResourceLoader.isPrivateResource(fileName)) {
			is = PrivateResourceLoader.getFileAsStream(fileName);
		}
		else {
			File f = new File(fileName);
			logger.info("Attempting to find file " + fileName);
			if (f.exists()) {
				logger.debug("Loading file from the file system " + f.getAbsolutePath());
				try {
					is = new FileInputStream(f);
				} catch (FileNotFoundException e) {
					logger.warn(e);
				}
			} else {
				logger.debug("File not found on file system, checking on classpath...");
				is = WebSocketSSLHelper.class.getClassLoader().getResourceAsStream(fileName);
			}
		}
	
		if (is == null) {
			logger.error("File " + fileName + " could not be found");
		}
		
		return is;
	}
}
