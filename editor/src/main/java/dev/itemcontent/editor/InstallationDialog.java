package dev.itemcontent.editor;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;

/** Review concrete game locations before installing the generated content. */
final class InstallationDialog extends JDialog {
    private final JTextField game=new JTextField(GameInstallation.defaultGameDirectory().toString(),32);
    private final JTextField world=new JTextField(32);
    private final JTextArea paths=new JTextArea(6,32);
    private final ItemProject project;
    InstallationDialog(Component owner,ItemProject project) {
        super(SwingUtilities.getWindowAncestor(owner),"Установка предметов в Minecraft",ModalityType.APPLICATION_MODAL);
        this.project=project;
        JPanel content=new JPanel(new BorderLayout(12,16)); content.setBorder(BorderFactory.createEmptyBorder(18,20,18,20)); setContentPane(content);
        JTextArea explanation=text("Закройте Minecraft и сервер перед установкой. Требуются Fabric и Fabric API для 1.20.1.\nВыберите папку нужной сборки. Стандартный путь предложен ниже; у стороннего лаунчера он может отличаться.");
        content.add(explanation,BorderLayout.NORTH);
        JPanel form=new JPanel(new BorderLayout(0,14));
        JPanel folders=new JPanel(new GridLayout(0,1,0,12));
        folders.add(folder("Папка Minecraft или сервера",game,false));
        boolean hasBlocks=project.items.stream().anyMatch(item -> project.type(item).block() && !item.existing);
        if (hasBlocks) folders.add(folder("Мир для правил добычи блоков",world,true));
        form.add(folders,BorderLayout.NORTH);
        paths.setEditable(false); paths.setLineWrap(true); paths.setWrapStyleWord(true); paths.setOpaque(false);
        form.add(paths,BorderLayout.CENTER); JScrollPane scroll=new JScrollPane(form); scroll.setBorder(BorderFactory.createEmptyBorder()); content.add(scroll,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,12));
        bottom.add(text("Будут установлены мод Item Content, настройки предметов и набор текстур.\nРанее установленные файлы Item Content будут сохранены в резервной копии.\nЭтот список заменит прежний список предметов Item Content в выбранной игре.\nМиры, другие моды и каталог предметов не заменяются."),BorderLayout.NORTH);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT)); JButton cancel=new JButton("Отмена"),install=new JButton("Установить в выбранную игру");
        buttons.add(cancel); buttons.add(install); bottom.add(buttons,BorderLayout.SOUTH); content.add(bottom,BorderLayout.SOUTH);
        cancel.addActionListener(e -> dispose());
        install.addActionListener(e -> {
            final Path selectedGame, selectedWorld;
            try {
                if (game.getText().isBlank()) throw new IllegalArgumentException("Выберите папку Minecraft.");
                selectedGame=Path.of(game.getText().trim());
                selectedWorld=world.getText().isBlank() ? null : Path.of(world.getText().trim());
            } catch (Exception failure) { JOptionPane.showMessageDialog(this,failure.getMessage(),"Проверьте папку",JOptionPane.ERROR_MESSAGE); return; }
            install.setEnabled(false); cancel.setEnabled(false); install.setText("Установка…");
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            new SwingWorker<GameInstallation.Result,Void>() {
                @Override protected GameInstallation.Result doInBackground() throws Exception {
                    return GameInstallation.install(project,selectedGame,selectedWorld,GameInstallation.bundledMod().orElse(null));
                }
                @Override protected void done() {
                    try {
                        var result=get();
                        JOptionPane.showMessageDialog(InstallationDialog.this,"Установлено в: "+result.game()+"\n\nЗапустите игру и включите набор ресурсов Item Content.\nПосле изменения предметов или вкладок полностью перезапустите игру и сервер.\nДля сетевой игры установите тот же список на сервер и все клиенты.\n\nРезервная копия и список путей восстановления: "+result.backup(),"Предметы установлены",JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } catch (Exception failure) {
                        Throwable cause=failure.getCause()==null ? failure : failure.getCause();
                        JOptionPane.showMessageDialog(InstallationDialog.this,cause.getMessage(),"Установка не выполнена",JOptionPane.ERROR_MESSAGE);
                    } finally {
                        install.setEnabled(true); cancel.setEnabled(true); install.setText("Установить в выбранную игру");
                        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    }
                }
            }.execute();
        });
        game.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePaths(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePaths(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePaths(); }
        });
        updatePaths(); pack(); Rectangle available=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(780,available.width-30),Math.min(getHeight(),available.height-30)); setLocationRelativeTo(owner);
    }
    private JPanel folder(String title,JTextField input,boolean isWorld) {
        JPanel row=new JPanel(new BorderLayout(8,5)); row.add(new JLabel(title),BorderLayout.NORTH); row.add(input,BorderLayout.CENTER);
        JButton choose=new JButton("Выбрать папку…"); row.add(choose,BorderLayout.EAST);
        choose.addActionListener(e -> {
            Path initial;
            try { initial=Path.of(game.getText().trim()); } catch (RuntimeException failure) { initial=GameInstallation.defaultGameDirectory(); }
            if (isWorld) initial=initial.resolve("saves");
            JFileChooser dialog=new JFileChooser(initial.toFile()); dialog.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dialog.setDialogTitle(isWorld ? "Выберите папку мира с файлом level.dat" : "Выберите корневую папку сборки Minecraft");
            if (dialog.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) input.setText(dialog.getSelectedFile().getAbsolutePath());
        }); return row;
    }
    private void updatePaths() {
        try {
            Path root=Path.of(game.getText().trim()).toAbsolutePath().normalize();
            paths.setText("Куда попадут файлы:\nМод: "+root.resolve("mods")+"\nНастройки: "+root.resolve("config/item-content/items.json")
                    +"\nНабор текстур: "+root.resolve("resourcepacks/item-content")
                    +(project.items.stream().anyMatch(item -> project.type(item).block() && !item.existing) ? "\nПравила добычи: datapacks/item-content в выбранном мире" : ""));
        } catch (RuntimeException failure) { paths.setText("Укажите корректный путь к папке игры."); }
    }
    private static JTextArea text(String value) {
        JTextArea area=new JTextArea(value); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setOpaque(false); return area;
    }
}
