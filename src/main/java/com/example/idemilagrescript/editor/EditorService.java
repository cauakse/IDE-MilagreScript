package com.example.idemilagrescript.editor;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EditorService {

    private final TabPane tabPane;

    public EditorService(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    public void openFile(Path path) throws IOException {
        String content = Files.readString(path);

        TextArea area = new TextArea(content);
        Tab tab = new Tab(path.getFileName().toString(), area);
        tab.setUserData(path);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    public void saveCurrent() throws IOException {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) return;

        Path path = (Path) tab.getUserData();
        TextArea area = (TextArea) tab.getContent();

        Files.writeString(path, area.getText());
    }

    public void saveAll() throws IOException {
        for (Tab tab : tabPane.getTabs()) {
            Path path = (Path) tab.getUserData();
            TextArea area = (TextArea) tab.getContent();
            Files.writeString(path, area.getText());
        }
    }
}