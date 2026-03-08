package com.example.idemilagrescript;

import com.example.idemilagrescript.compiler.LexError;
import com.example.idemilagrescript.compiler.LexerAnalyser;
import com.example.idemilagrescript.editor.EditorService;
import com.example.idemilagrescript.project.FileManager;
import com.example.idemilagrescript.terminal.PtyTerminalService;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.event.ActionEvent;
import javafx.scene.control.cell.PropertyValueFactory;
import org.fxmisc.richtext.CodeArea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainController {

    private PtyTerminalService terminalService;
    private FileManager fileManager;
    private EditorService editorService;
    private LexerAnalyser lexer = new LexerAnalyser();

    private final Map<CodeArea, ObservableList<LexError>> problemsByEditor = new HashMap<>();

    @FXML private TableView<LexError> problemsTable;
    @FXML private TableColumn<LexError, Integer> lineColumn;
    @FXML private TableColumn<LexError, Integer> columnColumn;
    @FXML private TableColumn<LexError, String> messageColumn;

    @FXML private MenuItem newFileItem;
    @FXML private MenuItem newFolderItem;
    @FXML private MenuItem saveItem;
    @FXML private MenuItem saveAllItem;
    @FXML private MenuItem openDirectoryItem;

    @FXML private TreeView<Path> projectTreeView;
    @FXML private TabPane editorTabPane;
    @FXML private TextArea terminalArea;
    @FXML private Label statusLabel;
    @FXML private Label autosaveLabel;

    @FXML
    public void initialize() {

        statusLabel.setText("Ready");

        terminalService = new PtyTerminalService(terminalArea);
        editorService = new EditorService(editorTabPane);
        fileManager = new FileManager(projectTreeView);
        problemsTable.setItems(FXCollections.observableArrayList());

        lineColumn.setCellValueFactory(new PropertyValueFactory<>("line"));
        columnColumn.setCellValueFactory(new PropertyValueFactory<>("column"));
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));

        editorTabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {

                    if (newTab == null) return;

                    CodeArea editor = (CodeArea) newTab.getContent();

                    ObservableList<LexError> list =
                            problemsByEditor.getOrDefault(editor,
                                    FXCollections.observableArrayList());

                    problemsTable.setItems(list);
                });

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

            if (event.getClickCount() != 2) return;

            TreeItem<Path> selected =
                    projectTreeView.getSelectionModel().getSelectedItem();

            if (selected == null) return;

            Path path = selected.getValue();

            if (Files.isRegularFile(path)) {
                try {

                    CodeArea editor = editorService.openFile(path);
                    editor.getStylesheets().add(
                            getClass().getResource("/editor.css").toExternalForm()
                    );

                    attachLexer(editor);

                    runLexer(editor);

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

    private void highlightErrors(CodeArea editor, List<LexError> errors) {

        editor.clearStyle(0, editor.getLength());

        for (LexError e : errors) {

            int start = e.getOffset();
            int end = start + e.getLength();

            if (start >= 0 && end <= editor.getLength()) {

                editor.setStyle(
                        start,
                        end,
                        Collections.singleton("error")
                );
            }
        }
    }

    public void attachLexer(CodeArea editor) {

        editor.multiPlainChanges()
                .successionEnds(java.time.Duration.ofMillis(300))
                .subscribe(ignore -> runLexer(editor));
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

    private void runLexer(CodeArea editor) {

        String text = editor.getText();

        List<LexError> errors = lexer.analyze(text);
        System.out.println("Tokens: " + lexer.getTokens().size() + ", Errors: " + errors.size());
        for (int i = 0; i < lexer.getTokens().size(); i++) {
            System.out.println(lexer.getTokens().get(i).getType() + " -> " + lexer.getTokens().get(i).getLexeme());
        }

        ObservableList<LexError> list = problemsByEditor.computeIfAbsent(
                editor,
                e -> FXCollections.observableArrayList()
        );

        list.setAll(errors);

        highlightErrors(editor, errors);
        if (isEditorSelected(editor)) {
            problemsTable.setItems(list);
        }

    }

    private boolean isEditorSelected(CodeArea editor) {

        Tab tab = editorTabPane.getSelectionModel().getSelectedItem();

        if (tab == null) return false;

        return tab.getContent() == editor;
    }

    private void updateProblems(List<LexError> errors) {

        Platform.runLater(() -> {
            problemsTable.getItems().setAll(errors);
        });
    }
}