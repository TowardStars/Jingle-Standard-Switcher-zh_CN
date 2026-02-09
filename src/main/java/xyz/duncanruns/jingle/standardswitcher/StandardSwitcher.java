package xyz.duncanruns.jingle.standardswitcher;

import com.google.common.io.Resources;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.JingleAppLaunch;
import xyz.duncanruns.jingle.gui.JingleGUI;
import xyz.duncanruns.jingle.instance.StandardSettings;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.plugin.PluginManager;
import xyz.duncanruns.jingle.util.FileUtil;
import xyz.duncanruns.jingle.util.OpenUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StandardSwitcher {
    private static final Path FOLDER = Jingle.FOLDER.resolve("standardswitcher").toAbsolutePath();
    private static final List<String> ILLEGAL_FILE_CHARACTERS = Arrays.asList("/", "\n", "\r", "\t", "\0", "\f", "`", "?", "*", "\\", "<", ">", "|", "\"", ":");

    public JPanel mainPanel;
    private JLabel whatsWrongLabel;
    private JButton createNewFileButton;
    private JButton switchToAnotherFileButton;
    private JPanel instancePanel;
    private JButton openStandardSwitcherFolderButton;
    private JLabel currentFileLabel;

    private Path usedFilePath;

    public StandardSwitcher() {
        this.instancePanel.setVisible(false);
        this.whatsWrongLabel.setVisible(false);
        openStandardSwitcherFolderButton.addActionListener(a -> OpenUtil.openFile(FOLDER.toString()));
        createNewFileButton.addActionListener(a -> createNewFileButtonPress());
        switchToAnotherFileButton.addActionListener(a -> switchToAnotherFileButtonPress());
    }

    public static void main(String[] args) throws IOException {
        JingleAppLaunch.launchWithDevPlugin(args, PluginManager.JinglePluginData.fromString(
                Resources.toString(Resources.getResource(StandardSwitcher.class, "/jingle.plugin.json"), Charset.defaultCharset())
        ), StandardSwitcher::initialize);
    }

    public static void initialize() {
        FOLDER.toFile().mkdir();

        StandardSwitcher standardSwitcher = new StandardSwitcher();
        JingleGUI.addPluginTab("Standard Switcher", standardSwitcher.mainPanel, standardSwitcher::reload);
        JingleGUI.get().addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                standardSwitcher.reload();
            }
        });
        PluginEvents.MAIN_INSTANCE_CHANGED.register(standardSwitcher::reload);
    }

    private static void setGlobalFile(Path instancePath, Path newPath) throws IOException {
        FileUtil.writeString(instancePath.resolve("config").resolve("mcsr").resolve("standardsettings.global"), newPath.toString());
    }

    private void switchToAnotherFileButtonPress() {
        Path instancePath = Jingle.getLatestInstancePath().orElse(null);
        if (instancePath == null) {
            reload();
            return;
        }

        Function<String, String> nameCleaner = s -> s.endsWith(".json") ? s.substring(0, s.length() - 5).trim() : s.trim();

        List<String> existingFileNames = Arrays.stream(Optional.ofNullable(FOLDER.toFile().list()).orElse(new String[]{})).filter(s -> s.endsWith(".json")).map(nameCleaner).collect(Collectors.toList());
        if (existingFileNames.isEmpty()) {
            JOptionPane.showMessageDialog(this.mainPanel, "Standard Switcher中没有存放任何standard settings文件。", "Jingle Standard Switcher: 没有文件", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String current = nameCleaner.apply(usedFilePath.getFileName().toString());
        current = existingFileNames.contains(current) ? current : existingFileNames.get(0);
        Object ans = JOptionPane.showInputDialog(this.mainPanel, "选择一个文件：", "Jingle Standard Switcher: 选择文件", JOptionPane.QUESTION_MESSAGE, null, existingFileNames.toArray(), current);
        if (ans == null) return;
        Path newPath = FOLDER.resolve(ans + ".json");

        try {
            setGlobalFile(instancePath, newPath);
        } catch (Exception e) {
            Jingle.logError("Failed to set standardsettings.global!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "创建standardsettings.global文件失败了！ （请检查日志）", "Jingle Standard Switcher: 复制失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        reload();
    }

    private void createNewFileButtonPress() {
        Path instancePath = Jingle.getLatestInstancePath().orElse(null);
        if (instancePath == null) {
            reload();
            return;
        }

        Function<String, String> nameCleaner = s -> s == null ? null : (s.endsWith(".json") ? s.substring(0, s.length() - 5).trim() : s.trim());

        List<String> existingFileNames = Arrays.stream(Optional.ofNullable(FOLDER.toFile().list()).orElse(new String[]{})).filter(s -> s.endsWith(".json")).map(nameCleaner).collect(Collectors.toList());

        Function<String, String> asker = warning -> JOptionPane.showInputDialog(this.mainPanel, warning + "您当前实例的Standard Settings配置将会被复制，请为该配置输入新名称：", "Jingle Standard Switcher: 创建新文件", JOptionPane.QUESTION_MESSAGE);
        Function<String, Integer> wrongChecker = s -> {
            if (s.isEmpty()) return 1; // Empty
            if (existingFileNames.stream().anyMatch(s::equalsIgnoreCase)) return 2; // Already exists
            if (ILLEGAL_FILE_CHARACTERS.stream().anyMatch(s::contains)) return 3; // Invalid name
            return 0;
        };

        String ans = nameCleaner.apply(asker.apply(""));
        int issue;
        while (ans != null && (issue = wrongChecker.apply(ans)) > 0) {
            switch (issue) {
                case 1:
                    ans = asker.apply("请输入一个名称！\n");
                    break;
                case 2:
                    ans = asker.apply("当前文件名已存在！\n");
                    break;
                case 3:
                    ans = asker.apply("文件名无效！\n");
                    break;
            }
        }
        if (ans == null) return;

        Path oldPath = usedFilePath;
        Path newPath = FOLDER.resolve(ans + ".json");
        String s;
        try {
            s = FileUtil.readString(oldPath);
        } catch (Exception e) {
            Jingle.logError("Failed to read standard settings file!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "读取Standard Settings文件失败了！（请检查日志）", "Jingle Standard Switcher: 复制失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            FileUtil.writeString(newPath, s);
        } catch (Exception e) {
            Jingle.logError("Failed to write standard settings file!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "写入Standard Settings文件失败了！（请检查日志）", "Jingle Standard Switcher: 复制失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            setGlobalFile(instancePath, newPath);
        } catch (Exception e) {
            Jingle.logError("Failed to set standardsettings.global!", e);
            JOptionPane.showMessageDialog(this.mainPanel, "创建standardsettings.global文件失败了！（请检查日志）", "Jingle Standard Switcher: 复制失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        this.reload();
    }

    public void reload() {
        Path instancePath = Jingle.getLatestInstancePath().orElse(null);
        whatsWrongLabel.setVisible(false);
        instancePanel.setVisible(false);
        if (instancePath == null) {
            warn("请打开一个游戏实例！");
            return;
        }

        try {
            usedFilePath = new StandardSettings(instancePath).getUsedFilePath();
        } catch (Exception e) {
            Jingle.logError("Failed to get used standard settings path for the instance!", e);
            warn("获取您standard settings文件的路径失败了！（请检查日志）");
            return;
        }

        boolean exists = Files.exists(usedFilePath);
        boolean isManaged = usedFilePath.getParent().toAbsolutePath().equals(FOLDER);
        boolean isGlobal = !instancePath.resolve("config").resolve("mcsr").resolve("standardsettings.json").toAbsolutePath().equals(usedFilePath.toAbsolutePath());

        this.instancePanel.setVisible(true);

        if (!exists) {
            this.currentFileLabel.setText("当前Standard Settings文件：不存在");
        } else if (!isGlobal) {
            this.currentFileLabel.setText("当前Standard Settings文件：非全局");
        } else if (isManaged) {
            this.currentFileLabel.setText("当前Standard Settings文件：" + usedFilePath.getFileName().toString());
        } else {
            String string = usedFilePath.toString();
            this.currentFileLabel.setText("当前Standard Settings文件：" + (string.length() > 50 ? ("..." + string.substring(string.length() - 47)) : string));
        }
    }

    private void warn(String text) {
        whatsWrongLabel.setVisible(true);
        whatsWrongLabel.setText(text);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane1 = new JScrollPane();
        mainPanel.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(5, 1, new Insets(5, 5, 5, 5), -1, -1));
        scrollPane1.setViewportView(panel1);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        whatsWrongLabel = new JLabel();
        whatsWrongLabel.setText("请打开一个游戏实例！");
        panel1.add(whatsWrongLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        instancePanel = new JPanel();
        instancePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(instancePanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        instancePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        currentFileLabel = new JLabel();
        currentFileLabel.setText("当前Standard Settings 文件：“（未知）");
        instancePanel.add(currentFileLabel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        createNewFileButton = new JButton();
        createNewFileButton.setText("创建新文件");
        instancePanel.add(createNewFileButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setForeground(new Color(-65536));
        label1.setText("在选择之前请确保游戏内的standard setting菜单是关闭的！");
        instancePanel.add(label1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        switchToAnotherFileButton = new JButton();
        switchToAnotherFileButton.setText("选择另一个文件");
        instancePanel.add(switchToAnotherFileButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 20, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        openStandardSwitcherFolderButton = new JButton();
        openStandardSwitcherFolderButton.setText("打开 Standard Switcher 文件夹");
        panel1.add(openStandardSwitcherFolderButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
