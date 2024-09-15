package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetAddress;
import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.*;
import java.util.logging.Logger;

public class WebServerManager {

    private HttpServer server;
    private boolean isRunning = false;
    private int port = 8000;
    private String host = "localhost"; // Default to all interfaces
    private boolean useSSL = false;
    private static final Logger logger = Logger.getLogger(WebServerManager.class.getName());

    // MIME Types
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put(".html", "text/html");
        MIME_TYPES.put(".css", "text/css");
        MIME_TYPES.put(".js", "application/javascript");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".jpg", "image/jpeg");
        MIME_TYPES.put(".gif", "image/gif");
        MIME_TYPES.put(".ico", "image/x-icon");
    }

    // List to store all received POST data
    private List<Map<String, String>> receivedData = new ArrayList<>();

    // Setters for port, IP and SSL
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

    public void enableSSL(boolean enableSSL) {
        this.useSSL = enableSSL;
    }

    public void startServer() throws Exception {
        if (isRunning) {
            System.out.println("Server is already running.");
            return;
        }

        InetSocketAddress address = new InetSocketAddress(host, port);

        if (useSSL) {
            HttpsServer httpsServer = HttpsServer.create(address, 0);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            char[] password = "123456".toCharArray();

            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream("C:\\Users\\ASUS TUF\\eclipse-workspace\\WebServer\\src\\server\\keystore.jks");
            ks.load(fis, password);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            sslContext.init(kmf.getKeyManagers(), null, null);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            server = httpsServer;
        } else {
            server = HttpServer.create(address, 0);
        }

        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String uri = exchange.getRequestURI().toString();
            logger.info("Request Method: " + method + ", URI: " + uri);

            if ("GET".equalsIgnoreCase(method)) {
                handleGetRequest(exchange, uri);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePostRequest(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        });

        server.setExecutor(null);
        server.start();
        isRunning = true;
        System.out.println("Server started on " + host + ":" + port);
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
    
    private String getHostUrl() {
        return (useSSL ? "https://" : "http://") + host + ":" + port;
    }

    private void handleGetRequest(HttpExchange exchange, String uri) throws IOException {
        if ("/receivedData".equals(uri)) {
            // Return all received POST data
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
                    // Update form action URL
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

        // Parse POST data and store it
        String requestBody = sb.toString();
        Map<String, String> postData = parsePostData(requestBody);
        receivedData.add(postData); // Save the received data

        // Send response to client
        String responseMessage = "Received POST data: " + postData;
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, responseMessage.length());

        OutputStream os = exchange.getResponseBody();
        os.write(responseMessage.getBytes());
        os.close();
    }

    // Method to parse POST data
    private Map<String, String> parsePostData(String requestBody) {
        Map<String, String> postData = new HashMap<>();
        String[] pairs = requestBody.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                postData.put(keyValue[0], keyValue[1]);
            }
        }
        return postData;
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
