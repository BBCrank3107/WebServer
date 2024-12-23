package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;

public class Server {

	private HttpServer server;
	private boolean isRunning = false;
	private int port = 8000;
	private String host = "localhost";
	public boolean useSSL = false;
	private ServerContextSetup contextSetup;
	private final RequestHandlers requestHandlers;

	public Server() {
		this.requestHandlers = new RequestHandlers(this);
		this.contextSetup = new ServerContextSetup(requestHandlers);
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getHost() {
		return host;
	}

	public String getHostUrl() {
		return (useSSL ? "https://" : "http://") + host + ":" + port;
	}
	
	public RequestHandlers getRequestHandlers() {
        return requestHandlers;
    }

	public void enableSSL(boolean enableSSL) {
		this.useSSL = enableSSL;
	}

	public void startServer() throws Exception {
		if (isRunning) {
			System.out.println("Server is already running.");
			return;
		}
		InetSocketAddress address = new InetSocketAddress(host, port);
		server = useSSL ? createHttpsServer(address) : HttpServer.create(address, 0);
		contextSetup.setupContexts(server);
		server.setExecutor(null);
		server.start();
		isRunning = true;
		System.out.println("Server started on " + host + ":" + port);
	}

	private HttpsServer createHttpsServer(InetSocketAddress address) throws Exception {
		HttpsServer httpsServer = HttpsServer.create(address, 0);
		SSLContext sslContext = SSLContext.getInstance("TLS");
		char[] password = "123456".toCharArray();

		KeyStore ks = KeyStore.getInstance("JKS");
		try (FileInputStream fis = new FileInputStream("path/to/keystore.jks")) {
			ks.load(fis, password);
		}

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks, password);
		sslContext.init(kmf.getKeyManagers(), null, null);
		httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));

		return httpsServer;
	}

	public void stopServer() {
		if (isRunning) {
			server.stop(0);
			isRunning = false;
			System.out.println("Server stopped");
		} else {
			System.out.println("Server is not running.");
		}
	}

	public void restartServer() throws Exception {
		stopServer();
		startServer();
	}

	public boolean isRunning() {
		return isRunning;
	}
}