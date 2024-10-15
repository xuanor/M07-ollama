package com.project;

import javafx.scene.control.ScrollPane;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ProgressIndicator; // Importar ProgressIndicator

import org.json.JSONObject;

public class Controller implements Initializable {

    @FXML
    private Button sendButton, uploadImageButton, stopButton;

    @FXML
    private VBox chatBox; // Caja donde se agregarán los mensajes dinámicamente

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private TextField textField;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private CompletableFuture<HttpResponse<InputStream>> streamRequest;
    private CompletableFuture<HttpResponse<String>> completeRequest;
    private AtomicBoolean isCancelled = new AtomicBoolean(false);
    private InputStream currentInputStream;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> streamReadingTask;
    private boolean isFirst = false;
    private Label lastLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setButtonsIdle();
        
    }

    @FXML
    private void sendMessage(ActionEvent event) {
        setButtonsRunning();
        isCancelled.set(false);

        String userInput = textField.getText();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"model\": \"llama3.2:1b\", \"prompt\": \"" + userInput + "\", \"stream\": true}"))
                .build();

        // Añadir el mensaje del usuario al chat
        Platform.runLater(() -> {
            Text userMessage = new Text("YOU: \n" + userInput);
            chatBox.getChildren().add(userMessage);
            progressIndicator.setVisible(true); // Mostrar el ProgressIndicator
        });

        isFirst = true;
        streamRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    currentInputStream = response.body();
                    streamReadingTask = executorService.submit(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentInputStream))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (isCancelled.get()) {
                                    System.out.println("Stream cancelled");
                                    break;
                                }
                                JSONObject jsonResponse = new JSONObject(line);
                                String responseText = jsonResponse.getString("response");

                                Platform.runLater(() -> {
                                    if (isFirst) {
                                        // Crear un nuevo Label con ajuste de texto habilitado
                                        lastLabel = new Label("SenpAI: \n" + responseText);
                                        lastLabel.setWrapText(true);

                                        // Ajustar el ancho máximo para que el Label respete el ancho del VBox
                                        lastLabel.setMaxWidth(scrollPane.getWidth() - 20);

                                        chatBox.getChildren().add(lastLabel);
                                        isFirst = false;
                                    } else {
                                        //System.out.println(responseText);
                                        // Actualizar el texto existente en el Label
                                        lastLabel.setText(lastLabel.getText() + responseText);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Platform.runLater(() -> {
                                Text errorMessage = new Text("\nError during streaming.");
                                chatBox.getChildren().add(errorMessage);
                                setButtonsIdle();
                            });
                        } finally {
                            try {
                                if (currentInputStream != null) {
                                    System.out.println("Cancelling InputStream in finally");
                                    currentInputStream.close();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            Platform.runLater(() -> {
                                progressIndicator.setVisible(false); // Ocultar el ProgressIndicator
                                setButtonsIdle();
                            });
                        }
                    });
                    return response;
                })
                .exceptionally(e -> {
                    if (!isCancelled.get()) {
                        e.printStackTrace();
                    }
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false); // Ocultar el ProgressIndicator
                        setButtonsIdle();
                    });
                    return null;
                });

        textField.setText(""); // Limpiar el campo de texto después de enviar
    }

    @FXML
    private void uploadImage(ActionEvent event) {
        setButtonsRunning();
        isCancelled.set(false);
        String[] base64Image = new String[1];

        File initialDirectory = new File("./");
        FileChooser fileChooser = new FileChooser();
        if (initialDirectory.exists()) {
            fileChooser.setInitialDirectory(initialDirectory);
        }
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        Stage stage = (Stage) uploadImageButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                BufferedImage bufferedImage = ImageIO.read(selectedFile);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", outputStream);
                byte[] imageBytes = outputStream.toByteArray();

                // Set the image in the ImageView
                Image image = new Image(selectedFile.toURI().toString());
                ImageView img = new ImageView();
                img.setImage(image);

                img.fitWidthProperty().bind(scrollPane.widthProperty());
                img.fitHeightProperty().bind(scrollPane.heightProperty());
                img.setPreserveRatio(true);

                chatBox.getChildren().add(img);
                base64Image[0] = Base64.getEncoder().encodeToString(imageBytes);
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(
                        "{\"model\": \"llava-phi3\", \"prompt\": \"Dime que hay en la foto en español?\", \"stream\": false, \"images\": [\"" + String.join(",", base64Image) + "\"]}"))
                .build();

        Platform.runLater(() -> {
            Text userMessage = new Text("YOU: \nQue hay en la imagen?");
            chatBox.getChildren().add(userMessage);
            progressIndicator.setVisible(true); // Mostrar el ProgressIndicator
        });

        completeRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    String responseText = jsonResponse.getString("response");

                    Platform.runLater(() -> {
                        //System.out.println(responseText);
                        lastLabel = new Label("SenpAI: " + responseText);
                        lastLabel.setWrapText(true);
                        lastLabel.setMaxWidth(scrollPane.getWidth() - 20);
                       // lastLabel.setMaxHeight(Double.MAX_VALUE);

                        chatBox.getChildren().add(lastLabel);
                        progressIndicator.setVisible(false); // Ocultar el ProgressIndicator
                        setButtonsIdle();
                    });
                    return response;
                })
                .exceptionally(e -> {
                    if (!isCancelled.get()) {
                        e.printStackTrace();
                    }
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false); // Ocultar el ProgressIndicator
                        setButtonsIdle();
                    });
                    return null;
                });

        textField.setText("");
    }

    @FXML
    private void stopExecution(ActionEvent event) {
        isCancelled.set(true);
        cancelStreamRequest();
        cancelCompleteRequest();
        Platform.runLater(() -> {
            Text cancelMessage = new Text("\nRequest cancelled.");
            chatBox.getChildren().add(cancelMessage);
            progressIndicator.setVisible(false); // Asegurarse de que el ProgressIndicator esté oculto
            setButtonsIdle();
        });
    }

    private void cancelStreamRequest() {
        if (streamRequest != null && !streamRequest.isDone()) {
            try {
                if (currentInputStream != null) {
                    currentInputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (streamReadingTask != null) {
                streamReadingTask.cancel(true);
            }
            streamRequest.cancel(true);
        }
    }

    private void cancelCompleteRequest() {
        if (completeRequest != null && !completeRequest.isDone()) {
            completeRequest.cancel(true);
        }
    }

    private void setButtonsRunning() {
        sendButton.setDisable(true);
        sendButton.setVisible(false);
        uploadImageButton.setDisable(true);

        stopButton.setDisable(false);
        stopButton.setVisible(true);

    }

    private void setButtonsIdle() {
        sendButton.setDisable(false);
        sendButton.setVisible(true);
        uploadImageButton.setDisable(false);
        stopButton.setDisable(true);
        stopButton.setVisible(false);
        streamRequest = null;
        completeRequest = null;
    }
}
