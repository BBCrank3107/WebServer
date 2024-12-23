package server;

import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RequestHandlers {
	private final Server server;

	public RequestHandlers(Server server) {
		this.server = server;
	}

	public static final Map<String, String> MIME_TYPES = Map.of(".html", "text/html", ".css", "text/css", ".js",
			"application/javascript", ".png", "image/png", ".jpg", "image/jpeg", ".gif", "image/gif", ".ico",
			"image/x-icon");

	public List<Map<String, String>> receivedData = new ArrayList<>();
	public static final Logger logger = Logger.getLogger(Server.class.getName());

	private Consumer<String> logConsumer;

	public void setLogConsumer(Consumer<String> logConsumer) {
		this.logConsumer = logConsumer;
	}

	// Methods
	private void logClientInfo(HttpExchange exchange, String email, String action) {
		String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
		String logMessage = "Email: " + URLDecoder.decode(email, StandardCharsets.UTF_8) + " from IP: " + clientIP
				+ " performed action: " + action;
		logger.info(logMessage);
		if (logConsumer != null) {
			logConsumer.accept(logMessage);
		}
	}

	private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", "text/plain");
		exchange.sendResponseHeaders(statusCode, message.length());
		OutputStream os = exchange.getResponseBody();
		os.write(message.getBytes());
		os.close();
	}

	private String getMimeType(String filePath) {
		int lastDot = filePath.lastIndexOf('.');
		if (lastDot != -1) {
			String extension = filePath.substring(lastDot);
			return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
		}
		return "application/octet-stream";
	}

	private Map<String, String> parseData(String data) {
	    Map<String, String> result = new HashMap<>();
	    String[] pairs = data.split("&");
	    for (String pair : pairs) {
	        String[] keyValue = pair.split("=");
	        if (keyValue.length == 2) {
	            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
	            String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
	            result.put(key, value);
	        }
	    }
	    return result;
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
	    String requestBody = sb.toString();
	    System.out.println("Request Body: " + requestBody);
	    return parseData(requestBody);
	}

	public void handleRootRequest(HttpExchange exchange) throws IOException {
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
					response = response.replace("action=\"host\"", "action=\"" + server.getHostUrl() + "\"");
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

	// Request Account
	public void handleRegisterRequest(HttpExchange exchange) throws IOException {
		Map<String, String> postData = parseRequestBody(exchange);
		String email = postData.get("email");
		String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);

		if ("POST".equals(exchange.getRequestMethod())) {
			String password = postData.get("password");
			String username = postData.get("username");

			if (saveAccount(username, decodedEmail, password)) {
				createUserDirectory(username);
				sendResponse(exchange, 200, "success");
				logClientInfo(exchange, decodedEmail, "register");
			} else {
				sendResponse(exchange, 400, "Registration failed: User already exists.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	public void handleLoginRequest(HttpExchange exchange) throws IOException {
		Map<String, String> postData = parseRequestBody(exchange);
		String email = postData.get("email");
		String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);

		if ("POST".equals(exchange.getRequestMethod())) {
			String password = postData.get("password");

			if (validateUser(decodedEmail, password)) {
				sendResponse(exchange, 200, "success");
				logClientInfo(exchange, decodedEmail, "login");
			} else {
				sendResponse(exchange, 401, "Invalid email or password.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	public void handleLogoutRequest(HttpExchange exchange) throws IOException {
		Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
		String username = queryParams.get("username");
		logClientInfo(exchange, username, "logout");
		sendResponse(exchange, 200, "Logout successful");
	}

	public void handleGetUserNameRequest(HttpExchange exchange) throws IOException {
		if ("GET".equals(exchange.getRequestMethod())) {
			Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
			String email = queryParams.get("email");

			if (email != null) {
				String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);
				String username = getUserNameByEmail(decodedEmail);

				if (username != null) {
					sendResponse(exchange, 200, username);
				} else {
					sendResponse(exchange, 404, "User not found.");
				}
			} else {
				sendResponse(exchange, 400, "Missing email parameter.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	private String getUserNameByEmail(String email) {
		try (Connection conn = DatabaseConnection.getConnection();
				PreparedStatement stmt = conn.prepareStatement("SELECT UserName FROM user WHERE UserEmail = ?")) {
			stmt.setString(1, email);
			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				return rs.getString("UserName");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private boolean validateUser(String email, String password) {
		try (Connection conn = DatabaseConnection.getConnection();
				PreparedStatement stmt = conn
						.prepareStatement("SELECT * FROM user WHERE UserEmail = ? AND UserPass = ?")) {
			stmt.setString(1, email);
			stmt.setString(2, password);
			ResultSet rs = stmt.executeQuery();

			return rs.next();
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	private boolean saveAccount(String username, String email, String password) {
		try (Connection conn = DatabaseConnection.getConnection();
				PreparedStatement checkStmt = conn.prepareStatement("SELECT * FROM user WHERE UserEmail = ?");
				PreparedStatement insertStmt = conn
						.prepareStatement("INSERT INTO user (UserName, UserEmail, UserPass) VALUES (?, ?, ?)")) {

			checkStmt.setString(1, email);
			ResultSet rs = checkStmt.executeQuery();
			if (rs.next()) {
				return false;
			}

			insertStmt.setString(1, username);
			insertStmt.setString(2, email);
			insertStmt.setString(3, password);
			insertStmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	private File createUserDirectory(String username) {
		File userDir = new File("html/" + username);
		if (!userDir.exists()) {
			userDir.mkdir();
		}
		return userDir;
	}

	// Request File and Project
	public void handleCreateProjectRequest(HttpExchange exchange) throws IOException {
		if ("POST".equals(exchange.getRequestMethod())) {
			Map<String, String> postData = parseRequestBody(exchange);
			String username = postData.get("username");
			String projectName = postData.get("projectName");
			String email = postData.get("email");
			String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);

			if (username == null || projectName == null) {
				sendResponse(exchange, 400, "Missing username or projectName");
				return;
			}

			File userDir = new File("html/" + username);
			if (!userDir.exists()) {
				userDir.mkdirs();
			}

			File projectDir = new File(userDir, projectName);
			if (!projectDir.exists()) {
				projectDir.mkdirs();
				sendResponse(exchange, 200, "Project created successfully!");
				logClientInfo(exchange, decodedEmail, "create project " + projectName);
			} else {
				sendResponse(exchange, 409, "Project already exists.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	public void handleListProjectsRequest(HttpExchange exchange) throws IOException {
		if ("GET".equals(exchange.getRequestMethod())) {
			Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
			String username = queryParams.get("username");

			if (username != null) {
				File userDir = new File("html/" + username);
				if (userDir.exists() && userDir.isDirectory()) {
					File[] directories = userDir.listFiles(File::isDirectory);
					if (directories != null) {
						List<String> projectNames = new ArrayList<>();
						for (File dir : directories) {
							projectNames.add(dir.getName());
						}

						String response = String.join(",", projectNames);
						sendResponse(exchange, 200, response);
					} else {
						sendResponse(exchange, 404, "No projects found.");
					}
				} else {
					sendResponse(exchange, 404, "User directory not found.");
				}
			} else {
				sendResponse(exchange, 400, "Missing username parameter.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	public void handleUploadRequest(HttpExchange exchange) throws IOException {
		Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
		String username = queryParams.get("username");
		String projectName = queryParams.get("project");
		String fileName = queryParams.get("filename");
		String email = queryParams.get("email");
		String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);
		long fileSize = Long.parseLong(queryParams.get("fileSize"));

		if ("POST".equals(exchange.getRequestMethod())) {
			File projectDir = new File("html/" + username + "/" + projectName);
			if (!projectDir.exists()) {
				projectDir.mkdirs();
			}

			File uploadedFile = new File(projectDir, fileName);

			try (InputStream is = exchange.getRequestBody();
					FileOutputStream fos = new FileOutputStream(uploadedFile)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = is.read(buffer)) != -1) {
					fos.write(buffer, 0, bytesRead);
				}
				saveFileToDatabase(username, projectName, fileName, fileSize);

				sendResponse(exchange, 200, "File uploaded successfully!");
				logClientInfo(exchange, decodedEmail, "upload " + fileName + " to " + projectName);
			} catch (IOException e) {
				sendResponse(exchange, 500, "File upload failed.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	public void handleListFilesInProjectRequest(HttpExchange exchange) throws IOException {
	    if ("GET".equals(exchange.getRequestMethod())) {
	        Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
	        String username = queryParams.get("username");
	        String projectName = queryParams.get("projectName");

	        if (username != null && projectName != null) {
	            List<String> fileInfoList = new ArrayList<>();

	            try (Connection conn = DatabaseConnection.getConnection()) {
	                String sqlUserId = "SELECT UserID FROM user WHERE UserName = ?";
	                int userId;
	                
	                try (PreparedStatement stmtUser = conn.prepareStatement(sqlUserId)) {
	                    stmtUser.setString(1, username);
	                    try (ResultSet rsUser = stmtUser.executeQuery()) {
	                        if (rsUser.next()) {
	                            userId = rsUser.getInt("UserID");
	                        } else {
	                            sendResponse(exchange, 404, "User not found.");
	                            return;
	                        }
	                    }
	                }

	                String sqlFileInfo = "SELECT FileName, FileSize, TimeUpload FROM file WHERE UserID = ? AND ProjectName = ?";
	                try (PreparedStatement stmtFile = conn.prepareStatement(sqlFileInfo)) {
	                    stmtFile.setInt(1, userId);
	                    stmtFile.setString(2, projectName);

	                    try (ResultSet rsFile = stmtFile.executeQuery()) {
	                        while (rsFile.next()) {
	                            String fileName = rsFile.getString("FileName");
	                            long fileSize = rsFile.getLong("FileSize");
	                            Timestamp timeUpload = rsFile.getTimestamp("TimeUpload");

	                            long elapsedTimeMillis = System.currentTimeMillis() - timeUpload.getTime();
	                            String elapsedTime = formatElapsedTime(elapsedTimeMillis);

	                            String fileInfo = String.format("%-30s %-15s %20s", fileName, formatFileSize(fileSize), elapsedTime);
	                            fileInfoList.add(fileInfo);
	                        }
	                    }
	                }

	                String response = String.join(",", fileInfoList);
	                sendResponse(exchange, 200, response);
	            } catch (SQLException e) {
	                e.printStackTrace();
	                sendResponse(exchange, 500, "Database error");
	            }
	        } else {
	            sendResponse(exchange, 400, "Missing username or projectName parameter.");
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}

	private String formatElapsedTime(long elapsedTimeMillis) {
	    long seconds = (elapsedTimeMillis / 1000) % 60;
	    long minutes = (elapsedTimeMillis / (1000 * 60)) % 60;
	    long hours = (elapsedTimeMillis / (1000 * 60 * 60)) % 24;
	    long days = elapsedTimeMillis / (1000 * 60 * 60 * 24);

	    if (days > 0) return days + " days ago";
	    else if (hours > 0) return hours + " hours ago";
	    else if (minutes > 0) return minutes + " minutes ago";
	    else return seconds + " seconds ago";
	}

	private String formatFileSize(long fileSize) {
	    if (fileSize >= 1024 * 1024) {
	        return (fileSize / (1024 * 1024)) + " MB";
	    } else if (fileSize >= 1024) {
	        return (fileSize / 1024) + " KB";
	    } else {
	        return fileSize + " bytes";
	    }
	}

	public void handleDeleteFileRequest(HttpExchange exchange) throws IOException {
		if ("DELETE".equals(exchange.getRequestMethod())) {
			String query = exchange.getRequestURI().getQuery();
			Map<String, String> queryParams = parseData(query);
			String username = queryParams.get("username");
			String projectName = queryParams.get("projectName");
			String fileName = queryParams.get("fileName");
			String email = queryParams.get("email");
			String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);

			if (username != null && projectName != null && fileName != null) {
				File projectDir = new File("html/" + username + "/" + projectName);
				File fileToDelete = new File(projectDir, fileName);

				if (fileToDelete.exists() && fileToDelete.isFile()) {
					boolean deleted = fileToDelete.delete();
					if (deleted) {
						boolean dbDeleted = deleteFileFromDatabase(username, projectName, fileName);
						if (dbDeleted) {
							sendResponse(exchange, 200, "File deleted successfully.");
							logClientInfo(exchange, decodedEmail, "delete " + fileName + " from " + projectName);
						} else {
							sendResponse(exchange, 500, "Failed to delete file from database.");
						}
					} else {
						sendResponse(exchange, 500, "Failed to delete file.");
					}
				} else {
					sendResponse(exchange, 404, "File not found.");
				}
			} else {
				sendResponse(exchange, 400, "Missing username, projectName, or fileName parameter.");
			}
		} else {
			exchange.sendResponseHeaders(405, -1);
		}
	}

	public void handleDeleteProjectRequest(HttpExchange exchange) throws IOException {
	    if ("DELETE".equals(exchange.getRequestMethod())) {
	        String query = exchange.getRequestURI().getQuery();
	        Map<String, String> queryParams = parseData(query);
	        String username = queryParams.get("username");
	        String projectName = queryParams.get("projectName");
			String email = queryParams.get("email");
			String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);

	        if (username != null && projectName != null) {
	            boolean projectExists = checkProjectExists(username, projectName);

	            if (projectExists) {
	                boolean dbDeleted = deleteProjectFromDatabase(username, projectName);
	                if (!dbDeleted) {
	                    sendResponse(exchange, 500, "Failed to delete project in database.");
	                    return;
	                }
	            }

	            File projectDir = new File("html/" + username + "/" + projectName);
	            if (projectDir.exists() && projectDir.isDirectory()) {
	                boolean deleted = deleteDirectory(projectDir);
	                if (deleted) {
	                    sendResponse(exchange, 200, "Project deleted successfully.");
	                    logClientInfo(exchange, decodedEmail, "delete project " + projectName);
	                } else {
	                    sendResponse(exchange, 500, "Failed to delete project files.");
	                }
	            } else {
	                sendResponse(exchange, 404, "Project not found on the server.");
	            }
	        } else {
	            sendResponse(exchange, 400, "Missing username or projectName parameter.");
	        }
	    } else {
	        exchange.sendResponseHeaders(405, -1);
	    }
	}

	private void saveFileToDatabase(String username, String projectName, String fileName, long fileSize) {
	    String sqlUserId = "SELECT UserID FROM user WHERE UserName = ?";
	    String sqlCheckFile = "SELECT FileID FROM file WHERE FileName = ? AND ProjectName = ? AND UserID = ?";
	    String sqlUpdateFile = "UPDATE file SET FileSize = ?, TimeUpload = NOW() WHERE FileID = ?";
	    String sqlInsertFile = "INSERT INTO file (FileName, FileSize, TimeUpload, ProjectName, UserID) VALUES (?, ?, NOW(), ?, ?)";

	    try (Connection conn = DatabaseConnection.getConnection()) {
	        try (PreparedStatement stmtUserId = conn.prepareStatement(sqlUserId)) {
	            stmtUserId.setString(1, username);
	            ResultSet rs = stmtUserId.executeQuery();

	            if (rs.next()) {
	                int userId = rs.getInt("UserID");

	                try (PreparedStatement stmtCheckFile = conn.prepareStatement(sqlCheckFile)) {
	                    stmtCheckFile.setString(1, fileName);
	                    stmtCheckFile.setString(2, projectName);
	                    stmtCheckFile.setInt(3, userId);
	                    ResultSet rsFile = stmtCheckFile.executeQuery();

	                    if (rsFile.next()) {
	                        int fileId = rsFile.getInt("FileID");
	                        try (PreparedStatement stmtUpdateFile = conn.prepareStatement(sqlUpdateFile)) {
	                            stmtUpdateFile.setLong(1, fileSize);
	                            stmtUpdateFile.setInt(2, fileId);
	                            stmtUpdateFile.executeUpdate();
	                        }
	                    } else {
	                        try (PreparedStatement stmtInsertFile = conn.prepareStatement(sqlInsertFile)) {
	                            stmtInsertFile.setString(1, fileName);
	                            stmtInsertFile.setLong(2, fileSize);
	                            stmtInsertFile.setString(3, projectName);
	                            stmtInsertFile.setInt(4, userId);
	                            stmtInsertFile.executeUpdate();
	                        }
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}

	private boolean deleteFileFromDatabase(String username, String projectName, String fileName) {
		String sqlUserId = "SELECT UserID FROM user WHERE UserName = ?";
		String sqlDeleteFile = "DELETE FROM file WHERE FileName = ? AND ProjectName = ? AND UserID = ?";

		try (Connection conn = DatabaseConnection.getConnection()) {
			try (PreparedStatement stmtUserId = conn.prepareStatement(sqlUserId)) {
				stmtUserId.setString(1, username);
				ResultSet rs = stmtUserId.executeQuery();

				if (rs.next()) {
					int userId = rs.getInt("UserID");

					try (PreparedStatement stmtDeleteFile = conn.prepareStatement(sqlDeleteFile)) {
						stmtDeleteFile.setString(1, fileName);
						stmtDeleteFile.setString(2, projectName);
						stmtDeleteFile.setInt(3, userId);
						int rowsAffected = stmtDeleteFile.executeUpdate();

						return rowsAffected > 0;
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean deleteDirectory(File directory) {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					deleteDirectory(file);
				}
				file.delete();
			}
		}
		return directory.delete();
	}
	
	private boolean checkProjectExists(String username, String projectName) {
	    String sqlUserId = "SELECT UserID FROM user WHERE UserName = ?";
	    String sqlCheckProject = "SELECT COUNT(*) FROM file WHERE ProjectName = ? AND UserID = ?";

	    try (Connection conn = DatabaseConnection.getConnection()) {
	        try (PreparedStatement stmtUserId = conn.prepareStatement(sqlUserId)) {
	            stmtUserId.setString(1, username);
	            ResultSet rs = stmtUserId.executeQuery();

	            if (rs.next()) {
	                int userId = rs.getInt("UserID");

	                try (PreparedStatement stmtCheckProject = conn.prepareStatement(sqlCheckProject)) {
	                    stmtCheckProject.setString(1, projectName);
	                    stmtCheckProject.setInt(2, userId);
	                    ResultSet projectResult = stmtCheckProject.executeQuery();

	                    if (projectResult.next()) {
	                        return projectResult.getInt(1) > 0;
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    return false;
	}

	private boolean deleteProjectFromDatabase(String username, String projectName) {
		String sqlUserId = "SELECT UserID FROM user WHERE UserName = ?";
		String sqlDeleteProject = "DELETE FROM file WHERE ProjectName = ? AND UserID = ?";

		try (Connection conn = DatabaseConnection.getConnection()) {
			try (PreparedStatement stmtUserId = conn.prepareStatement(sqlUserId)) {
				stmtUserId.setString(1, username);
				ResultSet rs = stmtUserId.executeQuery();

				if (rs.next()) {
					int userId = rs.getInt("UserID");

					try (PreparedStatement stmtDeleteProject = conn.prepareStatement(sqlDeleteProject)) {
						stmtDeleteProject.setString(1, projectName);
						stmtDeleteProject.setInt(2, userId);
						int rowsAffected = stmtDeleteProject.executeUpdate();

						return rowsAffected > 0;
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void handleCheckFileExistenceRequest(HttpExchange exchange) throws IOException {
	    Map<String, String> queryParams = parseData(exchange.getRequestURI().getQuery());
	    String username = queryParams.get("username");
	    String projectName = queryParams.get("project");
	    String fileName = queryParams.get("filename");

	    String sqlCheckFile = "SELECT FileID FROM file WHERE FileName = ? AND ProjectName = ? AND UserID = ?";
	    try (Connection conn = DatabaseConnection.getConnection()) {
	        try (PreparedStatement stmtUserId = conn.prepareStatement("SELECT UserID FROM user WHERE UserName = ?")) {
	            stmtUserId.setString(1, username);
	            ResultSet rsUser = stmtUserId.executeQuery();

	            if (rsUser.next()) {
	                int userId = rsUser.getInt("UserID");
	                try (PreparedStatement stmtCheckFile = conn.prepareStatement(sqlCheckFile)) {
	                    stmtCheckFile.setString(1, fileName);
	                    stmtCheckFile.setString(2, projectName);
	                    stmtCheckFile.setInt(3, userId);
	                    ResultSet rsFile = stmtCheckFile.executeQuery();

	                    if (rsFile.next()) {
	                        sendResponse(exchange, 200, "exists");
	                    } else {
	                        sendResponse(exchange, 200, "not exists");
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	        sendResponse(exchange, 500, "Database error.");
	    }
	}
}