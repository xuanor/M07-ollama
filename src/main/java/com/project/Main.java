package com.project;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    final int WINDOW_WIDTH = 700;
    final int WINDOW_HEIGHT = 550;

    @Override
    public void start(Stage stage) throws Exception {

        // Carrega la vista inicial des del fitxer FXML
        Parent root = FXMLLoader.load(getClass().getResource("/assets/ollamaLayout.fxml"));
        Scene scene = new Scene(root);
        // Vincular el archivo CSS a la escena
        scene.getStylesheets().add(getClass().getResource("/assets/styles.css").toExternalForm());


        stage.setScene(scene);
        stage.setTitle("SenpAI");
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.setMinWidth(400);
        stage.setMinHeight(400);
        stage.show();

        // Cargar el icono desde assets/logo.png para todos los sistemas operativos
        Image icon = new Image(getClass().getResource("/assets/logo.png").toExternalForm());
        stage.getIcons().add(icon);

        // Afegeix una icona només si no és un Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            icon = new Image("file:icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
