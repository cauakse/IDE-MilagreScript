package com.example.idemilagrescript.terminal;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;


import java.io.*;

public class PtyTerminalService {

    private final TextArea terminalArea;

    private PtyProcess process;
    private BufferedWriter writer;

    public PtyTerminalService(TextArea terminalArea) {
        this.terminalArea = terminalArea;
        startShell();
        startReaderThread();
    }

    private void startShell() {
        try {

            String[] command = getShellCommand();

            PtyProcessBuilder builder =
                    new PtyProcessBuilder(command);

            process = builder.start();

            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream())
            );

        } catch (Exception e) {
            append("Failed to start PTY shell\n");
        }
    }

    private String[] getShellCommand() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return new String[]{"cmd.exe"};
        }

        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return new String[]{shell};
        }

        return new String[]{"bash"};
    }

    private void startReaderThread() {

        Thread thread = new Thread(() -> {
            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    append(line + "\n");
                }

            } catch (IOException ignored) {}
        });

        thread.setDaemon(true);
        thread.start();
    }

    public void sendCommand(String command) {
        try {
            writer.write(command);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            append("Error sending command\n");
        }
    }

    public void interrupt() {
        try {
            writer.write("\u0003"); // Ctrl+C
            writer.flush();
        } catch (IOException ignored) {}
    }

    private void append(String text) {
        Platform.runLater(() -> {
            terminalArea.appendText(text);
            terminalArea.positionCaret(terminalArea.getLength());
        });
    }
}