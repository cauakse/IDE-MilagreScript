package com.example.idemilagrescript;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/idemilagrescript/MainView.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);

        stage.setTitle("MilagreScript IDE");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
