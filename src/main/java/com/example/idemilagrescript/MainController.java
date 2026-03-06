package com.example.idemilagrescript;

import com.example.idemilagrescript.editor.EditorService;
import com.example.idemilagrescript.project.FileManager;
import com.example.idemilagrescript.terminal.PtyTerminalService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.event.ActionEvent;

import java.nio.file.Files;
import java.nio.file.Path;

import static jdk.jfr.consumer.EventStream.openFile;

public class MainController {

    private PtyTerminalService terminalService;
    private FileManager fileManager;
    private EditorService editorService;

    @FXML private MenuItem newFileItem;
    @FXML private MenuItem newFolderItem;
    @FXML private MenuItem saveItem;
    @FXML private MenuItem saveAllItem;
    @FXML private MenuItem openDirectoryItem;

    @FXML private TreeView<Path> projectTreeView;
    @FXML private TabPane editorTabPane;
    @FXML private TableView<?> problemsTable;
    @FXML private TextArea terminalArea;
    @FXML private Label statusLabel;
    @FXML private Label autosaveLabel;

    @FXML
    public void initialize() {
        statusLabel.setText("Ready");
        autosaveLabel.setText("Autosave: ON");

        terminalService = new PtyTerminalService(terminalArea);
        editorService = new EditorService(editorTabPane);
        fileManager = new FileManager(projectTreeView);

        ContextMenu contextMenu = new ContextMenu();

        MenuItem newFile = new MenuItem("New File");
        MenuItem newFolder = new MenuItem("New Folder");
        MenuItem deleteItem = new MenuItem("Delete");

        deleteItem.setOnAction(this::handleDelete);
        newFile.setOnAction(this::handleNewFile);
        newFolder.setOnAction(this::handleNewFolder);

        contextMenu.getItems().addAll(newFile, newFolder, deleteItem);

        projectTreeView.setContextMenu(contextMenu);

        Path startupPath = Path.of(System.getProperty("user.dir"));
        fileManager.openDirectory(startupPath);

        projectTreeView.setOnMouseClicked(event -> {

            if (event.getClickCount() != 2) return; // só duplo clique

            TreeItem<Path> selected =
                    projectTreeView.getSelectionModel().getSelectedItem();

            if (selected == null) return;

            Path path = selected.getValue();

            if (Files.isRegularFile(path)) {
                try {
                    editorService.openFile(path);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        projectTreeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else {
                    Path fileName = item.getFileName();
                    setText(fileName != null ? fileName.toString() : item.toString());
                }
            }
        });

        terminalArea.setOnKeyPressed(event -> {

            if (event.isControlDown() && event.getCode().toString().equals("C")) {
                terminalService.interrupt();
                event.consume();
            }

            if (event.getCode().toString().equals("ENTER")) {
                String[] lines = terminalArea.getText().split("\n");
                String command = lines[lines.length - 1];
                terminalService.sendCommand(command);
            }
        });
        bindActions();
    }

    private void bindActions() {

        if (newFileItem != null)
            newFileItem.setOnAction(this::handleNewFile);

        if (newFolderItem != null)
            newFolderItem.setOnAction(this::handleNewFolder);

        if (saveItem != null)
            saveItem.setOnAction(this::handleSave);

        if (saveAllItem != null)
            saveAllItem.setOnAction(this::handleSaveAll);

        if (openDirectoryItem != null)
            openDirectoryItem.setOnAction(e -> fileManager.chooseAndOpenDirectory());
    }

    // ======================================================
    // ================== HANDLERS ==========================
    // ======================================================

    private void handleNewFile(ActionEvent event) {

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Novo Arquivo");
        dialog.setHeaderText("Digite o nome do arquivo:");

        dialog.showAndWait().ifPresent(name -> {
            try {
                Path baseDir = fileManager.getSelectedDirectory();
                fileManager.createFile(baseDir, name);
                statusLabel.setText("Arquivo criado");
            } catch (Exception e) {
                showError("Erro ao criar arquivo: " + e.getMessage());
            }
        });
    }

    private void handleDelete(ActionEvent event) {

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Tem certeza que deseja deletar?");
        confirm.showAndWait().ifPresent(response -> {

            if (response == ButtonType.OK) {
                try {
                    fileManager.deleteSelected();
                    statusLabel.setText("Deletado com sucesso");
                } catch (Exception e) {
                    showError("Erro ao deletar: " + e.getMessage());
                }
            }
        });
    }

    private void handleNewFolder(ActionEvent event) {

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nova Pasta");
        dialog.setHeaderText("Digite o nome da pasta:");

        dialog.showAndWait().ifPresent(name -> {
            try {
                Path baseDir = fileManager.getSelectedDirectory();
                fileManager.createFolder(baseDir, name);
                statusLabel.setText("Pasta criada");
            } catch (Exception e) {
                showError("Erro ao criar pasta: " + e.getMessage());
            }
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private void handleSave(ActionEvent event) {
        try {
            editorService.saveCurrent();
            statusLabel.setText("File saved");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSaveAll(ActionEvent event) {
        try {
            editorService.saveAll();
            statusLabel.setText("All files saved");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void handleRunLexer(ActionEvent actionEvent) {
    }
}