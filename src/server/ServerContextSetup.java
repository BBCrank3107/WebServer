package server;

import com.sun.net.httpserver.HttpServer;

public class ServerContextSetup {
	private final RequestHandlers requestHandlers;

	public ServerContextSetup(RequestHandlers requestHandlers) {
		this.requestHandlers = requestHandlers;
	}

	public void setupContexts(HttpServer server) {
		server.createContext("/", requestHandlers::handleRootRequest);
		server.createContext("/register", requestHandlers::handleRegisterRequest);
		server.createContext("/login", requestHandlers::handleLoginRequest);
		server.createContext("/logout", requestHandlers::handleLogoutRequest);
		server.createContext("/getUserName", requestHandlers::handleGetUserNameRequest);
		server.createContext("/listProjects", requestHandlers::handleListProjectsRequest);
		server.createContext("/listFilesInProject", requestHandlers::handleListFilesInProjectRequest);
		server.createContext("/upload", requestHandlers::handleUploadRequest);
		server.createContext("/deleteFile", requestHandlers::handleDeleteFileRequest);
		server.createContext("/createProject", requestHandlers::handleCreateProjectRequest);
		server.createContext("/deleteProject", requestHandlers::handleDeleteProjectRequest);
		server.createContext("/checkFileExistence", requestHandlers::handleCheckFileExistenceRequest);
	}
}