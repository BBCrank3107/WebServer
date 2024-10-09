package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpExchange;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class Server {

	private HttpServer server;
	private boolean isRunning = false;
	private int port = 8000;
	private String host = "localhost";
	private boolean useSSL = false;
	private static final Logger logger = Logger.getLogger(Server.class.getName());

	private static final Map<String, String> MIME_TYPES = Map.of(".html", "text/html", ".css", "text/css", ".js",
			"application/javascript", ".png", "image/png", ".jpg", "image/jpeg", ".gif", "image/gif", ".ico",
			"image/x-icon");

	private List<Map<String, String>> receivedData = new ArrayList<>();

	// Server Configuration
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

	private String getHostUrl() {
		return (useSSL ? "https://" : "http://") + host + ":" + port;
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

		setupContexts();
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

	private void setupContexts() {
		server.createContext("/", this::handleRootRequest);
		server.createContext("/register", this::handleRegisterRequest);
		server.createContext("/login", this::handleLoginRequest);
		server.createContext("/upload", this::handleUploadRequest);
		server.createContext("/files", this::handleFilesRequest);
		server.createContext("/delete", this::handleDeleteRequest);
		server.createContext("/logout", this::handleLogoutRequest);
	}
	
	private Consumer<String> logConsumer;

    public void setLogConsumer(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
    }
	
    private void logClientInfo(HttpExchange exchange, String username, String action) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        String logMessage = "User: " + username + " from IP: " + clientIP + " performed action: " + action;
        logger.info(logMessage);
        if (logConsumer != null) {
            logConsumer.accept(logMessage);
        }
    }

    private void handleRootRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI().toString();
        String addressIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        String logMessage = "Request Method: " + method + ", URI: " + uri + ", IP_ADDRESS: " + addressIP;
        logger.info(logMessage);
        if (logConsumer != null) {
            logConsumer.accept(logMessage);
        }
		if ("GET".equalsIgnoreCase(method)) {
			handleGetRequest(exchange, uri);
		} else if ("POST".equalsIgnoreCase(method)) {
			handlePostRequest(exchange);
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	private void handleRegisterRequest(HttpExchange exchange) throws IOException {
	    Map<String, String> postData = parseRequestBody(exchange);
	    String username = postData.get("username");
	    logClientInfo(exchange, username, "register");

	    if ("POST".equals(exchange.getRequestMethod())) {
	        String password = postData.get("password");

	        if (saveAccount(username, password)) {
	            createUserDirectory(username);
	            sendResponse(exchange, 200, "success");
	        } else {
	            sendResponse(exchange, 400, "Registration failed: User already exists.");
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}

	private void handleLoginRequest(HttpExchange exchange) throws IOException {
	    Map<String, String> postData = parseRequestBody(exchange);
	    String username = postData.get("username");
	    logClientInfo(exchange, username, "login");

	    if ("POST".equals(exchange.getRequestMethod())) {
	        String password = postData.get("password");

	        if (validateUser(username, password)) {
	            sendResponse(exchange, 200, "success");
	        } else {
	            sendResponse(exchange, 401, "Invalid username or password.");
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}

	private void handleUploadRequest(HttpExchange exchange) throws IOException {
	    String query = exchange.getRequestURI().getQuery();
	    Map<String, String> queryParams = parseData(query);
	    String username = queryParams.get("username");
	    logClientInfo(exchange, username, "upload");

	    if ("POST".equals(exchange.getRequestMethod())) {
	        String fileName = queryParams.get("filename");

	        File userDir = createUserDirectory(username);
	        File uploadedFile = new File(userDir, fileName);

	        try (InputStream is = exchange.getRequestBody();
	             FileOutputStream fos = new FileOutputStream(uploadedFile)) {
	            byte[] buffer = new byte[4096];
	            int bytesRead;
	            while ((bytesRead = is.read(buffer)) != -1) {
	                fos.write(buffer, 0, bytesRead);
	            }
	            sendResponse(exchange, 200, "File uploaded successfully!");
	        } catch (IOException e) {
	            sendResponse(exchange, 500, "File upload failed.");
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}

	private void handleFilesRequest(HttpExchange exchange) throws IOException {
	    String query = exchange.getRequestURI().getQuery();
	    String username = extractUsername(query);
	    logClientInfo(exchange, username, "retrieve files");

	    if ("GET".equals(exchange.getRequestMethod())) {
	        File userDirectory = new File("html/" + username);

	        if (userDirectory.exists() && userDirectory.isDirectory()) {
	            File[] files = userDirectory.listFiles((dir, name) -> name.endsWith(".html"));

	            if (files != null && files.length > 0) {
	                StringBuilder response = new StringBuilder();
	                for (File file : files) {
	                    response.append(file.getName()).append("\n");
	                }
	                exchange.sendResponseHeaders(200, response.toString().getBytes().length);
	                OutputStream os = exchange.getResponseBody();
	                os.write(response.toString().getBytes());
	                os.close();
	            } else {
	                String response = "No HTML files found";
	                exchange.sendResponseHeaders(200, response.getBytes().length);
	                OutputStream os = exchange.getResponseBody();
	                os.write(response.getBytes());
	                os.close();
	            }
	        } else {
	            exchange.sendResponseHeaders(404, -1);
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}

	private void handleDeleteRequest(HttpExchange exchange) throws IOException {
	    String query = exchange.getRequestURI().getQuery();
	    Map<String, String> queryParams = parseData(query);
	    String username = queryParams.get("username");
	    logClientInfo(exchange, username, "delete file");

	    if ("DELETE".equals(exchange.getRequestMethod())) {
	        String fileName = queryParams.get("filename");

	        File userDir = new File("html/" + username);
	        if (userDir.exists()) {
	            File fileToDelete = new File(userDir, fileName);
	            if (fileToDelete.exists() && fileToDelete.delete()) {
	                sendResponse(exchange, 200, "File deleted successfully.");
	            } else {
	                sendResponse(exchange, 404, "File not found.");
	            }
	        } else {
	            sendResponse(exchange, 404, "User directory not found.");
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}
	
	private void handleLogoutRequest(HttpExchange exchange) throws IOException {
	    String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
	    Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
	    String username = queryParams.get("username");

	    logger.info("User " + username + " from IP: " + clientIP + " performed action: logout");
	    sendResponse(exchange, 200, "Logout successful");
	}

	private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", "text/plain");
		exchange.sendResponseHeaders(statusCode, message.length());
		OutputStream os = exchange.getResponseBody();
		os.write(message.getBytes());
		os.close();
	}

	private void handleGetRequest(HttpExchange exchange, String uri) throws IOException {
		if ("/receivedData".equals(uri)) {
			StringBuilder responseBuilder = new StringBuilder("<h1>Received POST Data</h1><ul>");
			for (Map<String, String> dataMap : receivedData) {
				responseBuilder.append("<li>").append(dataMap).append("</li>");
			}
			responseBuilder.append("</ul>");

			String response = responseBuilder.toString();
			exchange.getResponseHeaders().set("Content-Type", "text/html");
			exchange.sendResponseHeaders(200, response.length());
			OutputStream os = exchange.getResponseBody();
			os.write(response.getBytes());
			os.close();
		} else {
			String filePath = "html" + uri;
			File file = new File(filePath);

			if (file.exists() && !file.isDirectory()) {
				String mimeType = getMimeType(filePath);
				String response;

				if (mimeType.equals("text/html")) {
					response = new String(Files.readAllBytes(Paths.get(filePath)));
					response = response.replace("action=\"host\"", "action=\"" + getHostUrl() + "\"");
				} else {
					byte[] responseBytes = Files.readAllBytes(Paths.get(filePath));
					response = new String(responseBytes);
				}

				exchange.getResponseHeaders().set("Content-Type", mimeType);
				exchange.sendResponseHeaders(200, response.length());
				OutputStream os = exchange.getResponseBody();
				os.write(response.getBytes());
				os.close();
			} else {
				String notFoundMessage = "<h1>404 Not Found</h1>";
				exchange.getResponseHeaders().set("Content-Type", "text/html");
				exchange.sendResponseHeaders(404, notFoundMessage.length());
				OutputStream os = exchange.getResponseBody();
				os.write(notFoundMessage.getBytes());
				os.close();
			}
		}
	}

	private void handlePostRequest(HttpExchange exchange) throws IOException {
		InputStream is = exchange.getRequestBody();
		StringBuilder sb = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = reader.readLine()) != null) {
			sb.append(line);
		}

		String requestBody = sb.toString();
		Map<String, String> postData = parseData(requestBody);
		receivedData.add(postData);

		String responseMessage = "Received POST data: " + postData;
		exchange.getResponseHeaders().set("Content-Type", "text/plain");
		exchange.sendResponseHeaders(200, responseMessage.length());

		OutputStream os = exchange.getResponseBody();
		os.write(responseMessage.getBytes());
		os.close();
	}

	private boolean validateUser(String username, String password) {
		try (BufferedReader reader = new BufferedReader(new FileReader("accounts.txt"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(":");
				if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean saveAccount(String username, String password) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("accounts.txt", true))) {
			if (isUserExists(username)) {
				return false;
			}
			writer.write(username + ":" + password);
			writer.newLine();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private boolean isUserExists(String username) {
		try (BufferedReader reader = new BufferedReader(new FileReader("accounts.txt"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith(username + ":")) {
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private File createUserDirectory(String username) {
		File userDir = new File("html/" + username);
		if (!userDir.exists()) {
			userDir.mkdir();
		}
		return userDir;
	}

	private String extractUsername(String query) {
		if (query != null && query.startsWith("username=")) {
			return query.split("=")[1];
		}
		return null;
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

	private Map<String, String> parseRequestBody(HttpExchange exchange) throws IOException {
		InputStream is = exchange.getRequestBody();
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
		}
		return parseData(sb.toString());
	}

	private Map<String, String> parseData(String data) {
		Map<String, String> parsedData = new HashMap<>();
		String[] pairs = data.split("&");
		for (String pair : pairs) {
			String[] keyValue = pair.split("=");
			if (keyValue.length == 2) {
				parsedData.put(keyValue[0], keyValue[1]);
			}
		}
		return parsedData;
	}

	private String getMimeType(String filePath) {
		int lastDot = filePath.lastIndexOf('.');
		if (lastDot != -1) {
			String extension = filePath.substring(lastDot);
			return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
		}
		return "application/octet-stream";
	}
}
