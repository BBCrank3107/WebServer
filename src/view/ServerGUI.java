package view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import server.Server;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

public class ServerGUI extends Application {

	private final File htmlFolder = new File("html");
	private final Server webServerManager = new Server();
	private ListView<String> logListView;
	
	public void initializeLogging(Server server) {
        ObservableList<String> logItems = logListView.getItems();
        webServerManager.getRequestHandlers().setLogConsumer(logMessage -> {
            Platform.runLater(() -> logItems.add(logMessage));
        });
    }

	@Override
	public void start(Stage primaryStage) {
		primaryStage.setTitle("Web Server Manager");

		// ListView for website files
		ListView<String> accountListView = new ListView<>();
		loadAccount(accountListView);
		logListView = new ListView<>();
		
		initializeLogging(webServerManager);

		// Buttons for removing account
		Button removeButton = new Button("Delete Account");
		Button refreshButton = new Button("Refresh Account");

		// Start/Stop/Restart buttons
		Button startButton = new Button("Start Server");
		Button stopButton = new Button("Stop Server");
		Button restartButton = new Button("Restart Server");

		// CheckBox for localhost and IP address
		CheckBox localhostCheckBox = new CheckBox("Localhost");
		CheckBox ipAddressCheckBox = new CheckBox("IP Address");

		// Ensure only one can be selected at a time
		localhostCheckBox.setSelected(true);

		// Port and IP configuration
		TextField portField = new TextField("8000");
		TextField ipField = new TextField("0.0.0.0");
		ipField.setDisable(true);
		Button applyPortButton = new Button("Apply Port");
		Button applyIpButton = new Button("Apply IP");
		applyIpButton.setDisable(true);

		// SSL configuration
		CheckBox sslCheckBox = new CheckBox("Enable SSL");

		// Label to display server status
		Label serverStatusLabel = new Label("Server is stopped.");

		refreshButton.setOnAction(e -> {
			loadAccount(accountListView);
		});

		// Remove account
		removeButton.setOnAction(e -> {
			String selectedWebsite = accountListView.getSelectionModel().getSelectedItem();
			if (selectedWebsite != null) {
				// Confirmation alert before deleting account
				Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
				confirmationAlert.setTitle("Confirm Deletion");
				confirmationAlert.setHeaderText(null);
				confirmationAlert.setContentText("Are you sure you want to delete this Account?");
				confirmationAlert.showAndWait().ifPresent(response -> {
					if (response == ButtonType.OK) {
						removeAccount(accountListView);
					}
				});
			}
		});

		// Start server
		startButton.setOnAction(e -> {
			int port = webServerManager.getPort();
			if (localhostCheckBox.isSelected()) {
				webServerManager.setHost("localhost");
				try {
					webServerManager.startServer();
					serverStatusLabel.setText("Server is running on localhost:" + port);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				String ip = webServerManager.getHost();
				if (!isValidIp(ip)) {
					showInvalidIpAlert();
				} else {
					try {
						webServerManager.startServer();
						serverStatusLabel.setText("Server is running on " + ip + ":" + port);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		});

		// Stop server
		stopButton.setOnAction(e -> {
			Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
			confirmationAlert.setTitle("Confirm Stop");
			confirmationAlert.setHeaderText(null);
			confirmationAlert.setContentText("Are you sure you want to stop the server?");
			confirmationAlert.showAndWait().ifPresent(response -> {
				if (response == ButtonType.OK) {
					webServerManager.stopServer();
					serverStatusLabel.setText("Server is stopped");
				}
			});
		});

		// Restart server
		restartButton.setOnAction(e -> {
			String ip = webServerManager.getHost();
			int port = webServerManager.getPort();
			if (!webServerManager.isRunning()) {
				showServerNotRunningAlert();
			} else {
				try {
					webServerManager.restartServer();
					serverStatusLabel.setText("Server is restarted. Server is running on " + ip + ":" + port);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});

		// Apply port change
		applyPortButton.setOnAction(e -> {
			Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
			confirmationAlert.setTitle("Confirm Port Change");
			confirmationAlert.setHeaderText(null);
			confirmationAlert.setContentText("Are you sure to use this Port?");

			// Wait for the user's confirmation
			confirmationAlert.showAndWait().ifPresent(response -> {
				if (response == ButtonType.OK) {
					try {
						int newPort = Integer.parseInt(portField.getText());
						if (newPort == 0) {
							showInvalidPortAlert(); // New alert for invalid port
						} else {
							webServerManager.setPort(newPort);
						}
					} catch (NumberFormatException ex) {
						ex.printStackTrace();
					}
				}
			});
		});

		// Apply IP change
		applyIpButton.setOnAction(e -> {
			String ip = ipField.getText().trim();
			if (!isValidIp(ip)) {
				showInvalidIpAlert();
			} else {
				Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
				confirmationAlert.setTitle("Confirm IP Change");
				confirmationAlert.setHeaderText(null);
				confirmationAlert.setContentText("Are you sure to use this IP address?");

				// Wait for the user's confirmation
				confirmationAlert.showAndWait().ifPresent(response -> {
					if (response == ButtonType.OK) {
						webServerManager.setHost(ip);
					}
				});
			}
		});

		// Toggle SSL
		sslCheckBox.setOnAction(e -> webServerManager.enableSSL(sslCheckBox.isSelected()));

		// Handle CheckBox actions
		localhostCheckBox.setOnAction(e -> {
			if (localhostCheckBox.isSelected()) {
				ipAddressCheckBox.setSelected(false);
				ipAddressCheckBox.setDisable(false);
				webServerManager.setHost("localhost");
				ipField.setDisable(true);
			} else {
				ipAddressCheckBox.setDisable(false);
				ipField.setDisable(false);
			}
		});

		ipAddressCheckBox.setOnAction(e -> {
			if (ipAddressCheckBox.isSelected()) {
				String ipAddress = ipField.getText().trim();
				localhostCheckBox.setSelected(false);
				localhostCheckBox.setDisable(false);
				ipField.setDisable(false);
				applyIpButton.setDisable(false);
				webServerManager.setHost(ipAddress);
			} else {
				localhostCheckBox.setDisable(false);
				localhostCheckBox.setSelected(false);
			}
		});

		HBox serverControlBox = new HBox(10);
		serverControlBox.getChildren().addAll(startButton, stopButton, restartButton);
		HBox basicControlBox = new HBox(10);
		basicControlBox.getChildren().addAll(refreshButton, removeButton);
		// Layout configuration
		VBox mainLayout = new VBox(10);
		VBox controlLayout = new VBox(10);
		GridPane configGrid = new GridPane();

		configGrid.setHgap(10);
		configGrid.setVgap(10);
		configGrid.add(new Label("Port:"), 0, 0);
		configGrid.add(portField, 1, 0);
		configGrid.add(applyPortButton, 2, 0);
		configGrid.add(localhostCheckBox, 0, 1);
		configGrid.add(ipAddressCheckBox, 0, 2);
		configGrid.add(ipField, 1, 2);
		configGrid.add(applyIpButton, 2, 2);
		configGrid.add(sslCheckBox, 0, 3, 3, 1);

		controlLayout.getChildren().addAll(new Label("Website Management"), accountListView, basicControlBox,
				new Label("Server Control"), serverControlBox, new Label("Configuration"), configGrid,
				new Label("Server Logs"), logListView, serverStatusLabel);

		mainLayout.getChildren().add(controlLayout);

		Scene scene = new Scene(mainLayout, 700, 700);
		scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
		primaryStage.setScene(scene);
		primaryStage.show();

	}

	private void loadAccount(ListView<String> accountListView) {
		accountListView.getItems().clear();
		File[] websites = htmlFolder.listFiles();
		if (websites != null) {
			for (File website : websites) {
				accountListView.getItems().add(website.getName());
			}
		}
	}

	private void removeAccount(ListView<String> accountListView) {
		String selectedAccount = accountListView.getSelectionModel().getSelectedItem();
		if (selectedAccount != null) {
			// Xóa tài khoản từ file
			File accountsFile = new File("accounts.txt");
			File tempFile = new File("accounts_temp.txt");

			try (BufferedReader reader = new BufferedReader(new FileReader(accountsFile));
					PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

				String line;
				boolean accountFound = false;

				while ((line = reader.readLine()) != null) {
					String username = line.split(":")[0];
					if (username.equals(selectedAccount)) {
						accountFound = true;
					} else {
						writer.println(line);
					}
				}

				if (accountFound) {
					File userFolder = new File(htmlFolder, selectedAccount);
					if (userFolder.exists() && userFolder.isDirectory()) {
						for (File file : userFolder.listFiles()) {
							file.delete();
						}
						userFolder.delete();
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			}

			accountsFile.delete();
			tempFile.renameTo(accountsFile);

			loadAccount(accountListView);
		}
	}

	private boolean isValidIp(String ip) {
		String ipPattern = "^(25[0-5]|2[0-4][0-9]|[1]?[1-9][0-9]?)\\." + "(25[0-5]|2[0-4][0-9]|[1]?[1-9][0-9]?)\\."
				+ "(25[0-5]|2[0-4][0-9]|[1]?[1-9][0-9]?)\\." + "(25[0-5]|2[0-4][0-9]|[1]?[1-9][0-9]?)$";
		return Pattern.matches(ipPattern, ip);
	}

	private void showInvalidIpAlert() {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Invalid IP Address");
		alert.setHeaderText(null);
		alert.setContentText("Invalid IP address! Please enter a valid IP address.");
		alert.showAndWait();
	}

	private void showInvalidPortAlert() {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Invalid Port");
		alert.setHeaderText(null);
		alert.setContentText("Port must be different from 0!");
		alert.showAndWait();
	}

	private void showServerNotRunningAlert() {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Server Not Running");
		alert.setHeaderText(null);
		alert.setContentText("Server is not running! Please start the server before restarting.");
		alert.showAndWait();
	}

	public static void main(String[] args) {
		launch(args);
	}
}