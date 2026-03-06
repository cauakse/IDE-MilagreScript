package com.example.idemilagrescript.editor;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

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

        CodeArea codeArea = new CodeArea();

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea)); // Isso aqui que faz a numeção das linas

        codeArea.replaceText(0, 0, content);

        Tab tab = new Tab(path.getFileName().toString(), codeArea);
        tab.setUserData(path);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    public void saveCurrent() throws IOException {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) return;

        Path path = (Path) tab.getUserData();
        CodeArea area = (CodeArea) tab.getContent();

        Files.writeString(path, area.getText());
    }

    public void saveAll() throws IOException {
        for (Tab tab : tabPane.getTabs()) {
            Path path = (Path) tab.getUserData();
            CodeArea area = (CodeArea) tab.getContent();
            Files.writeString(path, area.getText());
        }
    }
}