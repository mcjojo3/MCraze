package mc.sayda.mcraze.tools;

import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCraze World Editor - JSON-only viewer with search functionality.
 */
public class WorldEditor extends JFrame {

    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JPanel editorPanel;
    private JLabel statusLabel;
    private JTextArea jsonEditor;
    private JTextArea logArea;
    private File currentFile;
    private File currentWorldFolder;
    private File currentWorldDatFile;
    private WorldDataParser.WorldData currentWorldData;
    private Object currentEditingObject;
    private javax.swing.Timer statusResetTimer;
    private Color defaultStatusColor;

    // Search components
    private JTextField searchField;
    private JCheckBox matchCaseCheckbox;
    private Highlighter.HighlightPainter searchHighlighter;
    private int currentSearchIndex = -1;
    private java.util.List<int[]> searchMatches = new ArrayList<>();

    private String initialDirectory = null;

    public WorldEditor() {
        this(null);
    }

    public WorldEditor(String directory) {
        this.initialDirectory = directory;

        setTitle("MCraze World Editor");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setupMenuBar();
        setLayout(new BorderLayout());
        setupToolBar();

        // Left Side: File Tree
        rootNode = new DefaultMutableTreeNode("No World Loaded");
        treeModel = new DefaultTreeModel(rootNode);
        fileTree = new JTree(treeModel);
        JScrollPane treeScroll = new JScrollPane(fileTree);
        treeScroll.setPreferredSize(new Dimension(280, 0));

        // Right Side: JSON Editor Panel
        editorPanel = new JPanel(new CardLayout());

        JLabel emptyLabel = new JLabel("Select a node to view JSON", SwingConstants.CENTER);
        editorPanel.add(emptyLabel, "EMPTY");

        jsonEditor = new JTextArea();
        jsonEditor.setFont(new Font("Monospaced", Font.PLAIN, 12));
        jsonEditor.setEditable(true);
        JScrollPane editorScroll = new JScrollPane(jsonEditor);
        editorPanel.add(editorScroll, "JSON");

        // Split Pane (Tree + Editor)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, editorPanel);
        mainSplit.setDividerLocation(280);
        // Stabilize layout - prevent components from collapsing
        treeScroll.setMinimumSize(new Dimension(150, 100));
        editorPanel.setMinimumSize(new Dimension(200, 100));

        // Log Panel
        logArea = new JTextArea(4, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        logScroll.setMinimumSize(new Dimension(100, 60));

        // Vertical Split (Main + Log)
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplit, logScroll);
        verticalSplit.setResizeWeight(0.85);
        verticalSplit.setDividerLocation(450);
        mainSplit.setMinimumSize(new Dimension(300, 200));
        add(verticalSplit, BorderLayout.CENTER);

        // Status Bar
        statusLabel = new JLabel(" Ready");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);

        // Search highlighter
        searchHighlighter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 100));

        log("INFO", "World Editor initialized");

        // Tree selection listener
        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (node == null)
                return;

            Object userObject = node.getUserObject();
            if (userObject instanceof FileNode) {
                FileNode fileNode = (FileNode) userObject;
                // Allow editing .dat files (which are JSON in MCraze) or .json files
                if (fileNode.filename.endsWith(".dat") || fileNode.filename.endsWith(".json")) {
                    // But skip binary world.dat which has its own custom view logic
                    if (!fileNode.filename.equals("world.dat")) {
                        loadJsonFile(fileNode.file);
                    }
                } else {
                    ((CardLayout) editorPanel.getLayout()).show(editorPanel, "EMPTY");
                    currentFile = null;
                }
            } else if (userObject instanceof String) {
                String label = (String) userObject;
                handleNodeSelection(node, label);
            }
        });

        // Auto-open directory after visible (use SwingUtilities.invokeLater to defer)
        SwingUtilities.invokeLater(() -> {
            if (initialDirectory != null && !initialDirectory.isEmpty()) {
                File dir = new File(initialDirectory);
                if (dir.exists() && dir.isDirectory()) {
                    File worldDat = new File(dir, "world.dat");
                    if (worldDat.exists()) {
                        log("INFO", "Auto-opening: " + dir.getAbsolutePath());
                        loadWorld(dir);
                        return;
                    }
                }
                // Invalid directory - log warning and open selector
                log("WARN", "Invalid directory: " + initialDirectory);
            }
            // No directory or invalid - prompt world selector
            openWorldFolder();
        });
    }

    private void handleNodeSelection(DefaultMutableTreeNode node, String label) {
        currentEditingObject = node;
        clearSearchHighlights();

        if (currentWorldData == null) {
            ((CardLayout) editorPanel.getLayout()).show(editorPanel, "EMPTY");
            return;
        }

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        String json = "";

        // Category folders - show all items
        if (label.startsWith("📦")) {
            json = gson.toJson(currentWorldData.chests);
        } else if (label.startsWith("🔥")) {
            json = gson.toJson(currentWorldData.furnaces);
        } else if (label.startsWith("⚗")) {
            json = gson.toJson(currentWorldData.alchemies);
        } else if (label.startsWith("🌻")) {
            json = gson.toJson(currentWorldData.pots);
        } else if (label.startsWith("👾")) {
            json = gson.toJson(currentWorldData.entities);
        } else if (label.startsWith("📊")) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("width", currentWorldData.width);
            info.put("height", currentWorldData.height);
            info.put("ticksAlive", currentWorldData.ticksAlive);
            json = gson.toJson(info);
        } else if (label.equals("world.dat")) {
            json = gson.toJson(currentWorldData);
        }
        // Individual items - show single item JSON
        else if (label.startsWith("Chest @")) {
            for (WorldDataParser.ChestEntry chest : currentWorldData.chests) {
                if (label.equals("Chest @ (" + chest.x + ", " + chest.y + ")")) {
                    json = gson.toJson(chest);
                    break;
                }
            }
        } else if (label.startsWith("Furnace @")) {
            for (WorldDataParser.FurnaceEntry furnace : currentWorldData.furnaces) {
                if (label.startsWith("Furnace @ (" + furnace.x + ", " + furnace.y + ")")) {
                    json = gson.toJson(furnace);
                    break;
                }
            }
        } else if (label.startsWith("Alchemy @")) {
            for (WorldDataParser.AlchemyEntry alchemy : currentWorldData.alchemies) {
                if (label.startsWith("Alchemy @ (" + alchemy.x + ", " + alchemy.y + ")")) {
                    json = gson.toJson(alchemy);
                    break;
                }
            }
        } else if (label.startsWith("Pot @")) {
            for (WorldDataParser.PotEntry pot : currentWorldData.pots) {
                if (label.startsWith("Pot @ (" + pot.x + ", " + pot.y + ")")) {
                    json = gson.toJson(pot);
                    break;
                }
            }
        } else if (label.contains("@ (")) {
            // Individual entity (e.g., "EntityZombie @ (100, 200)")
            for (WorldDataParser.EntityEntry entity : currentWorldData.entities) {
                String entityLabel = entity.type + " @ (" + (int) entity.x + ", " + (int) entity.y + ")";
                if (label.equals(entityLabel)) {
                    json = gson.toJson(entity);
                    break;
                }
            }
        }

        if (!json.isEmpty()) {
            jsonEditor.setText(json);
            jsonEditor.setCaretPosition(0);
            ((CardLayout) editorPanel.getLayout()).show(editorPanel, "JSON");
            statusLabel.setText(" Viewing: " + label);
            log("INFO", "Selected: " + label);
        } else {
            ((CardLayout) editorPanel.getLayout()).show(editorPanel, "EMPTY");
        }
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open World Folder...");
        openItem.addActionListener(e -> openWorldFolder());

        JMenuItem saveItem = new JMenuItem("Save Changes");
        saveItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        saveItem.addActionListener(e -> saveCurrentFile());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void showAboutDialog() {
        String message = "<html><body style='width: 350px; padding: 10px;'>" +
                "<p>MCraze World Editor</p>" +
                "<p>A standalone tool for viewing and editing MCraze save files.</p>" +
                "<br>" +
                "<p><b>Features:</b><br>" +
                "• Browse world folders containing world.dat<br>" +
                "• Edit level.dat (world settings, seed, gamerules)<br>" +
                "• Edit entities.dat (player data, inventory, mobs)<br>" +
                "• View and edit world data structure<br>" +
                "• Search JSON content</p>" +
                "<br>" +
                "<p><b>Credits:</b><br>" +
                "MCraze Game &amp; World Editor<br>" +
                "Created by SaydaGames (mc_jojo3)<br>" +
                "<br>" +
                "MCraze version: " + mc.sayda.mcraze.Constants.GAME_VERSION + "</p>" +
                "</body></html>";

        JOptionPane.showMessageDialog(this, message, "About MCraze World Editor",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void setupToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton openBtn = new JButton("📁 Open");
        openBtn.setToolTipText("Open World Folder");
        openBtn.addActionListener(e -> openWorldFolder());

        JButton saveBtn = new JButton("💾 Save");
        saveBtn.setToolTipText("Save Current File (Ctrl+S)");
        saveBtn.addActionListener(e -> saveCurrentFile());

        JButton reloadBtn = new JButton("🔄 Reload");
        reloadBtn.setToolTipText("Reload Current Save");
        reloadBtn.addActionListener(e -> reloadCurrentWorld());

        toolBar.add(openBtn);
        toolBar.add(reloadBtn);
        toolBar.addSeparator();
        toolBar.add(saveBtn);

        // Add flexible space to push search to the right
        toolBar.add(Box.createHorizontalGlue());

        // Search components
        JLabel searchLabel = new JLabel("Search: ");
        searchField = new JTextField(15);
        searchField.setMaximumSize(new Dimension(200, 28));
        searchField.addActionListener(e -> performSearch());

        matchCaseCheckbox = new JCheckBox("Match Case");
        matchCaseCheckbox.setFocusable(false);

        JButton searchBtn = new JButton("🔍");
        searchBtn.setToolTipText("Find (Enter)");
        searchBtn.addActionListener(e -> performSearch());

        JButton nextBtn = new JButton("▼");
        nextBtn.setToolTipText("Next Match");
        nextBtn.addActionListener(e -> jumpToNextMatch());

        JButton prevBtn = new JButton("▲");
        prevBtn.setToolTipText("Previous Match");
        prevBtn.addActionListener(e -> jumpToPrevMatch());

        toolBar.add(searchLabel);
        toolBar.add(searchField);
        toolBar.add(matchCaseCheckbox);
        toolBar.add(searchBtn);
        toolBar.add(prevBtn);
        toolBar.add(nextBtn);

        add(toolBar, BorderLayout.NORTH);
    }

    private void performSearch() {
        clearSearchHighlights();
        searchMatches.clear();
        currentSearchIndex = -1;

        String query = searchField.getText();
        if (query.isEmpty()) {
            statusLabel.setText(" Ready");
            return;
        }

        String text = jsonEditor.getText();
        boolean matchCase = matchCaseCheckbox.isSelected();

        Pattern pattern;
        if (matchCase) {
            pattern = Pattern.compile(Pattern.quote(query));
        } else {
            pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        }

        Matcher matcher = pattern.matcher(text);
        Highlighter highlighter = jsonEditor.getHighlighter();

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            searchMatches.add(new int[] { start, end });
            try {
                highlighter.addHighlight(start, end, searchHighlighter);
            } catch (Exception e) {
                log("WARN", "Highlight error: " + e.getMessage());
            }
        }

        if (searchMatches.isEmpty()) {
            showStatus("No matches found", Color.ORANGE);
        } else {
            currentSearchIndex = 0;
            jumpToMatch(0);
            showStatus(searchMatches.size() + " matches found (1/" + searchMatches.size() + ")", new Color(0, 128, 0));
        }
    }

    private void jumpToNextMatch() {
        if (searchMatches.isEmpty())
            return;
        currentSearchIndex = (currentSearchIndex + 1) % searchMatches.size();
        jumpToMatch(currentSearchIndex);
    }

    private void jumpToPrevMatch() {
        if (searchMatches.isEmpty())
            return;
        currentSearchIndex = (currentSearchIndex - 1 + searchMatches.size()) % searchMatches.size();
        jumpToMatch(currentSearchIndex);
    }

    private void jumpToMatch(int index) {
        if (index < 0 || index >= searchMatches.size())
            return;
        int[] match = searchMatches.get(index);
        jsonEditor.setCaretPosition(match[0]);
        jsonEditor.moveCaretPosition(match[1]);
        jsonEditor.requestFocusInWindow();
        showStatus((index + 1) + "/" + searchMatches.size() + " matches", new Color(0, 128, 0));
    }

    private void clearSearchHighlights() {
        jsonEditor.getHighlighter().removeAllHighlights();
    }

    private void openWorldFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Select World Folder or world.dat");

        String defaultSaveDir = mc.sayda.mcraze.world.storage.WorldSaveManager.getSavesDirectory();
        if (defaultSaveDir != null) {
            chooser.setCurrentDirectory(new File(defaultSaveDir));
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selection = chooser.getSelectedFile();
            File folder = selection.isFile() ? selection.getParentFile() : selection;
            loadWorld(folder);
        }
    }

    private void loadWorld(File folder) {
        File worldFile = new File(folder, "world.dat");
        statusLabel.setText(" Loading: " + folder.getName());

        if (!worldFile.exists()) {
            JOptionPane.showMessageDialog(this, "Valid world.dat not found in this folder!", "Error",
                    JOptionPane.ERROR_MESSAGE);
            statusLabel.setText(" Error loading world");
            return;
        }

        rootNode.setUserObject(folder.getName());
        rootNode.removeAllChildren();

        addFileNode(folder, "level.dat");
        addFileNode(folder, "world.dat");
        addFileNode(folder, "entities.dat");

        // Add playerdata folder
        File playerDataDir = new File(folder, "playerdata");
        if (playerDataDir.exists() && playerDataDir.isDirectory()) {
            DefaultMutableTreeNode playerDirNode = new DefaultMutableTreeNode("👤 playerdata");
            File[] playerFiles = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
            if (playerFiles != null) {
                for (File pf : playerFiles) {
                    playerDirNode.add(new DefaultMutableTreeNode(new FileNode(pf.getName(), pf)));
                }
            }
            rootNode.add(playerDirNode);
            log("INFO", "Added playerdata folder with " + (playerFiles != null ? playerFiles.length : 0) + " files");
        }

        treeModel.reload();
        statusLabel.setText(" Loaded: " + folder.getAbsolutePath());
    }

    private void reloadCurrentWorld() {
        if (currentWorldFolder != null && currentWorldFolder.exists()) {
            log("INFO", "Reloading world: " + currentWorldFolder.getName());
            loadWorld(currentWorldFolder);
            showStatus("✓ Reloaded", new Color(0, 128, 0));
        } else {
            showStatus("No world to reload", Color.ORANGE);
        }
    }

    private void addFileNode(File folder, String filename) {
        File f = new File(folder, filename);
        if (f.exists()) {
            log("INFO", "Found: " + filename + " (" + f.length() + " bytes)");
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileNode(filename, f));

            if (filename.equals("world.dat")) {
                currentWorldFolder = folder;
                currentWorldDatFile = f;
                try {
                    currentWorldData = WorldDataParser.parse(f);
                    WorldDataParser.populateTreeNodeSimple(fileNode, currentWorldData);
                    log("INFO", "Parsed world.dat: " + currentWorldData.width + "x" + currentWorldData.height +
                            " tiles, " + currentWorldData.chests.size() + " chests, " +
                            currentWorldData.furnaces.size() + " furnaces, " + currentWorldData.pots.size() + " pots");
                    showStatus("✓ world.dat loaded successfully", new Color(0, 128, 0));
                } catch (java.io.EOFException e) {
                    log("WARN", "Incomplete world.dat - some data may be missing (old format?)");
                    fileNode.add(new DefaultMutableTreeNode("⚠ Partial data (old format)"));
                    currentWorldData = null;
                } catch (Exception e) {
                    log("ERROR", "Failed to parse world.dat: " + e.getMessage());
                    fileNode.add(new DefaultMutableTreeNode("❌ Error: " + e.getMessage()));
                    e.printStackTrace();
                    currentWorldData = null;
                }
            }
            rootNode.add(fileNode);
        } else {
            log("WARN", "Missing file: " + filename);
        }
    }

    private void loadJsonFile(File file) {
        currentFile = file;
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            jsonEditor.setText(content);
            jsonEditor.setCaretPosition(0);
            ((CardLayout) editorPanel.getLayout()).show(editorPanel, "JSON");
            statusLabel.setText(" Editing: " + file.getName());
        } catch (Exception e) {
            statusLabel.setText(" Error reading file");
            e.printStackTrace();
        }
    }

    private void saveCurrentFile() {
        if (currentFile != null) {
            try {
                java.nio.file.Files.write(currentFile.toPath(), jsonEditor.getText().getBytes());
                log("INFO", "Saved JSON: " + currentFile.getName());
                showStatus("✓ Saved: " + currentFile.getName(), new Color(0, 128, 0));
            } catch (Exception e) {
                log("ERROR", "Failed to save JSON: " + e.getMessage());
                showStatus("✗ Failed to save: " + e.getMessage(), Color.RED);
            }
        } else if (currentWorldData != null && currentWorldDatFile != null) {
            try {
                // Parse JSON back to model and save
                String json = jsonEditor.getText();
                com.google.gson.Gson gson = new com.google.gson.Gson();

                // Determine what we're saving based on currentEditingObject
                if (currentEditingObject != null) {
                    String label = currentEditingObject.toString();

                    // Category folder saves (bulk)
                    if (label.startsWith("📦")) {
                        currentWorldData.chests = gson.fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.List<WorldDataParser.ChestEntry>>() {
                                }.getType());
                    } else if (label.startsWith("🔥")) {
                        currentWorldData.furnaces = gson.fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.List<WorldDataParser.FurnaceEntry>>() {
                                }.getType());
                    } else if (label.startsWith("⚗")) {
                        currentWorldData.alchemies = gson.fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.List<WorldDataParser.AlchemyEntry>>() {
                                }.getType());
                    } else if (label.startsWith("🌻")) {
                        currentWorldData.pots = gson.fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.List<WorldDataParser.PotEntry>>() {
                                }.getType());
                    } else if (label.startsWith("👾")) {
                        currentWorldData.entities = gson.fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.List<WorldDataParser.EntityEntry>>() {
                                }.getType());
                    } else if (label.startsWith("📊")) {
                        Map<String, Object> info = gson.fromJson(json, Map.class);
                        if (info != null && info.containsKey("ticksAlive")) {
                            currentWorldData.ticksAlive = ((Number) info.get("ticksAlive")).longValue();
                        }
                    } else if (label.equals("world.dat")) {
                        currentWorldData = gson.fromJson(json, WorldDataParser.WorldData.class);
                    }
                    // Individual item saves
                    else if (label.startsWith("Chest @")) {
                        WorldDataParser.ChestEntry updated = gson.fromJson(json, WorldDataParser.ChestEntry.class);
                        for (int i = 0; i < currentWorldData.chests.size(); i++) {
                            WorldDataParser.ChestEntry c = currentWorldData.chests.get(i);
                            if (c.x == updated.x && c.y == updated.y) {
                                currentWorldData.chests.set(i, updated);
                                break;
                            }
                        }
                    } else if (label.startsWith("Furnace @")) {
                        WorldDataParser.FurnaceEntry updated = gson.fromJson(json, WorldDataParser.FurnaceEntry.class);
                        for (int i = 0; i < currentWorldData.furnaces.size(); i++) {
                            WorldDataParser.FurnaceEntry f = currentWorldData.furnaces.get(i);
                            if (f.x == updated.x && f.y == updated.y) {
                                currentWorldData.furnaces.set(i, updated);
                                break;
                            }
                        }
                    } else if (label.startsWith("Alchemy @")) {
                        WorldDataParser.AlchemyEntry updated = gson.fromJson(json, WorldDataParser.AlchemyEntry.class);
                        for (int i = 0; i < currentWorldData.alchemies.size(); i++) {
                            WorldDataParser.AlchemyEntry a = currentWorldData.alchemies.get(i);
                            if (a.x == updated.x && a.y == updated.y) {
                                currentWorldData.alchemies.set(i, updated);
                                break;
                            }
                        }
                    } else if (label.startsWith("Pot @")) {
                        WorldDataParser.PotEntry updated = gson.fromJson(json, WorldDataParser.PotEntry.class);
                        for (int i = 0; i < currentWorldData.pots.size(); i++) {
                            WorldDataParser.PotEntry p = currentWorldData.pots.get(i);
                            if (p.x == updated.x && p.y == updated.y) {
                                currentWorldData.pots.set(i, updated);
                                break;
                            }
                        }
                    } else if (label.contains("@ (")) {
                        // Individual entity (e.g., "EntityZombie @ (100, 200)")
                        WorldDataParser.EntityEntry updated = gson.fromJson(json, WorldDataParser.EntityEntry.class);
                        for (int i = 0; i < currentWorldData.entities.size(); i++) {
                            WorldDataParser.EntityEntry e = currentWorldData.entities.get(i);
                            String oldLabel = e.type + " @ (" + (int) e.x + ", " + (int) e.y + ")";
                            if (label.equals(oldLabel)) {
                                currentWorldData.entities.set(i, updated);
                                break;
                            }
                        }
                    }
                }

                WorldDataParser.save(currentWorldDatFile, currentWorldData);
                log("INFO", "Saved Binary: world.dat");
                showStatus("✓ world.dat saved successfully", new Color(0, 128, 0));
            } catch (Exception e) {
                log("ERROR", "Failed to save: " + e.getMessage());
                showStatus("✗ Save Failed", Color.RED);
                e.printStackTrace();
            }
        } else {
            showStatus("No file to save", Color.ORANGE);
        }
    }

    private void showStatus(String message, Color color) {
        if (statusResetTimer != null && statusResetTimer.isRunning()) {
            statusResetTimer.stop();
        }
        if (defaultStatusColor == null) {
            defaultStatusColor = statusLabel.getForeground();
        }
        statusLabel.setText(" " + message);
        statusLabel.setForeground(color);

        statusResetTimer = new javax.swing.Timer(3000, e -> {
            statusLabel.setText(" Ready");
            statusLabel.setForeground(defaultStatusColor);
        });
        statusResetTimer.setRepeats(false);
        statusResetTimer.start();
    }

    private void log(String level, String message) {
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String prefix = "[" + timestamp + "] [" + level + "] ";
        logArea.append(prefix + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static class FileNode {
        String filename;
        File file;

        FileNode(String name, File f) {
            this.filename = name;
            this.file = f;
        }

        @Override
        public String toString() {
            return filename;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Use Nimbus L&F
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            new WorldEditor().setVisible(true);
        });
    }
}
