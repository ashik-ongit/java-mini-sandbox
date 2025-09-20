import java.awt.BorderLayout;
import java.awt.Font;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.tools.*;

public class MiniSandboxGUI {
    private static final int TIMEOUT_SECONDS = 3;

    private static final List<String> BLOCKED_SUBSTRINGS = Arrays.asList(
        "System.exit", "Runtime.getRuntime", "ProcessBuilder", "java.lang.reflect",
        "Class.forName", "getDeclaredMethod", "getMethod", "System.loadLibrary",
        "System.load", "Files.delete", "Files.deleteIfExists", "FileInputStream",
        "FileOutputStream", "RandomAccessFile", "Socket", "ServerSocket",
        "java.net", "URLConnection", "Desktop.getDesktop", "ProcessBuilder",
        "Runtime.getRuntime().exec", "exec(", "native ", "pinvoke", "sun.misc.Unsafe",
        "setSecurityManager", "SecurityManager", "System.setSecurityManager",
        "java.nio", "Paths.get", "Files.write", "Files.readAllBytes"
    );

    // Regex patterns for imports, absolute paths, suspicious constructs
    private static final List<Pattern> BLOCKED_REGEX = Arrays.asList(
        Pattern.compile("import\\s+java\\.net\\.[\\w\\.*]+"),
        Pattern.compile("import\\s+java\\.nio\\.[\\w\\.*]+"),
        Pattern.compile("import\\s+java\\.lang\\.reflect\\.[\\w\\.*]+"),
        Pattern.compile("\"[A-Za-z]:\\\\\\\\[^\"]+\""),                // Windows absolute path literal "C:\..."
        Pattern.compile("\"/[^\\s\"]+\""),                          // Unix absolute path literal "/etc/..."
        Pattern.compile("\\.\\./"),                                 // parent directory usage
        Pattern.compile("System\\.loadLibrary\\s*\\("),
        Pattern.compile("System\\.load\\s*\\("),
        Pattern.compile("\\bnative\\b"),                            // native keyword
        Pattern.compile("Runtime\\.getRuntime\\s*\\("),
        Pattern.compile("ProcessBuilder\\s*\\(")
    );

    private JFrame frame;
    private JTextArea codeArea;
    private JTextArea outputArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MiniSandboxGUI().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Java Mini Sandbox (with basic protection)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 650);

        codeArea = new JTextArea(20, 80);
        codeArea.setText("""
            public class UserProgram {
                public static void main(String[] args) {
                    System.out.println("Hello from protected sandbox!");
                }
            }
            """);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        outputArea = new JTextArea(12, 80);
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JButton runButton = new JButton("Run Code");
        runButton.addActionListener(e -> runUserCode());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JScrollPane(codeArea), BorderLayout.CENTER);
        topPanel.add(runButton, BorderLayout.NORTH);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JLabel("Output / Diagnostics:"), BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private void runUserCode() {
        outputArea.setText("");
        String userCode = codeArea.getText();

        // Normalize: remove repeated whitespace (but keep single spaces where needed)
        String normalized = userCode.replaceAll("\\s+", " ");

        // Quick blocked substring check (case-sensitive for Java keywords)
        for (String s : BLOCKED_SUBSTRINGS) {
            if (normalized.contains(s)) {
                outputArea.setText("❌ Security Alert: Dangerous code detected! (" + s + ")\n");
                return;
            }
        }

        // Regex checks (for imports, absolute paths, parent dir usage, native keyword, etc.)
        for (Pattern p : BLOCKED_REGEX) {
            Matcher m = p.matcher(userCode);
            if (m.find()) {
                outputArea.setText("❌ Security Alert: Dangerous pattern blocked: " + p.pattern() + "\n");
                return;
            }
        }

        // Also block obvious attempts to construct absolute file paths in code strings or use parent dir
        if (userCode.matches("(?s).*\\bnew\\s+File\\s*\\(\\s*\"[A-Za-z]:\\\\.*\".*")) {
            outputArea.setText("❌ Security Alert: Absolute file path usage blocked.\n");
            return;
        }
        if (userCode.contains("..")) {
            outputArea.setText("❌ Security Alert: Parent directory access ('..') is blocked.\n");
            return;
        }

        // Create a temporary working directory for this run
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("sandbox_run_");
        } catch (IOException io) {
            outputArea.setText("Error creating temp directory: " + io.getMessage());
            return;
        }

        try {
            // Write source file to temp dir
            Path sourceFile = tempDir.resolve("UserProgram.java");
            Files.writeString(sourceFile, userCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Compile using JavaCompiler (capturing diagnostics)
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                outputArea.setText("❌ Java compiler not found. Run with a JDK (not JRE).\n");
                return;
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            Iterable<? extends JavaFileObject> compUnits = fileManager.getJavaFileObjectsFromFiles(
                    Collections.singletonList(sourceFile.toFile()));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, Arrays.asList("-d", tempDir.toString()), null, compUnits);
            boolean success = task.call();
            fileManager.close();

            if (!success) {
                StringBuilder sb = new StringBuilder();
                sb.append("❌ Compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    sb.append("Line ").append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append("\n");
                }
                outputArea.setText(sb.toString());
                return;
            }

            outputArea.append("✅ Compilation successful!\n");

            // Run the compiled class in a separate process, with tempDir as working directory and -cp tempDir
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", tempDir.toString(), "UserProgram");
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            // Read output asynchronously and enforce timeout
            ExecutorService readerExec = Executors.newSingleThreadExecutor();
            Future<?> readerFuture = readerExec.submit(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String lineCopy = line;
                        SwingUtilities.invokeLater(() -> outputArea.append(lineCopy + "\n"));
                    }
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> outputArea.append("Error reading process output: " + ex.getMessage() + "\n"));
                }
            });

            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                outputArea.append("\n⏱ Execution timed out (" + TIMEOUT_SECONDS + "s) and was killed.\n");
            } else {
                // Ensure reader finishes reading streams
                try {
                    readerFuture.get(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            readerExec.shutdownNow();

        } catch (IOException | InterruptedException ex) {
            outputArea.append("Runtime error: " + ex.getMessage() + "\n");
        } finally {
            // Attempt to delete the temp directory and its contents
            try {
                deleteDirectoryRecursively(tempDir);
            } catch (Exception ignored) { }
        }
    }

    // Utility: recursively delete a directory
    private static void deleteDirectoryRecursively(Path path) {
        if (path == null) return;
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }
}
