package dev.itemcontent.editor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Locale;
import static dev.itemcontent.editor.EditorTheme.*;

/** Item-first editor. Persistence and runtime formats remain in ItemProject. */
public final class ItemContentEditor {
    public static void main(String[] args) {
        EditorTheme.setup();
        SwingUtilities.invokeLater(() -> {
            EditorPanel panel = new EditorPanel();
            JFrame frame = new JFrame("Item Content — редактор предметов");
            frame.setIconImage(EditorTheme.windowIcon());
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            Rectangle available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setContentPane(panel);
            frame.setMinimumSize(new Dimension(Math.min(980, available.width), Math.min(580, available.height)));
            frame.setSize(windowSize(available)); frame.setLocationRelativeTo(null);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { if (panel.mayDiscard()) frame.dispose(); }
            });
            frame.setVisible(true);
            for (int index = 0; index + 1 < args.length; index++) {
                if (args[index].equals("--open")) {
                    Path file = Path.of(args[index + 1]); panel.guarded(() -> panel.openList(file));
                }
            }
            if (args.length >= 2 && args[0].equals("--screenshot")) {
                if (args.length >= 4 && args[2].matches("[0-9]+") && args[3].matches("[0-9]+")) frame.setSize(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                SwingUtilities.invokeLater(() -> {
                    try {
                        frame.validate();
                        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
                        ImageIO.write(image, "png", Path.of(args[1]).toFile());
                    } catch (Exception failure) { failure.printStackTrace(); System.exit(1); }
                    frame.dispose();
                });
            }
        });
    }
    static Dimension windowSize(Rectangle available) {
        return new Dimension(Math.min(1280, Math.max(1, available.width - 48)), Math.min(820, Math.max(1, available.height - 48)));
    }

    static final class EditorPanel extends JPanel {
        private ItemProject project = new ItemProject();
        private ItemProject.Entry current;
        private final DefaultListModel<ItemProject.Entry> listModel = new DefaultListModel<>();
        private final JList<ItemProject.Entry> list = new JList<>(listModel);
        private final JTextField name = new JTextField(), id = new JTextField(), appearance = new JTextField(), search = new JTextField();
        private final JRadioButton custom = new JRadioButton("Своя PNG"), existing = new JRadioButton("Модель", true);
        private final JCheckBox fireproof = new JCheckBox("Не сгорает в огне и лаве");
        private final JSpinner maxCount = new JSpinner(new SpinnerNumberModel(64, 1, 99, 1));
        private final ImagePreview preview = new ImagePreview();
        private final JLabel texturePath = label("", 12, false, MUTED);
        private final JLabel status = label("Готово к созданию предметов", 12, false, MUTED);
        private final JLabel saveState = label("Не сохранено", 12, false, MUTED);
        private final JLabel itemCount = label("", 12, false, MUTED);
        private final JLabel selectionTitle = label("", 23, true, INK);
        private final JLabel selectionId = label("", 12, false, MUTED);
        private final JButton choosePng = new JButton("Выбрать картинку…");
        private JButton removeButton, duplicateButton;
        private boolean refreshing, dirty;
        private Path document;
        private String selectedTexture = "";

        EditorPanel() {
            super(new BorderLayout()); setBackground(BACKGROUND);
            add(header(), BorderLayout.NORTH);
            JPanel body = panel(new BorderLayout(20, 0)); body.setBorder(padding(20, 24, 16, 24));
            body.add(sidebar(), BorderLayout.WEST); body.add(inspector(), BorderLayout.CENTER); add(body, BorderLayout.CENTER);
            JPanel footer = panel(new BorderLayout(16, 0)); footer.setBorder(padding(0, 26, 12, 26));
            footer.add(status, BorderLayout.CENTER); footer.add(saveState, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
            bindChanges();
            shortcut("control N", "newItem", this::addItem);
            shortcut("control O", "openList", this::open);
            shortcut("control S", "saveList", () -> save());
            shortcut("control shift S", "saveListAs", () -> saveAs());
            shortcut("F1", "help", this::help);
            reset();
        }
        private JPanel header() {
            JPanel bar = new JPanel(new BorderLayout(20, 0));
            bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), padding(16, 24, 16, 24)));
            JPanel brand = panel(new BorderLayout(14, 0));
            JLabel mark = label("IC", 18, true, Color.WHITE); mark.setOpaque(true); mark.setBackground(ACCENT);
            mark.setHorizontalAlignment(SwingConstants.CENTER); mark.setPreferredSize(new Dimension(44, 44)); brand.add(mark, BorderLayout.WEST);
            JPanel words = panel(new GridLayout(2, 1, 0, 1));
            words.add(label("Предметы", 22, true, INK)); words.add(label("Item Content · Minecraft 1.20.1 / Fabric", 12, false, MUTED));
            brand.add(words, BorderLayout.CENTER); bar.add(brand, BorderLayout.WEST);
            JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            JButton file = button(actions, "Файл", "fileMenu", () -> {}); quiet(file);
            JPopupMenu menu = new JPopupMenu();
            menu(menu, "Открыть список предметов…", "openProject", this::open);
            menu(menu, "Сохранить список как…", "saveAs", () -> saveAs()); menu.addSeparator();
            menu(menu, "Новый список предметов…", "newProject", () -> { if (mayDiscard()) reset(); });
            menu.addSeparator(); menu(menu, "Как пользоваться", "help", this::help);
            file.addActionListener(e -> menu.show(file, 0, file.getHeight()));
            button(actions, "Сохранить", "saveProject", () -> save());
            JButton prepare = button(actions, "Подготовить для игры…", "export", this::export); primary(prepare);
            bar.add(actions, BorderLayout.EAST); return bar;
        }
        private JPanel sidebar() {
            JPanel side = new JPanel(new BorderLayout(0, 14)); side.setPreferredSize(new Dimension(254, 300)); side.setBorder(padding(16, 14, 12, 14));
            JPanel top = panel(new BorderLayout(0, 14));
            JPanel heading = panel(new BorderLayout()); heading.add(label("Все предметы", 14, true, INK), BorderLayout.WEST);
            heading.add(itemCount, BorderLayout.EAST); top.add(heading, BorderLayout.NORTH);
            JButton create = new JButton("+  Создать предмет"); create.setName("addItem"); primary(create);
            create.addActionListener(e -> guarded(this::addItem)); top.add(create, BorderLayout.CENTER);
            search.setName("searchItems"); search.putClientProperty("JTextField.placeholderText", "Поиск по названию или ID");
            top.add(search, BorderLayout.SOUTH); side.add(top, BorderLayout.NORTH);
            list.setName("items"); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setFixedCellHeight(68); list.setFixedCellWidth(216); list.setCellRenderer(new ItemCell());
            JScrollPane scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            side.add(scroll, BorderLayout.CENTER); side.add(label("Ctrl+N  —  создать предмет", 11, false, MUTED), BorderLayout.SOUTH); return side;
        }
        private JPanel inspector() {
            JPanel inspector = panel(new BorderLayout(0, 14));
            JPanel heading = panel(new BorderLayout(12, 0));
            JPanel titles = panel(new GridLayout(2, 1, 0, 2)); titles.add(selectionTitle); titles.add(selectionId); heading.add(titles, BorderLayout.CENTER);
            JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            duplicateButton = button(actions, "Дублировать", "duplicateItem", this::duplicateItem); quiet(duplicateButton);
            removeButton = button(actions, "Удалить", "removeItem", this::removeItem); quiet(removeButton);
            heading.add(actions, BorderLayout.EAST); inspector.add(heading, BorderLayout.NORTH);
            inspector.add(appearanceCard(), BorderLayout.CENTER); return inspector;
        }
        private JComponent appearanceCard() {
            preview.setName("itemPreview");
            JPanel content = new JPanel(new GridBagLayout()); content.setBorder(padding(18, 18, 18, 18));
            JPanel art = panel(new BorderLayout(0, 12)); art.setPreferredSize(new Dimension(230, 298));
            JPanel modes = panel(new GridLayout(1, 2, 4, 0)); ButtonGroup group = new ButtonGroup(); group.add(existing); group.add(custom);
            modes.add(existing); modes.add(custom); art.add(modes, BorderLayout.NORTH); art.add(preview, BorderLayout.CENTER);
            JPanel controls = panel(new BorderLayout(0, 8)); choosePng.setName("choosePng"); controls.add(choosePng, BorderLayout.NORTH);
            controls.add(texturePath, BorderLayout.CENTER); controls.add(label("PNG с прозрачностью · например 32 × 32", 11, false, MUTED), BorderLayout.SOUTH); art.add(controls, BorderLayout.SOUTH);
            JPanel form = panel(new GridBagLayout());
            name.setName("itemName"); id.setName("itemId"); appearance.setName("appearance");
            field(form, 0, "Название в игре", name, null);
            JPanel address = panel(new BorderLayout(8, 0)); address.add(label("itemcontent:", 13, false, MUTED), BorderLayout.WEST); address.add(id, BorderLayout.CENTER);
            field(form, 1, "ID предмета", address, "Строчные a–z, цифры и _. Например: steel_plate");
            JPanel properties = panel(new BorderLayout(16, 0)); ((JSpinner.DefaultEditor) maxCount.getEditor()).getTextField().setColumns(3); properties.add(maxCount, BorderLayout.WEST); properties.add(fireproof, BorderLayout.CENTER);
            field(form, 2, "Предметов в одном стаке", properties, null);
            field(form, 3, "Модель существующего предмета", appearance, null);
            appearance.setToolTipText("Например minecraft:iron_ingot. Для своей PNG это поле не используется.");
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.NORTH; c.fill = GridBagConstraints.BOTH; c.weighty = 1; content.add(art, c);
            c.gridx = 1; c.weightx = 1; c.insets = new Insets(0, 24, 0, 0); c.fill = GridBagConstraints.HORIZONTAL; content.add(form, c);
            JScrollPane scroll = new JScrollPane(content); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(18); scroll.setName("itemDetailsScroll"); return scroll;
        }
        private void bindChanges() {
            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || refreshing) return;
                try { commit(); current = list.getSelectedValue(); showCurrent(); }
                catch (IllegalArgumentException failure) {
                    refreshing = true; list.setSelectedValue(current, true); refreshing = false;
                    status.setText("Исправьте данные текущего предмета: " + failure.getMessage());
                }
            });
            DocumentListener changes = listener(() -> {
                if (!refreshing) {
                    dirty = true;
                    if (current != null) { current.name = name.getText().trim(); current.id = "itemcontent:" + id.getText().trim(); }
                    selectionTitle.setText(name.getText().isBlank() ? "Без названия" : name.getText());
                    selectionId.setText("itemcontent:" + id.getText()); list.repaint(); updateSaveState();
                }
            });
            name.getDocument().addDocumentListener(changes); id.getDocument().addDocumentListener(changes); appearance.getDocument().addDocumentListener(changes);
            ((JSpinner.DefaultEditor) maxCount.getEditor()).getTextField().getDocument().addDocumentListener(listener(this::changed));
            maxCount.addChangeListener(e -> changed()); fireproof.addActionListener(e -> changed());
            custom.addActionListener(e -> { changed(); updateImage(); }); existing.addActionListener(e -> { changed(); updateImage(); });
            search.getDocument().addDocumentListener(listener(() -> { if (!refreshing) guarded(() -> { commit(); refreshList(current); }); }));
            choosePng.addActionListener(e -> guarded(() -> {
                JFileChooser chooser = chooser("Выберите картинку предмета", false); chooser.setFileFilter(new FileNameExtensionFilter("PNG-картинка", "png"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                ItemProject.validatePng(chooser.getSelectedFile().toPath()); selectedTexture = chooser.getSelectedFile().getAbsolutePath();
                custom.setSelected(true); changed(); updateImage();
            }));
        }
        private void changed() { if (!refreshing) { dirty = true; updateSaveState(); } }
        private void updateSaveState() {
            itemCount.setText(Integer.toString(project.items.size()));
            saveState.setText(dirty ? "Есть несохранённые изменения" : document == null ? "Список ещё не сохранён" : "Все изменения сохранены");
            saveState.setForeground(dirty ? new Color(0x9b691b) : MUTED);
        }
        private void refreshList(ItemProject.Entry preferred) {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            refreshing = true; listModel.clear();
            for (var entry : project.items)
                if (query.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(query) || entry.id.toLowerCase(Locale.ROOT).contains(query)) listModel.addElement(entry);
            if (preferred != null && listModel.contains(preferred)) list.setSelectedValue(preferred, true);
            else if (!listModel.isEmpty()) list.setSelectedIndex(0);
            current = list.getSelectedValue(); refreshing = false; showCurrent();
        }
        private void duplicateItem() {
            if (current == null) return;
            commit(); ItemProject.Entry entry = new ItemProject.Entry();
            int suffix = 2; String base = current.id;
            while (containsId(base + "_" + suffix)) suffix++;
            entry.id = base + "_" + suffix; entry.name = current.name + " — копия";
            entry.appearance = current.appearance; entry.texture = current.texture; entry.customModel = current.customModel;
            entry.maxCount = current.maxCount; entry.fireproof = current.fireproof;
            project.items.add(entry); refreshing = true; search.setText(""); refreshing = false;
            refreshList(entry); changed(); name.requestFocusInWindow(); name.selectAll();
        }
        private DocumentListener listener(Runnable action) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { action.run(); }
                public void removeUpdate(DocumentEvent e) { action.run(); }
                public void changedUpdate(DocumentEvent e) { action.run(); }
            };
        }
        private JButton button(JPanel parent, String text, String name, CheckedAction action) {
            JButton button = new JButton(text); button.setName(name); button.addActionListener(e -> guarded(action)); parent.add(button); return button;
        }
        private void menu(JPopupMenu parent, String text, String name, CheckedAction action) {
            JMenuItem item = new JMenuItem(text); item.setName(name); item.addActionListener(e -> guarded(action)); parent.add(item);
        }
        private void shortcut(String key, String name, CheckedAction action) {
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
            getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { guarded(action); } });
        }
        private void field(JPanel parent, int row, String title, JComponent input, String hint) {
            JPanel group = panel(new BorderLayout(0, 7)); group.add(label(title, 13, true, INK), BorderLayout.NORTH);
            if (input instanceof JTextField text) text.setColumns(20);
            group.add(input, BorderLayout.CENTER);
            if (hint != null) group.add(label("<html>" + hint + "</html>", 11, false, MUTED), BorderLayout.SOUTH);
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = row; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTHWEST; c.insets = new Insets(0, 0, row == 3 ? 0 : 12, 0); parent.add(group, c);
        }
        private static final class ItemCell extends JPanel implements ListCellRenderer<ItemProject.Entry> {
            private final JLabel initial = label("", 20, true, ACCENT);
            private final JLabel title = label("", 14, true, INK), address = label("", 11, false, MUTED);
            ItemCell() {
                super(new BorderLayout(10, 0)); setBorder(padding(10, 10, 10, 10)); initial.setPreferredSize(new Dimension(28, 34));
                add(initial, BorderLayout.WEST); JPanel words = panel(new GridLayout(2, 1, 0, 2)); words.add(title); words.add(address); add(words, BorderLayout.CENTER);
            }
            public Component getListCellRendererComponent(JList<? extends ItemProject.Entry> list, ItemProject.Entry item, int index, boolean selected, boolean focused) {
                title.setText(item.name.isBlank() ? "Без названия" : item.name); address.setText(item.id);
                initial.setText(item.name.isBlank() ? "?" : item.name.substring(0, 1).toUpperCase(Locale.ROOT));
                setBackground(selected ? new Color(0xe9f0ff) : Color.WHITE); return this;
            }
        }

        private void reset() {
            project = new ItemProject(); current = null; document = null;
            refreshing = true; listModel.clear(); search.setText(""); refreshing = false;
            addItem(); dirty = false; status.setText("Готово к созданию предметов"); status.setToolTipText(null); updateSaveState();
        }
        private void addItem() {
            commit();
            ItemProject.Entry entry = new ItemProject.Entry();
            int sequence = project.items.size() + 1;
            while (containsId("itemcontent:item_" + sequence)) sequence++;
            entry.id = "itemcontent:item_" + sequence; entry.name = "Предмет " + sequence;
            project.items.add(entry);
            refreshing = true; search.setText(""); refreshing = false;
            refreshList(entry); dirty = true; updateSaveState();
            name.requestFocusInWindow(); name.selectAll();
        }
        private boolean containsId(String id) { return project.items.stream().anyMatch(item -> item.id.equals(id)); }
        private void removeItem() {
            if (current == null) return;
            if (JOptionPane.showConfirmDialog(this, "Удалить «" + current.name + "» из списка?", "Удаление предмета", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            int index = list.getSelectedIndex(); refreshing = true;
            project.items.remove(current); listModel.removeElement(current); current = null;
            refreshing = false;
            if (!listModel.isEmpty()) list.setSelectedIndex(Math.min(index, listModel.size() - 1)); else showCurrent();
            dirty = true; updateSaveState();
        }
        private void commit() {
            if (refreshing || current == null) return;
            try { maxCount.commitEdit(); } catch (java.text.ParseException e) { throw new IllegalArgumentException("Введите целое количество предметов в стаке."); }
            current.name = name.getText().trim(); current.id = "itemcontent:" + id.getText().trim();
            current.appearance = appearance.getText().trim(); current.maxCount = ((Number) maxCount.getValue()).intValue();
            current.customModel = custom.isSelected(); current.fireproof = fireproof.isSelected(); current.texture = selectedTexture;
            list.repaint();
        }
        private void showCurrent() {
            refreshing = true;
            boolean enabled = current != null;
            for (JComponent component : new JComponent[]{name, id, appearance, maxCount, custom, existing, fireproof, choosePng}) component.setEnabled(enabled);
            name.setText(enabled ? current.name : ""); id.setText(enabled ? current.id.substring("itemcontent:".length()) : "");
            appearance.setText(enabled ? current.appearance : "minecraft:iron_ingot"); maxCount.setValue(enabled ? current.maxCount : 64);
            custom.setSelected(enabled && current.customModel); existing.setSelected(!enabled || !current.customModel);
            fireproof.setSelected(enabled && current.fireproof);
            selectedTexture = enabled ? current.texture : "";
            selectionTitle.setText(enabled ? current.name : search.getText().isBlank() ? "Создайте первый предмет" : "Ничего не найдено");
            selectionId.setText(enabled ? current.id : "Измените поиск или нажмите «Создать предмет»");
            removeButton.setEnabled(enabled); duplicateButton.setEnabled(enabled);
            updateImage(); refreshing = false; updateSaveState();
        }
        private void updateImage() {
            appearance.setEnabled(current != null && !custom.isSelected()); choosePng.setEnabled(current != null);
            preview.showImage(null, custom.isSelected() ? "Выберите PNG" : "Модель видна в игре");
            texturePath.setText(custom.isSelected() ? "Картинка ещё не выбрана" : "Внешний вид существующего предмета"); texturePath.setToolTipText(null);
            if (custom.isSelected() && selectedTexture != null && !selectedTexture.isBlank()) {
                try {
                    Path path = Path.of(selectedTexture); ItemProject.validatePng(path);
                    BufferedImage image = ImageIO.read(path.toFile()); preview.showImage(image, "");
                    texturePath.setText(image.getWidth() + " × " + image.getHeight() + " px · " + path.getFileName()); texturePath.setToolTipText(selectedTexture);
                } catch (Exception e) { preview.showImage(null, "PNG недоступна"); texturePath.setText("Выберите картинку заново"); }
            }
        }
        private boolean save() { return save(false); }
        private boolean saveAs() { return save(true); }
        private boolean save(boolean chooseFile) {
            try {
                commit(); project.validate();
                Path path = document;
                if (chooseFile || path == null) {
                    JFileChooser chooser = chooser("Сохранить список предметов", false);
                    chooser.setFileFilter(new FileNameExtensionFilter("Список предметов (*.icproject)", "icproject"));
                    if (document != null) chooser.setSelectedFile(document.toFile());
                    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
                    path = chooser.getSelectedFile().toPath();
                    if (!path.getFileName().toString().endsWith(".icproject")) path = path.resolveSibling(path.getFileName() + ".icproject");
                    if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Заменить этот список предметов?", "Сохранение", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
                }
                project.save(path); document = path; dirty = false; status.setText(project.legacyBackup() == null ? "Список сохранён: " + path.getFileName() : "Сохранено. Прежние данные сохранены в резервной копии.");
                status.setToolTipText(project.legacyBackup() == null ? path.toString() : project.legacyBackup().toString()); updateSaveState(); return true;
            } catch (Exception e) { error(e); return false; }
        }
        private void open() {
            if (!mayDiscard()) return;
            guarded(() -> {
                JFileChooser chooser = chooser("Открыть список предметов", false); chooser.setFileFilter(new FileNameExtensionFilter("Список предметов", "icproject"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                openList(chooser.getSelectedFile().toPath());
            });
        }
        void openList(Path path) throws java.io.IOException {
            ItemProject loaded = ItemProject.load(path);
            refreshing = true; project = loaded; current = null; search.setText(""); refreshing = false;
            refreshList(project.items.get(0));
            document = path; dirty = false; status.setText(loaded.hasLegacyData() ? "Старые характеристики не используются. При сохранении будет создана резервная копия." : "Открыт список: " + path.getFileName()); status.setToolTipText(path.toString()); updateSaveState();
        }
        private void export() {
            guarded(() -> {
                commit(); project.validate(); JFileChooser chooser = chooser("Куда подготовить файлы для игры — новая или пустая папка", true);
                if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path target = chooser.getSelectedFile().toPath(); project.export(target);
                status.setText("Подготовлено предметов: " + project.items.size() + ". Папка: " + target);
                JOptionPane.showMessageDialog(this, "Готово: " + target + "\n\n"
                        + "1. config/item-content → в config сервера и каждого клиента.\n"
                        + "2. resourcepacks/item-content → в resourcepacks клиента; включите в игре.\n"
                        + "3. Перезапустите сервер и клиент.\n"
                        + "Предметы появятся во вкладке «Ингредиенты».\n\n"
                        + "«Сохранить» сохраняет список для дальнейшего редактирования.\n"
                        + "Item Content работает самостоятельно.", "Предметы готовы к установке", JOptionPane.INFORMATION_MESSAGE);
            });
        }
        boolean mayDiscard() {
            if (!dirty) return true;
            int answer = JOptionPane.showConfirmDialog(this, "Сохранить изменения списка предметов?", "Несохранённые изменения", JOptionPane.YES_NO_CANCEL_OPTION);
            return answer == JOptionPane.NO_OPTION || answer == JOptionPane.YES_OPTION && save();
        }
        private void help() {
            JOptionPane.showMessageDialog(this, "1. «Создать предмет» добавляет предмет в список слева.\n"
                    + "2. Справа задайте название, внешний вид и свойства.\n"
                    + "3. «Сохранить» запоминает весь список для дальнейшего редактирования.\n"
                    + "4. «Подготовить для игры» создаёт настройки и картинки для Minecraft.\n\n"
                    + "Список предметов — ваши сохранённые настройки. Новый мод при этом не создаётся.\n"
                    + "Один мод Item Content загружает все подготовленные вами предметы.\n\n"
                    + "Подробная инструкция: docs/editor.md.", "Как создать предмет", JOptionPane.INFORMATION_MESSAGE);
        }
        private JFileChooser chooser(String title, boolean directories) {
            JFileChooser chooser = new JFileChooser(document == null ? null : document.toAbsolutePath().getParent().toFile());
            chooser.setDialogTitle(title); if (directories) chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); return chooser;
        }
        private void guarded(CheckedAction action) { try { action.run(); } catch (Exception e) { error(e); } }
        private void error(Exception e) { status.setText("Не выполнено: " + e.getMessage()); JOptionPane.showMessageDialog(this, e.getMessage(), "Проверьте настройки", JOptionPane.ERROR_MESSAGE); }
        @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    }
}
