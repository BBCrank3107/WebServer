package server;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WebServer {

    public static void main(String[] args) throws IOException {
        // Khởi tạo HttpServer tại cổng 8000
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // Tạo context để xử lý request đến địa chỉ "/"
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                // Lấy URI yêu cầu
                String uri = exchange.getRequestURI().toString();
                
                // Đường dẫn tới file HTML trong hệ thống
                String filePath = "html" + uri;

                // Kiểm tra nếu file tồn tại, nếu không trả về 404
             // Kiểm tra nếu file tồn tại, nếu không trả về 404
                if (Files.exists(Paths.get(filePath))) {
                    byte[] response = Files.readAllBytes(Paths.get(filePath));
                    
                    // Set Content-Type cho HTML
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    
                    exchange.sendResponseHeaders(200, response.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response);
                    os.close();
                } else {
                    String notFoundMessage = "<h1>404 Not Found</h1>";
                    exchange.sendResponseHeaders(404, notFoundMessage.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(notFoundMessage.getBytes());
                    os.close();
                }
            }
        });

        // Bắt đầu server
        server.setExecutor(null);
        server.start();
        System.out.println("Server is running on http://localhost:8000");
    }
}
