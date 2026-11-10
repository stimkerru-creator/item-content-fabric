package dev.itemcontent.editor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Separate desktop app for the item-content repository. Recipes belong to item-parameters. */
public final class ItemContentEditor {
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        UIManager.put("Label.font", new Font("Dialog", Font.PLAIN, 14));
        UIManager.put("Button.font", new Font("Dialog", Font.PLAIN, 14));
        UIManager.put("TextField.font", new Font("Dialog", Font.PLAIN, 14));
        SwingUtilities.invokeLater(() -> {
            EditorPanel panel = new EditorPanel();
            JFrame frame = new JFrame("Item Content — конструктор предметов");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setContentPane(panel); frame.setMinimumSize(new Dimension(980, 650));
            frame.setSize(1180, 780); frame.setLocationRelativeTo(null);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { if (panel.mayDiscard()) frame.dispose(); }
            });
            frame.setVisible(true);
            if (args.length == 2 && args[0].equals("--screenshot")) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
                        ImageIO.write(image, "png", Path.of(args[1]).toFile());
                    } catch (Exception failure) { failure.printStackTrace(); System.exit(1); }
                    frame.dispose();
                });
            }
        });
    }

    static final class EditorPanel extends JPanel {
        private ItemProject project = new ItemProject();
        private ItemProject.Entry current;
        private final DefaultListModel<ItemProject.Entry> listModel = new DefaultListModel<>();
        private final JList<ItemProject.Entry> list = new JList<>(listModel);
        private final JTextField name = new JTextField(), id = new JTextField(), appearance = new JTextField();
        private final JCheckBox custom = new JCheckBox("Своя картинка PNG"), fireproof = new JCheckBox("Не сгорает в огне и лаве");
        private final JSpinner maxCount = new JSpinner(new SpinnerNumberModel(64, 1, 99, 1));
        private final JLabel preview = new JLabel("PNG не выбрана", SwingConstants.CENTER);
        private final JLabel texturePath = new JLabel("Используется готовая модель Minecraft.");
        private final JLabel status = new JLabel("Добавьте предмет, задайте название и изображение, затем экспортируйте.");
        private final JButton choosePng = new JButton("Выбрать PNG…");
        private final DefaultTableModel fields = new DefaultTableModel(new String[]{"ID параметра", "Начальное значение", "Минимум", "Максимум"}, 0);
        private final JTable table = new JTable(fields);
        private boolean refreshing, dirty;
        private Path document;
        private String selectedTexture = "";

        EditorPanel() {
            super(new BorderLayout(12, 12)); setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
            JPanel top = new JPanel(new BorderLayout(0, 10));
            JLabel title = new JLabel("Конструктор новых предметов"); title.setFont(title.getFont().deriveFont(Font.BOLD, 23));
            top.add(title, BorderLayout.NORTH);
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            button(toolbar, "Новый проект", "newProject", () -> { if (mayDiscard()) reset(); });
            button(toolbar, "Открыть…", "openProject", this::open);
            button(toolbar, "Сохранить проект…", "saveProject", () -> save());
            button(toolbar, "Экспорт для Minecraft…", "export", this::export);
            button(toolbar, "Инструкция", "help", this::help);
            top.add(toolbar, BorderLayout.CENTER);
            top.add(new JLabel("1. Добавьте предмет    →    2. Выберите название и внешний вид    →    3. Экспортируйте в игру"), BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);

            JPanel left = new JPanel(new BorderLayout(0, 10));
            left.setBorder(BorderFactory.createTitledBorder("Ваши предметы"));
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(34); list.setName("items");
            left.add(new JScrollPane(list), BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            button(actions, "+ Добавить", "addItem", this::addItem);
            button(actions, "Удалить", "removeItem", this::removeItem);
            left.add(actions, BorderLayout.SOUTH);

            JPanel details = new JPanel(new BorderLayout(8, 12));
            JPanel general = new JPanel(new GridBagLayout());
            name.setName("itemName"); id.setName("itemId"); appearance.setName("appearance");
            row(general, 0, "Название в игре", name);
            row(general, 1, "ID: itemcontent:", id);
            row(general, 2, "Предметов в стаке", maxCount);
            row(general, 3, "Свойства", fireproof);
            row(general, 4, "Изображение", custom);
            row(general, 5, "Готовая модель (ID)", appearance);
            JPanel imageTools = new JPanel(new BorderLayout(12, 6));
            preview.setPreferredSize(new Dimension(148, 148)); preview.setOpaque(true); preview.setBackground(new Color(235, 238, 242));
            preview.setBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)));
            JPanel previewHolder = new JPanel(new BorderLayout()); previewHolder.add(preview, BorderLayout.NORTH);
            imageTools.add(previewHolder, BorderLayout.WEST);
            JPanel imageActions = new JPanel(); imageActions.setLayout(new BoxLayout(imageActions, BoxLayout.Y_AXIS));
            choosePng.setAlignmentX(LEFT_ALIGNMENT); imageActions.add(choosePng); imageActions.add(Box.createVerticalStrut(12));
            texturePath.setAlignmentX(LEFT_ALIGNMENT); imageActions.add(texturePath); imageActions.add(Box.createVerticalStrut(8));
            JLabel imageHint = new JLabel("<html>PNG, например 16×16, 32×32 или 64×64.<br>Без PNG используется готовая модель из поля выше.</html>");
            imageHint.setAlignmentX(LEFT_ALIGNMENT); imageActions.add(imageHint); imageTools.add(imageActions, BorderLayout.CENTER);
            JPanel card = new JPanel(new BorderLayout(12, 16)); card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            card.add(general, BorderLayout.NORTH); card.add(imageTools, BorderLayout.CENTER);
            card.add(new JLabel("ID: латинские строчные буквы, цифры и _. Например: steel_plate"), BorderLayout.SOUTH);

            JPanel parameterPanel = new JPanel(new BorderLayout(8, 12)); parameterPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            parameterPanel.add(new JLabel("<html>Необязательно. Начальные характеристики для мода Item Parameters.<br>Рецепты и формулы настраиваются в отдельном первом проекте.</html>"), BorderLayout.NORTH);
            table.setRowHeight(28); table.setName("parameters"); parameterPanel.add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel parameterActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            button(parameterActions, "+ Параметр", "addField", () -> { if (current != null) fields.addRow(new Object[]{nextFieldId(), "0", "-21474836.48", "21474836.47"}); });
            button(parameterActions, "Удалить параметр", "removeField", () -> { int row = table.getSelectedRow(); if (row >= 0) { stopTable(); fields.removeRow(row); } });
            parameterPanel.add(parameterActions, BorderLayout.SOUTH);
            JTabbedPane tabs = new JTabbedPane(); tabs.addTab("Предмет и картинка", card); tabs.addTab("Начальные параметры (необязательно)", parameterPanel);
            details.add(tabs, BorderLayout.CENTER);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, details);
            split.setResizeWeight(.25); split.setDividerLocation(290); left.setMinimumSize(new Dimension(220, 200)); details.setMinimumSize(new Dimension(600, 300));
            add(split, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);

            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || refreshing) return;
                try {
                    commit(); current = list.getSelectedValue(); showCurrent();
                } catch (IllegalArgumentException failure) {
                    refreshing = true;
                    list.setSelectedValue(current, true);
                    refreshing = false;
                    status.setText("Сначала исправьте данные текущего предмета: " + failure.getMessage());
                }
            });
            DocumentListener listener = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { changed(); }
                public void removeUpdate(DocumentEvent e) { changed(); }
                public void changedUpdate(DocumentEvent e) { changed(); }
                private void changed() { if (!refreshing) dirty = true; }
            };
            name.getDocument().addDocumentListener(listener); id.getDocument().addDocumentListener(listener); appearance.getDocument().addDocumentListener(listener);
            maxCount.addChangeListener(e -> { if (!refreshing) dirty = true; });
            ((JSpinner.DefaultEditor) maxCount.getEditor()).getTextField().getDocument().addDocumentListener(listener);
            fireproof.addActionListener(e -> dirty = true);
            custom.addActionListener(e -> { dirty = true; updateImage(); });
            fields.addTableModelListener(e -> { if (!refreshing) dirty = true; });
            choosePng.addActionListener(e -> guarded(() -> {
                JFileChooser chooser = chooser("Выберите PNG предмета", false); chooser.setFileFilter(new FileNameExtensionFilter("PNG", "png"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                ItemProject.validatePng(chooser.getSelectedFile().toPath());
                selectedTexture = chooser.getSelectedFile().getAbsolutePath(); custom.setSelected(true); dirty = true; updateImage();
            }));
            reset();
        }

        private void reset() {
            project = new ItemProject(); current = null; document = null;
            refreshing = true; listModel.clear(); refreshing = false;
            addItem(); dirty = false;
        }
        private void addItem() {
            commit();
            ItemProject.Entry entry = new ItemProject.Entry();
            int sequence = project.items.size() + 1;
            while (containsId("itemcontent:item_" + sequence)) sequence++;
            entry.id = "itemcontent:item_" + sequence; entry.name = "Предмет " + sequence;
            project.items.add(entry); listModel.addElement(entry); list.setSelectedValue(entry, true); dirty = true;
        }
        private boolean containsId(String id) { return project.items.stream().anyMatch(item -> item.id.equals(id)); }
        private void removeItem() {
            if (current == null) return;
            if (JOptionPane.showConfirmDialog(this, "Удалить «" + current.name + "» из проекта?", "Удаление предмета", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            int index = list.getSelectedIndex(); refreshing = true;
            project.items.remove(current); listModel.removeElement(current); current = null;
            refreshing = false;
            if (!listModel.isEmpty()) list.setSelectedIndex(Math.min(index, listModel.size() - 1)); else showCurrent();
            dirty = true;
        }
        private String nextFieldId() {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int row = 0; row < fields.getRowCount(); row++) ids.add(cell(row, 0));
            int next = 1;
            while (ids.contains("itemcontent:parameter_" + next)) next++;
            return "itemcontent:parameter_" + next;
        }
        private void stopTable() { if (table.isEditing()) table.getCellEditor().stopCellEditing(); }
        private void commit() {
            if (refreshing || current == null) return;
            stopTable();
            try { maxCount.commitEdit(); } catch (java.text.ParseException e) { throw new IllegalArgumentException("Введите целое количество предметов в стаке."); }
            current.name = name.getText().trim(); current.id = "itemcontent:" + id.getText().trim();
            current.appearance = appearance.getText().trim(); current.maxCount = ((Number) maxCount.getValue()).intValue();
            current.customModel = custom.isSelected(); current.fireproof = fireproof.isSelected(); current.texture = selectedTexture;
            current.fields.clear();
            for (int row = 0; row < fields.getRowCount(); row++) {
                ItemProject.Field f = new ItemProject.Field();
                f.id = cell(row, 0); f.value = cell(row, 1); f.min = cell(row, 2); f.max = cell(row, 3); current.fields.add(f);
            }
            list.repaint();
        }
        private String cell(int row, int column) { Object value = fields.getValueAt(row, column); return value == null ? "" : value.toString().trim(); }
        private void showCurrent() {
            refreshing = true;
            boolean enabled = current != null;
            for (JComponent component : new JComponent[]{name, id, appearance, maxCount, custom, fireproof, choosePng, table}) component.setEnabled(enabled);
            name.setText(enabled ? current.name : ""); id.setText(enabled ? current.id.substring("itemcontent:".length()) : "");
            appearance.setText(enabled ? current.appearance : "minecraft:iron_ingot"); maxCount.setValue(enabled ? current.maxCount : 64);
            custom.setSelected(enabled && current.customModel); fireproof.setSelected(enabled && current.fireproof);
            selectedTexture = enabled ? current.texture : ""; fields.setRowCount(0);
            if (enabled) for (var field : current.fields) fields.addRow(new Object[]{field.id, field.value, field.min, field.max});
            updateImage(); refreshing = false;
        }
        private void updateImage() {
            appearance.setEnabled(current != null && !custom.isSelected()); choosePng.setEnabled(current != null);
            preview.setIcon(null); preview.setText(custom.isSelected() ? "Выберите PNG" : "Готовая модель");
            texturePath.setText("Используется готовая модель Minecraft."); texturePath.setToolTipText(null);
            if (custom.isSelected() && selectedTexture != null && !selectedTexture.isBlank()) {
                try {
                    texturePath.setText(Path.of(selectedTexture).getFileName().toString()); texturePath.setToolTipText(selectedTexture);
                    ItemProject.validatePng(Path.of(selectedTexture));
                    BufferedImage image = ImageIO.read(Path.of(selectedTexture).toFile());
                    double scale = Math.min(128.0 / image.getWidth(), 128.0 / image.getHeight());
                    preview.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1, (int) (image.getWidth() * scale)),
                            Math.max(1, (int) (image.getHeight() * scale)), Image.SCALE_REPLICATE))); preview.setText("");
                } catch (Exception e) { preview.setText("PNG недоступна"); }
            }
        }
        private boolean save() {
            try {
                commit(); project.validate();
                JFileChooser chooser = chooser("Сохранить проект предметов", false);
                chooser.setFileFilter(new FileNameExtensionFilter("Проект Item Content (*.icproject)", "icproject"));
                if (document != null) chooser.setSelectedFile(document.toFile());
                if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
                Path path = chooser.getSelectedFile().toPath();
                if (!path.getFileName().toString().endsWith(".icproject")) path = path.resolveSibling(path.getFileName() + ".icproject");
                if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Заменить этот файл проекта?", "Сохранение", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
                project.save(path); document = path; dirty = false; status.setText("Проект сохранён: " + path); return true;
            } catch (Exception e) { error(e); return false; }
        }
        private void open() {
            if (!mayDiscard()) return;
            guarded(() -> {
                JFileChooser chooser = chooser("Открыть проект предметов", false); chooser.setFileFilter(new FileNameExtensionFilter("Проект Item Content", "icproject"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path path = chooser.getSelectedFile().toPath(); ItemProject loaded = ItemProject.load(path);
                refreshing = true; project = loaded; current = null; listModel.clear();
                project.items.forEach(listModel::addElement); refreshing = false; list.setSelectedIndex(0);
                document = path; dirty = false; status.setText("Открыт проект: " + path);
            });
        }
        private void export() {
            guarded(() -> {
                commit(); project.validate(); JFileChooser chooser = chooser("Новая или пустая папка экспорта", true);
                if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path target = chooser.getSelectedFile().toPath(); project.export(target);
                status.setText("Экспортировано предметов: " + project.items.size() + ". Папка: " + target);
                JOptionPane.showMessageDialog(this, "Готово: " + target + "\n\n"
                        + "1. config/item-content → в config сервера и каждого клиента.\n"
                        + "2. resourcepacks/item-content → в resourcepacks клиента; включите в игре.\n"
                        + "3. Перезапустите сервер и клиент.\n"
                        + "Предметы появятся во вкладке «Ингредиенты».\n\n"
                        + "Экспорт не сохраняет исходный проект: используйте «Сохранить проект…».\n"
                        + "Рецепты настраиваются отдельно в Item Parameters.", "Предметы готовы к установке", JOptionPane.INFORMATION_MESSAGE);
            });
        }
        boolean mayDiscard() {
            if (!dirty) return true;
            int answer = JOptionPane.showConfirmDialog(this, "Сохранить изменения проекта?", "Несохранённые изменения", JOptionPane.YES_NO_CANCEL_OPTION);
            return answer == JOptionPane.NO_OPTION || answer == JOptionPane.YES_OPTION && save();
        }
        private void help() {
            JOptionPane.showMessageDialog(this, "Это отдельный конструктор новых предметов для Fabric 1.20.1.\n\n"
                    + "1. «+ Добавить» создаёт описание предмета слева.\n"
                    + "2. Название — надпись в игре. ID — постоянный адрес, например steel_plate.\n"
                    + "3. Включите «Своя картинка PNG» и выберите файл либо оставьте готовую модель.\n"
                    + "4. «Сохранить проект…» сохраняет редактируемый .icproject.\n"
                    + "5. «Экспорт для Minecraft…» создаёт конфигурацию и resource pack.\n\n"
                    + "Начальные параметры необязательны; они нужны для совместимости с Item Parameters.\n"
                    + "Формулы, рецепты и станки относятся к первому проекту.\n"
                    + "Подробная инструкция: docs/editor.md в этом репозитории.", "Как пользоваться", JOptionPane.INFORMATION_MESSAGE);
        }
        private JFileChooser chooser(String title, boolean directories) {
            JFileChooser chooser = new JFileChooser(document == null ? null : document.toAbsolutePath().getParent().toFile());
            chooser.setDialogTitle(title); if (directories) chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); return chooser;
        }
        private void guarded(CheckedAction action) { try { action.run(); } catch (Exception e) { error(e); } }
        private void error(Exception e) { status.setText("Не выполнено: " + e.getMessage()); JOptionPane.showMessageDialog(this, e.getMessage(), "Проверьте настройки", JOptionPane.ERROR_MESSAGE); }
        private void button(JPanel parent, String text, String name, CheckedAction action) {
            JButton button = new JButton(text); button.setName(name); button.addActionListener(e -> guarded(action)); parent.add(button);
        }
        private void row(JPanel panel, int row, String label, JComponent value) {
            GridBagConstraints c = new GridBagConstraints(); c.gridy = row; c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(6, 0, 6, 14);
            panel.add(new JLabel(label), c); c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(value, c);
        }
        @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    }
}
