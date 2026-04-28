package com.example.idemilagrescript.project;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class FileManager {

    private Path currentRoot;
    private final TreeView<Path> treeView;

    public FileManager(TreeView<Path> treeView) {
        this.treeView = treeView;
    }

    public void openDirectory(Path path) {
        if (path == null || !Files.exists(path)) return;

        currentRoot = path;
        treeView.setRoot(createNode(currentRoot));
    }

    public void openDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(treeView.getScene().getWindow());

        if (dir != null) {
            currentRoot = dir.toPath();
            treeView.setRoot(createNode(currentRoot));
        }
    }

    private TreeItem<Path> createNode(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);

        if (Files.isDirectory(path)) {
            try (Stream<Path> paths = Files.list(path)) {
                paths.forEach(p ->
                        item.getChildren().add(createNode(p))
                );
            } catch (IOException ignored) {}
        }

        return item;
    }

    public void createFile(Path baseDir, String name) throws IOException {

        Path newFile = baseDir.resolve(name);

        if (Files.exists(newFile))
            throw new FileAlreadyExistsException(name);

        Files.createFile(newFile);

        TreeItem<Path> parentItem =
                findTreeItem(treeView.getRoot(), baseDir);

        if (parentItem != null) {
            parentItem.getChildren().add(new TreeItem<>(newFile));
            parentItem.setExpanded(true);
        }
    }

    public Path getSelectedDirectory() {
        TreeItem<Path> selected =
                treeView.getSelectionModel().getSelectedItem();

        if (selected == null) return currentRoot;

        Path path = selected.getValue();

        if (Files.isDirectory(path)) return path;

        return path.getParent();
    }

    public void createFolder(Path baseDir, String name) throws IOException {

        Path newFolder = baseDir.resolve(name);

        if (Files.exists(newFolder))
            throw new FileAlreadyExistsException(name);

        Files.createDirectory(newFolder);

        TreeItem<Path> parentItem =
                findTreeItem(treeView.getRoot(), baseDir);

        if (parentItem != null) {
            parentItem.getChildren().add(new TreeItem<>(newFolder));
            parentItem.setExpanded(true);
        }
    }

    public void deleteSelected() throws IOException {

        TreeItem<Path> selected =
                treeView.getSelectionModel().getSelectedItem();

        if (selected == null) return;

        Path path = selected.getValue();

        if (Files.isDirectory(path)) {
            deleteDirectoryRecursively(path);
        } else {
            Files.delete(path);
        }

        selected.getParent().getChildren().remove(selected);
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {

        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public void chooseAndOpenDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(treeView.getScene().getWindow());

        if (dir != null) {
            openDirectory(dir.toPath());
        }
    }

    private TreeItem<Path> findTreeItem(TreeItem<Path> root, Path target) {
        if (root.getValue().equals(target)) return root;

        for (TreeItem<Path> child : root.getChildren()) {
            TreeItem<Path> found = findTreeItem(child, target);
            if (found != null) return found;
        }

        return null;
    }

    public Path getCurrentRoot() {
        return currentRoot;
    }
}