package com.example.idemilagrescript;

import com.example.idemilagrescript.compiler.LexError;
import com.example.idemilagrescript.compiler.LexerAnalyser;
import com.example.idemilagrescript.compiler.SemanticAnalyser;
import com.example.idemilagrescript.compiler.IntermediateCodeGenerator;
import com.example.idemilagrescript.compiler.IntermediateCodeOptimizer;
import com.example.idemilagrescript.utils.SymbolTable;
import com.example.idemilagrescript.compiler.ParserAnalyser;
import com.example.idemilagrescript.editor.EditorService;
import com.example.idemilagrescript.project.FileManager;
import com.example.idemilagrescript.terminal.PtyTerminalService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.event.ActionEvent;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;
import com.example.idemilagrescript.utils.Symbol;
import org.fxmisc.richtext.CodeArea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fxmisc.richtext.model.StyleSpansBuilder;
import java.util.Collection;
import com.example.idemilagrescript.utils.Token;
import com.example.idemilagrescript.utils.TokenType;

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

    @FXML private Button themeToggleButton;
    @FXML private Button inspectorToggleButton;
    @FXML private VBox inspectorPanel;
    @FXML private TabPane inspectorTabPane;
    @FXML private TableView<Symbol> symbolTableView;
    @FXML private TableColumn<Symbol, String> symNameCol;
    @FXML private TableColumn<Symbol, String> symTypeCol;
    @FXML private TableColumn<Symbol, String> symScopeCol;
    @FXML private TableColumn<Symbol, String> symLineCol;
    @FXML private TableColumn<Symbol, String> symInitCol;
    @FXML private TableColumn<Symbol, String> symUsedCol;
    @FXML private TableView<Token> tokenTableView;
    @FXML private TableColumn<Token, String> tokLexemeCol;
    @FXML private TableColumn<Token, String> tokTypeCol;
    @FXML private TableColumn<Token, String> tokLineCol;
    @FXML private TableColumn<Token, String> tokColCol;
    @FXML private Button toggleOptimizedBtn;
    @FXML private Label codeViewLabel;
    @FXML private TextArea intermediateCodeArea;

    private boolean isDarkMode = true;
    private SymbolTable lastSymbolTable;
    private List<Token> lastTokens = new ArrayList<>();
    private List<String> lastIntermediateCode = new ArrayList<>();
    private List<String> lastOptimizedCode = new ArrayList<>();
    private boolean showingOptimized = false;


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

        symNameCol.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getName()));
        symTypeCol.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getType().name()));
        symScopeCol.setCellValueFactory(s -> new SimpleStringProperty(String.valueOf(s.getValue().getScopeDepth())));
        symLineCol.setCellValueFactory(s -> new SimpleStringProperty(String.valueOf(s.getValue().getLine())));
        symInitCol.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().isInitialized() ? "✓" : "✗"));
        symUsedCol.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().isUsed() ? "✓" : "✗"));

        tokLexemeCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getLexeme()));
        tokTypeCol.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getType().name()));
        tokLineCol.setCellValueFactory(t -> new SimpleStringProperty(String.valueOf(t.getValue().getLine())));
        tokColCol.setCellValueFactory(t -> new SimpleStringProperty(String.valueOf(t.getValue().getColumn())));

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

        List<LexError> lexicalErrors = lexer.analyze(text);
        lastTokens = new ArrayList<>(lexer.getTokens());

        ParserAnalyser parser = new ParserAnalyser(lexer.getTokens());
        List<LexError> syntacticErrors = parser.parse();

        List<LexError> semanticErrors = new ArrayList<>();
        SemanticAnalyser semantic = null;

        if (syntacticErrors.isEmpty()) {
            semantic = new SemanticAnalyser(lexer.getTokens());
            semanticErrors = semantic.analyze();
        }

        List<LexError> allErrors = new ArrayList<>();
        allErrors.addAll(lexicalErrors);
        allErrors.addAll(syntacticErrors);
        allErrors.addAll(semanticErrors);

        terminalService.printLine("─────────────────────────────────────────────");
        terminalService.printLine("Tokens: "      + lexer.getTokens().size()
                + " | Léxico: "   + lexicalErrors.size()
                + " | Sintático: "+ syntacticErrors.size()
                + " | Semântico: "+ semanticErrors.size());

        for (LexError e : syntacticErrors) {
            terminalService.printLine("[SINTATICO] Linha " + e.getLine()
                    + " Col " + String.format("%02d", e.getColumn())
                    + ": " + e.getMessage());
        }

        for (LexError e : semanticErrors) {
            boolean isWarning = e.getMessage().startsWith("[AVISO]");
            String tag = isWarning ? "[SEMANTICO-AVISO]" : "[SEMANTICO-ERRO]";
            terminalService.printLine(tag + " Linha " + e.getLine()
                    + " Col " + String.format("%02d", e.getColumn())
                    + ": " + e.getMessage());
        }

        if (semantic != null) {
            lastSymbolTable = semantic.getSymbolTable();
            terminalService.printLine(lastSymbolTable.toString());
        } else {
            lastSymbolTable = null;
        }

        //tive que tratar assim, porque nao gerava com erros e aviso, sendo que o aviso nao deve barrar de gerar o codigo intermediario
        //porque o aviso está dentro da lista semanticErrors, então seu MainController trata aviso como se fosse erro
        boolean hasSemanticError = semanticErrors.stream()
        .anyMatch(e -> !e.getMessage().startsWith("[AVISO]"));

        if (lexicalErrors.isEmpty() && syntacticErrors.isEmpty() && !hasSemanticError) {
            IntermediateCodeGenerator generator =
                    new IntermediateCodeGenerator(lexer.getTokens());

            List<String> intermediateCode = generator.generate();
            lastIntermediateCode = new ArrayList<>(intermediateCode);

            IntermediateCodeOptimizer optimizer = new IntermediateCodeOptimizer();
            lastOptimizedCode = optimizer.optimize(intermediateCode);

            terminalService.printLine("Código Intermediário:");
            for (String line : intermediateCode) {
                terminalService.printLine(line);
            }

            terminalService.printLine("Código Otimizado:");
            for (String line : lastOptimizedCode) {
                terminalService.printLine(line);
            }
        } else {
            lastIntermediateCode = new ArrayList<>();
            lastOptimizedCode = new ArrayList<>();
            terminalService.printLine("Código intermediário não gerado devido a erros anteriores.");
        }

        if (inspectorPanel.isVisible()) {
            refreshSymbolTable();
            refreshTokenTable();
            refreshIntermediateCode();
        }

        terminalService.printLine("─────────────────────────────────────────────");

        ObservableList<LexError> list = problemsByEditor.computeIfAbsent(
                editor, e -> FXCollections.observableArrayList()
        );

        list.setAll(allErrors);
        aplicarDestaque(editor, lexer.getTokens());
        highlightErrors(editor, allErrors);

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

    @FXML
    private void toggleInspector() {
        boolean show = !inspectorPanel.isVisible();
        inspectorPanel.setVisible(show);
        inspectorPanel.setManaged(show);
        if (show) {
            refreshSymbolTable();
            refreshTokenTable();
            refreshIntermediateCode();
        }
    }

    @FXML
    private void toggleOptimizedCode() {
        showingOptimized = !showingOptimized;
        if (showingOptimized) {
            toggleOptimizedBtn.setText("Mostrar Original");
            codeViewLabel.setText("Otimizado");
        } else {
            toggleOptimizedBtn.setText("Mostrar Otimizado");
            codeViewLabel.setText("Original");
        }
        refreshIntermediateCode();
    }

    private void refreshIntermediateCode() {
        List<String> code = showingOptimized ? lastOptimizedCode : lastIntermediateCode;
        intermediateCodeArea.setText(String.join("\n", code));
    }

    private void refreshSymbolTable() {
        if (lastSymbolTable == null) {
            symbolTableView.getItems().clear();
            return;
        }
        symbolTableView.getItems().setAll(lastSymbolTable.getAllSymbols());
    }

    private void refreshTokenTable() {
        tokenTableView.getItems().setAll(lastTokens);
    }

    @FXML
    private void toggleTheme() {
        Scene scene = editorTabPane.getScene();

        ObservableList<String> styleClasses = scene.getRoot().getStyleClass();

        if (isDarkMode) {
            styleClasses.add("theme-light");
            themeToggleButton.setText("🌙 Dark Mode");
        } else {
            styleClasses.remove("theme-light");
            themeToggleButton.setText("☀ Light Mode");
        }

        isDarkMode = !isDarkMode;
    }


    private String getStyleClassForToken(TokenType type) {
        switch (type) {
            case VOID:
                case CHAR:
                    case INT:
                        case DOUBLE:
                            case SHORT:
                                case LONG:
                                    case STRING:
                return "type";
            case IF: case ELSE: case WHILE: case RETURN:
                return "keyword";

            case STRING_LITERAL:
                return "string";
            case NUMERO:
                return "number";

            case MAIS:
                case MENOS:
                    case VEZES:
                        case DIVISAO:
                            case ATRIBUICAO:
            case IGUAL:
                case DIFERENTE:
                    case MAIOR:
                        case MENOR:
                            case MAIOR_IGUAL:
                                case MENOR_IGUAL:
            case NOT:
                case AND:
                    case OR:
                return "operator";

            case ABRE_PAREN:
                case FECHA_PAREN:
                    case ABRE_CHAVE:
                        case FECHA_CHAVE:
                            case PONTO_VIRGULA:
                return "punctuation";

            case IDENTIFICADOR:
                return "identifier";
            default:
                return null;
        }
    }

    private void aplicarDestaque(CodeArea editor, List<Token> tokens) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastPos = 0;

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (token.getType() != TokenType.EOF) {
                String styleClass = getStyleClassForToken(token.getType());
                int gap = token.getOffset() - lastPos;

                if (gap > 0)
                    spansBuilder.add(Collections.emptyList(), gap);

                if (styleClass != null && !styleClass.isEmpty())
                    spansBuilder.add(Collections.singleton(styleClass), token.getLength());
                else
                    spansBuilder.add(Collections.emptyList(), token.getLength());

                lastPos = token.getOffset() + token.getLength();
            }
        }

        int textLength = editor.getLength();
        if (lastPos < textLength)
            spansBuilder.add(Collections.emptyList(), textLength - lastPos);

        editor.setStyleSpans(0, spansBuilder.create());
    }
}