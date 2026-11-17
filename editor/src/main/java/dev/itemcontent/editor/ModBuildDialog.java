package dev.itemcontent.editor;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.Set;

final class ModBuildDialog extends JDialog {
    ModBuildDialog(Component owner, ItemProject project, Runnable settingsChanged) {
        super(SwingUtilities.getWindowAncestor(owner), "Собрать самостоятельный мод", ModalityType.APPLICATION_MODAL);
        var selection = new ModSelectionPanel(project); setLayout(new BorderLayout()); add(selection, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        JButton cancel = new JButton("Отмена"), build = new JButton("Собрать JAR…"); EditorTheme.primary(build);
        bottom.add(cancel); bottom.add(build); add(bottom, BorderLayout.SOUTH); cancel.addActionListener(e -> dispose());
        build.addActionListener(e -> {
            final StandaloneMod.Settings settings; final Set<String> items; final Path target;
            try {
                settings = selection.settings(); items = selection.selection(); StandaloneMod.selected(project, items).validateForExport();
                var chooser = new JFileChooser(); chooser.setDialogTitle("Куда сохранить готовый мод"); chooser.setSelectedFile(new java.io.File(settings.id() + "-" + settings.version() + ".jar"));
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Готовый мод Minecraft (.jar)", "jar"));
                if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                target = chooser.getSelectedFile().toPath();
                project.modId = settings.id(); project.modName = settings.name(); project.modVersion = settings.version();
                project.modItems = new java.util.ArrayList<>(items); project.requiredMods = new java.util.ArrayList<>(settings.requiredMods());
                settingsChanged.run();
            } catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Проверьте настройки мода", JOptionPane.ERROR_MESSAGE); return; }
            cancel.setEnabled(false); build.setEnabled(false); build.setText("Сборка…"); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            new SwingWorker<StandaloneMod.Result, Void>() {
                protected StandaloneMod.Result doInBackground() throws Exception { return StandaloneMod.build(project, items, settings, target); }
                protected void done() {
                    try {
                        var result = get();
                        JOptionPane.showMessageDialog(ModBuildDialog.this, "Готово: " + result.jar() + "\nПредметов: " + result.items()
                                + "\n\nУстановка: положите этот JAR в mods вашей сборки Minecraft 1.20.1 / Fabric.\nНужен Fabric API. Тот же JAR установите на сервер и клиенты.\nМодели и текстуры включены и загружаются автоматически.\nРедактор, Item Content, отдельные конфиги и ресурс-паки не нужны.\n\nСписок ID предметов находится внутри JAR в ITEMS.txt.", "Мод собран", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } catch (Exception failure) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        JOptionPane.showMessageDialog(ModBuildDialog.this, cause.getMessage(), "Не удалось собрать мод", JOptionPane.ERROR_MESSAGE);
                    } finally { cancel.setEnabled(true); build.setEnabled(true); build.setText("Собрать JAR…"); setDefaultCloseOperation(DISPOSE_ON_CLOSE); }
                }
            }.execute();
        });
        pack(); Rectangle available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(760, available.width - 30), Math.min(600, available.height - 30)));
        setSize(Math.min(900, available.width - 30), Math.min(720, available.height - 30)); setLocationRelativeTo(owner);
    }
}
