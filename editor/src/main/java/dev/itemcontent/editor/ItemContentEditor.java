package dev.itemcontent.editor;

import javax.imageio.ImageIO;
import dev.itemcontent.definition.*;
import com.google.gson.*;
import java.awt.datatransfer.*;
import java.util.*;
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
                @Override public void windowClosing(WindowEvent e) { if (panel.mayClose()) frame.dispose(); }
            });
            frame.setVisible(true);
            for (int index = 0; index + 1 < args.length; index++) {
                if (args[index].equals("--open")) {
                    Path file = Path.of(args[index + 1]); panel.guarded(() -> panel.openList(file));
                }
            }
            for (int index = 0; index + 1 < args.length; index++) if (args[index].equals("--select")) panel.selectItem(args[index + 1]);
            for (int index=0;index+1<args.length;index++) if(args[index].equals("--game")) {
                Path folder=Path.of(args[index+1]); panel.guarded(() -> panel.loadCatalog(CatalogPaths.locate(folder)));
            }
            if (Arrays.asList(args).contains("--behavior")) ((JTabbedPane) findNamed(panel, "detailsTabs")).setSelectedIndex(1);
            if (args.length >= 3 && args[0].equals("--screenshot-catalog")) {
                try {
                    Path file = Path.of(args[2]); panel.loadCatalog(file);
                    var dialog = new CatalogDialog(panel, panel.project, panel.catalog, file.toAbsolutePath().getParent(), entry -> {});
                    dialog.setModalityType(Dialog.ModalityType.MODELESS); dialog.setVisible(true);
                    SwingUtilities.invokeLater(() -> {
                        try { dialog.validate(); var image = new BufferedImage(dialog.getContentPane().getWidth(), dialog.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics(); dialog.getContentPane().printAll(graphics); graphics.dispose(); ImageIO.write(image,"png",Path.of(args[1]).toFile()); }
                        catch(Exception failure) { failure.printStackTrace(); System.exit(1); }
                        dialog.dispose(); frame.dispose();
                    });
                } catch(Exception failure) { failure.printStackTrace(); System.exit(1); }
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
    private static Component findNamed(Container parent, String name) {
        for (var child : parent.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof Container nested) { var found = findNamed(nested, name); if (found != null) return found; } } return null;
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
        private final JRadioButton custom = new JRadioButton("Простая текстура"), existing = new JRadioButton("Как у предмета из игры", true);
        private final JRadioButton imported = new JRadioButton("Модель Minecraft");
        private final JButton importModel = new JButton("Импортировать модель…");
        private final JCheckBox fireproof = new JCheckBox("Не сгорает в огне и лаве");
        private final JSpinner maxCount = new JSpinner(new SpinnerNumberModel(64, 1, 99, 1));
        private final ImagePreview preview = new ImagePreview();
        private final JTextField modelSource = new JTextField();
        private final JLabel texturePath = label("", 12, false, MUTED);
        private final JLabel status = label("Готово к созданию предметов", 12, false, MUTED);
        private final JLabel saveState = label("Не сохранено", 12, false, MUTED);
        private final JLabel itemCount = label("", 12, false, MUTED);
        private final JLabel selectionTitle = label("", 23, true, INK);
        private final JLabel selectionId = label("", 12, false, MUTED);
        private final JComboBox<String> typeCategory = new JComboBox<>();
        private final JComboBox<TypeDefinition> itemType = new JComboBox<>();
        private final JComboBox<GroupChoice> creativeGroup = new JComboBox<>();
        private final JLabel idPrefix = label("itemcontent:", 13, false, MUTED);
        private final BehaviorForm behavior = new BehaviorForm(this::changed);
        private final JTabbedPane groupTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        private final java.util.List<String> tabGroups = new ArrayList<>();
        private String selectedGroup = "";
        private GameCatalog catalog;
        private Path catalogFile;
        private final JButton textureOptions = new JButton("Другие текстуры…");
        private record GroupChoice(String id, String name) { @Override public String toString() { return name; } }
        private final JButton choosePng = new JButton("Выбрать PNG-файл…");
        private final JButton chooseGameAppearance = new JButton("Выбрать предмет…");
        private final JPanel appearanceActions=panel(new CardLayout());
        private JButton removeButton, duplicateButton;
        private boolean refreshing, dirty;
        private Path document;
        private String selectedTexture = "";

        EditorPanel() {
            super(new BorderLayout()); setBackground(BACKGROUND);
            add(header(), BorderLayout.NORTH);
            JPanel body = panel(new BorderLayout(20, 0)); body.setBorder(padding(20, 24, 16, 24));
            JPanel categoryBar = panel(new BorderLayout(8, 0)); groupTabs.setName("creativeTabs"); groupTabs.setToolTipText("Фильтр вашего списка. Где предмет появится в игре, выбирается справа: «Вкладка в креативе»."); groupTabs.setPreferredSize(new Dimension(400, 42));
            categoryBar.add(groupTabs, BorderLayout.CENTER); JButton newGroup = new JButton("+ Вкладка в игре"); newGroup.setName("addGroup"); newGroup.addActionListener(e -> guarded(this::addGroup)); categoryBar.add(newGroup, BorderLayout.EAST);
            body.add(categoryBar, BorderLayout.NORTH);
            body.add(sidebar(), BorderLayout.WEST); body.add(inspector(), BorderLayout.CENTER); add(body, BorderLayout.CENTER);
            JPanel footer = panel(new BorderLayout(16, 0)); footer.setBorder(padding(0, 26, 12, 26));
            footer.add(status, BorderLayout.CENTER); footer.add(saveState, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
            bindChanges();
            groupTabs.addChangeListener(e -> { if (!refreshing && groupTabs.getSelectedIndex() >= 0) guarded(() -> { commit(); selectedGroup = tabGroups.get(groupTabs.getSelectedIndex()); refreshList(current); }); });
            groupTabs.setTransferHandler(new TransferHandler() {
                @Override public boolean canImport(TransferSupport support) { return support.isDataFlavorSupported(DataFlavor.stringFlavor) && support.isDrop(); }
                @Override public boolean importData(TransferSupport support) {
                    if (!canImport(support)) return false;
                    try {
                        Point point = support.getDropLocation().getDropPoint(); int index = groupTabs.indexAtLocation(point.x, point.y);
                        if (index <= 0) return false;
                        String value = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        commit(); ItemProject.Entry entry = project.items.stream().filter(item -> item.id.equals(value)).findFirst().orElse(null);
                        if (entry == null || entry.categoryLocked) return false;
                        entry.group = tabGroups.get(index); selectedGroup = entry.group; changed(); rebuildGroupTabs(); refreshList(entry); return true;
                    } catch (Exception failure) { error(failure); return false; }
                }
            });
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
            menu(menu, "Подключить сборку Minecraft…", "openCatalog", this::openCatalog);
            menu(menu, "Экспортировать файлы для переноса…", "exportFiles", this::export);
            menu(menu, "Сохранить список как…", "saveAs", () -> saveAs()); menu.addSeparator();
            menu(menu, "Новый список предметов…", "newProject", () -> { if (mayDiscard()) reset(); });
            menu.addSeparator(); menu(menu, "Как пользоваться", "help", this::help);
            file.addActionListener(e -> menu.show(file, 0, file.getHeight()));
            button(actions, "Сохранить", "saveProject", () -> save());
            JButton prepare = button(actions, "Установить в Minecraft…", "export", this::install); primary(prepare);
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
            side.add(scroll, BorderLayout.CENTER);
            JButton browse = new JButton("Каталог предметов игры…"); browse.setName("browseCatalog"); browse.addActionListener(e -> guarded(this::browseCatalog)); side.add(browse, BorderLayout.SOUTH);
            if (!GraphicsEnvironment.isHeadless()) list.setDragEnabled(true);
            list.setTransferHandler(new TransferHandler() {
                @Override public int getSourceActions(JComponent component) { return COPY; }
                @Override protected Transferable createTransferable(JComponent component) { var entry = list.getSelectedValue(); return entry == null ? null : new StringSelection(entry.id); }
            });
            return side;
        }
        private JPanel inspector() {
            JPanel inspector = panel(new BorderLayout(0, 14));
            JPanel heading = panel(new BorderLayout(12, 0));
            JPanel titles = panel(new GridLayout(2, 1, 0, 2)); titles.add(selectionTitle); titles.add(selectionId); heading.add(titles, BorderLayout.CENTER);
            JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            duplicateButton = button(actions, "Дублировать", "duplicateItem", this::duplicateItem); quiet(duplicateButton);
            removeButton = button(actions, "Удалить", "removeItem", this::removeItem); quiet(removeButton);
            heading.add(actions, BorderLayout.EAST); inspector.add(heading, BorderLayout.NORTH);
            JPanel editor = panel(new BorderLayout(0, 10)); JPanel selectors = panel(new GridLayout(1, 3, 12, 0));
            typeCategory.setName("typeCategory"); itemType.setName("itemType"); creativeGroup.setName("creativeGroup");
            selectors.add(selector("Категория", typeCategory)); selectors.add(selector("Тип предмета", itemType)); selectors.add(selector("Вкладка в креативе", creativeGroup)); editor.add(selectors, BorderLayout.NORTH);
            JTabbedPane details = new JTabbedPane(); details.setName("detailsTabs"); details.addTab("Название и внешний вид", appearanceCard());
            JPanel gameProperties=panel(new BorderLayout(0,0));
            JPanel general=panel(new GridBagLayout()); general.setBorder(padding(16,18,0,18));
            JPanel stackSettings=panel(new BorderLayout(16,0)); ((JSpinner.DefaultEditor) maxCount.getEditor()).getTextField().setColumns(3);
            stackSettings.add(maxCount,BorderLayout.WEST); stackSettings.add(fireproof,BorderLayout.CENTER);
            field(general,0,"Предметов в одном стаке",stackSettings,null);
            gameProperties.add(general,BorderLayout.NORTH); gameProperties.add(behavior,BorderLayout.CENTER);
            JScrollPane properties = new JScrollPane(gameProperties); properties.setBorder(BorderFactory.createEmptyBorder()); properties.getVerticalScrollBar().setUnitIncrement(22);
            details.addTab("Свойства в игре", properties); editor.add(details, BorderLayout.CENTER);
            inspector.add(editor, BorderLayout.CENTER); return inspector;
        }
        private JComponent appearanceCard() {
            preview.setName("itemPreview");
            JPanel content = new JPanel(new GridBagLayout()); content.setBorder(padding(18, 18, 18, 18));
            JPanel art = panel(new BorderLayout(0, 12)); preview.setPreferredSize(new Dimension(230,100)); preview.setMinimumSize(new Dimension(160,100));
            imported.setName("minecraftModelMode"); custom.setName("ownTextureMode"); existing.setName("gameAppearanceMode");
            custom.setToolTipText("Оформить новый предмет своей PNG-текстурой.");
            existing.setToolTipText("Использовать готовое оформление из игры. Игровые свойства не копируются.");
            JPanel modes = panel(new GridLayout(0, 1, 0, 4)); ButtonGroup group = new ButtonGroup(); group.add(existing); group.add(custom); group.add(imported);
            modes.add(imported); modes.add(custom); modes.add(existing);
            imported.setToolTipText("Собственная модель Minecraft Java: JSON, текстуры и анимация из ресурс-пака.");
            importModel.setName("importMinecraftModel"); importModel.addActionListener(e -> guarded(this::importMinecraftModel));
            appearanceActions.add(importModel,"model");
            choosePng.setName("choosePng"); appearanceActions.add(choosePng,"png");
            chooseGameAppearance.setName("pickGameAppearance"); chooseGameAppearance.addActionListener(e -> guarded(this::pickAppearance)); appearanceActions.add(chooseGameAppearance,"game");
            JPanel artTop=panel(new BorderLayout(0,10)); artTop.add(modes,BorderLayout.NORTH); artTop.add(appearanceActions,BorderLayout.CENTER);
            art.add(artTop,BorderLayout.NORTH); art.add(preview,BorderLayout.CENTER); art.add(texturePath,BorderLayout.SOUTH);
            textureOptions.setName("textureOptions"); textureOptions.addActionListener(e -> guarded(this::textures));
            JPanel form = panel(new GridBagLayout());
            name.setName("itemName"); id.setName("itemId"); appearance.setName("appearance");
            field(form, 0, "Название в игре", name, null);
            JPanel address = panel(new BorderLayout(8, 0)); address.add(idPrefix, BorderLayout.WEST); address.add(id, BorderLayout.CENTER);
            field(form, 1, "ID предмета", address, "Строчные a–z, цифры и _. Например: steel_plate");

            appearance.setEditable(false); appearance.setFocusable(false); appearance.setOpaque(false); appearance.setBorder(BorderFactory.createEmptyBorder(3,0,3,0));
            field(form, 2, "Выбранный образец внешнего вида", appearance,
                    "Используются модель и текстуры выбранного предмета.<br>Урон, прочность и другие свойства задаются отдельно.");
            appearance.getParent().setName("gameAppearanceSource");
            modelSource.setEditable(false); modelSource.setFocusable(false); modelSource.setName("importedModelSource");
            field(form,2,"Импортированная модель",modelSource,"Файлы модели сохранены в списке предметов.<br>Слева — текстура. Объёмный вид проверяется в Minecraft.");
            GridBagConstraints extraConstraints=new GridBagConstraints(); extraConstraints.gridx=0; extraConstraints.gridy=3;
            extraConstraints.anchor=GridBagConstraints.NORTHWEST; extraConstraints.insets=new Insets(10,0,0,0); form.add(textureOptions,extraConstraints);
            appearance.setToolTipText("ID предмета, чьё оформление нужно использовать. Его урон, прочность и другие свойства не копируются.");
            typeCategory.setToolTipText("Общая группа типов: оружие, еда, инструменты или блоки.");
            itemType.setToolTipText("Определяет, что предмет умеет делать в игре: меч, лук, еда и другие типы.");
            creativeGroup.setToolTipText("Где предмет появится в инвентаре творческого режима. Верхние вкладки фильтруют список слева.");
            textureOptions.setToolTipText("Отдельные картинки для граней блока, кадров лука или надетой брони.");
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.NORTH; c.fill = GridBagConstraints.BOTH; c.weighty = 1; content.add(art, c);
            c.gridx = 1; c.weightx = 1; c.insets = new Insets(0, 24, 0, 0); c.fill = GridBagConstraints.HORIZONTAL; content.add(form, c);
            JScrollPane scroll = new JScrollPane(content); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(18); scroll.setName("itemDetailsScroll"); return scroll;
        }
        private void bindChanges() {
            typeCategory.addActionListener(e -> { if (!refreshing && current != null && !current.existing) guarded(() -> {
                String category = Objects.toString(typeCategory.getSelectedItem(), "");
                TypeDefinition next = project.types().values().stream().filter(type -> type.category().equals(category)).findFirst().orElseThrow(); changeType(next);
            }); });
            itemType.addActionListener(e -> { if (!refreshing && current != null && !current.existing && itemType.getSelectedItem() instanceof TypeDefinition type) guarded(() -> changeType(type)); });
            creativeGroup.addActionListener(e -> { if (!refreshing && current != null && creativeGroup.getSelectedItem() instanceof GroupChoice group) { current.group = group.id(); changed(); } });
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
                    if (current != null) { current.name = name.getText().trim(); if (!current.existing) current.id = "itemcontent:" + id.getText().trim(); }
                    selectionTitle.setText(name.getText().isBlank() ? "Без названия" : name.getText());
                    selectionId.setText(current != null && current.existing ? current.id : "itemcontent:" + id.getText()); list.repaint(); updateSaveState();
                }
            });
            name.getDocument().addDocumentListener(changes); id.getDocument().addDocumentListener(changes); appearance.getDocument().addDocumentListener(changes);
            ((JSpinner.DefaultEditor) maxCount.getEditor()).getTextField().getDocument().addDocumentListener(listener(this::changed));
            maxCount.addChangeListener(e -> changed()); fireproof.addActionListener(e -> changed());
            custom.addActionListener(e -> { changed(); updateImage(); }); existing.addActionListener(e -> { changed(); updateImage(); });
            imported.addActionListener(e -> { changed(); updateImage(); });
            search.getDocument().addDocumentListener(listener(() -> { if (!refreshing) guarded(() -> { commit(); refreshList(current); }); }));
            choosePng.addActionListener(e -> guarded(() -> {
                JFileChooser chooser = chooser("Выберите PNG-файл на компьютере", false); chooser.setFileFilter(new FileNameExtensionFilter("PNG-картинка", "png"));
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
                if ((selectedGroup.isEmpty() || entry.group.equals(selectedGroup)) && (query.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(query) || entry.id.toLowerCase(Locale.ROOT).contains(query))) listModel.addElement(entry);
            if (preferred != null && listModel.contains(preferred)) list.setSelectedValue(preferred, true);
            else if (!listModel.isEmpty()) list.setSelectedIndex(0);
            current = list.getSelectedValue(); refreshing = false; showCurrent();
        }
        private void duplicateItem() {
            if (current == null) return;
            commit(); ItemProject.Entry entry = current.copy();
            entry.existing = false; entry.categoryLocked = false;
            String base = "itemcontent:" + current.id.substring(current.id.indexOf(':') + 1); int suffix = 2;
            while (containsId(base + "_" + suffix)) suffix++;
            entry.id = base + "_" + suffix; entry.name = current.name + " — копия";
            if (current.existing) {
                JsonObject merged = current.referenceProperties.deepCopy(); current.properties.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue())); entry.properties = project.type(entry).validate(merged, false);
                if (entry.group.equals("itemcontent:keep")) entry.group = project.type(entry).defaultGroup();
            }
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
            project = new ItemProject(); current = null; document = null; selectedGroup = ""; rebuildGroupTabs();
            refreshing = true; listModel.clear(); search.setText(""); refreshing = false;
            addItem(); dirty = false; status.setText("Готово к созданию предметов"); status.setToolTipText(null); updateSaveState();
        }
        private void addItem() {
            commit();
            ItemProject.Entry entry = new ItemProject.Entry();
            int sequence = project.items.size() + 1;
            while (containsId("itemcontent:item_" + sequence)) sequence++;
            entry.id = "itemcontent:item_" + sequence; entry.name = "Предмет " + sequence;
            entry.importedAppearance = true;
            if (!selectedGroup.isEmpty()) entry.group = selectedGroup;
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
            current.name = name.getText().trim(); if (!current.existing) current.id = "itemcontent:" + id.getText().trim();
            current.appearance = appearance.getText().trim(); current.maxCount = ((Number) maxCount.getValue()).intValue();
            current.properties = behavior.read();
            current.importedAppearance = imported.isSelected(); current.customModel = custom.isSelected(); current.fireproof = fireproof.isSelected(); current.texture = selectedTexture;
            list.repaint();
        }
        private void showCurrent() {
            refreshing = true;
            boolean enabled = current != null;
            for (JComponent component : new JComponent[]{name, id, appearance, maxCount, custom, existing, imported, importModel, fireproof, choosePng}) component.setEnabled(enabled);
            name.setText(enabled ? current.name : ""); id.setText(enabled ? current.existing ? current.id : current.id.substring("itemcontent:".length()) : "");
            idPrefix.setText(enabled && current.existing ? "" : "itemcontent:"); id.setEnabled(enabled && !current.existing);
            appearance.setText(enabled ? current.appearance : "minecraft:iron_ingot"); maxCount.setValue(enabled ? current.maxCount : 64);
            custom.setSelected(enabled && current.customModel && !current.importedAppearance); imported.setSelected(enabled && current.importedAppearance); existing.setSelected(!enabled || !current.customModel && !current.importedAppearance);
            fireproof.setSelected(enabled && current.fireproof);
            selectedTexture = enabled ? current.texture : "";
            selectionTitle.setText(enabled ? current.name : search.getText().isBlank() ? "Создайте первый предмет" : "Ничего не найдено");
            selectionId.setText(enabled ? current.id : "Измените поиск или нажмите «Создать предмет»");
            removeButton.setEnabled(enabled); duplicateButton.setEnabled(enabled);
            if (enabled) {
                TypeDefinition definition = project.type(current); typeCategory.removeAllItems(); project.types().values().stream().map(TypeDefinition::category).distinct().forEach(typeCategory::addItem);
                typeCategory.setSelectedItem(definition.category()); itemType.removeAllItems(); project.types().values().stream().filter(type -> type.category().equals(definition.category())).forEach(itemType::addItem); itemType.setSelectedItem(definition);
                creativeGroup.removeAllItems(); if (current.existing) creativeGroup.addItem(new GroupChoice("itemcontent:keep", "Сохранить исходные вкладки"));
                groupNames().forEach((key, value) -> creativeGroup.addItem(new GroupChoice(key, value)));
                if (!groupNames().containsKey(current.group) && !current.group.equals("itemcontent:keep")) creativeGroup.addItem(new GroupChoice(current.group, current.group));
                for (int index = 0; index < creativeGroup.getItemCount(); index++) if (creativeGroup.getItemAt(index).id().equals(current.group)) creativeGroup.setSelectedIndex(index);
                typeCategory.setEnabled(!current.existing); itemType.setEnabled(!current.existing); creativeGroup.setEnabled(!current.categoryLocked);
                behavior.show(current, definition);
            }
            behavior.setVisible(enabled); typeCategory.setEnabled(enabled && !current.existing); itemType.setEnabled(enabled && !current.existing); creativeGroup.setEnabled(enabled && !current.categoryLocked);
            updateImage(); refreshing = false; updateSaveState();
        }
        private void updateImage() {
            modelSource.getParent().setVisible(current != null && imported.isSelected());
            modelSource.setText(current != null && current.importedModel != null ? current.importedModel.root : "Выберите JSON-модель или ресурс-пак");
            appearance.setEnabled(current != null && existing.isSelected() && !project.type(current).model().equals("chest") && !(current.existing && project.type(current).block())); choosePng.setEnabled(current != null);
            ((CardLayout)appearanceActions.getLayout()).show(appearanceActions,imported.isSelected() ? "model" : custom.isSelected() ? "png" : "game");
            appearanceActions.setVisible(current != null && (imported.isSelected() || custom.isSelected() || appearance.isEnabled()));
            chooseGameAppearance.setEnabled(current != null && appearance.isEnabled());
            appearance.getParent().setVisible(current != null && existing.isSelected() && appearance.isEnabled());
            preview.showImage(null, custom.isSelected() ? "Добавьте PNG-файл" : catalog == null ? "Подключите сборку" : "Нет предпросмотра");
            texturePath.setText(current != null && project.type(current).model().equals("chest") && !custom.isSelected() ? "Форма сундука · доски дуба" : custom.isSelected() ? "Картинка с вашего компьютера" : catalog == null ? "Выберите папку своей сборки" : "Модель и текстуры образца"); texturePath.setToolTipText(null);
            textureOptions.setVisible(current != null && textureSlots().length > 0);
            textureOptions.setEnabled(current != null && textureSlots().length > 0 && (custom.isSelected() || current.type.equals("itemcontent:armor")));
            textureOptions.setToolTipText(custom.isSelected() || current != null && current.type.equals("itemcontent:armor") ? null : "Отдельные грани блока, кадры лука и текстуры брони. Для граней и кадров сначала выберите свою основную PNG.");
            if (existing.isSelected() && catalog!=null) {
                catalog.items().stream().filter(item -> item.id().equals(appearance.getText()) && !item.icon().isBlank()).findFirst().ifPresent(item -> {
                    try {
                        Path root=catalogFile.getParent(), image=root.resolve(item.icon()).normalize();
                        if (image.startsWith(root)) { ItemProject.validatePng(image); preview.showImage(ImageIO.read(image.toFile()), ""); texturePath.setText("Иконка выбранного образца"); }
                    } catch (Exception ignored) { }
                });
            }
            if (imported.isSelected()) {
                preview.showImage(null, "Импортируйте модель"); texturePath.setText("JSON + текстуры ресурс-пака");
                if (current != null && current.importedModel != null) try {
                    preview.showImage(current.importedModel.texturePreview(), "Модель импортирована");
                    texturePath.setText("<html>" + current.importedModel.files.size() + " файлов · текстура модели<br>3D-вид проверяется в Minecraft</html>");
                    texturePath.setToolTipText(current.importedModel.root);
                } catch (java.io.IOException failure) { texturePath.setText("Ошибка ресурсов модели"); }
            }
            if (custom.isSelected() && selectedTexture != null && !selectedTexture.isBlank()) {
                try {
                    Path path = Path.of(selectedTexture); ItemProject.validatePng(path);
                    BufferedImage image = ImageIO.read(path.toFile()); preview.showImage(image, "");
                    texturePath.setText(image.getWidth() + " × " + image.getHeight() + " px · " + path.getFileName()); texturePath.setToolTipText(selectedTexture);
                } catch (Exception e) { preview.showImage(null, "PNG недоступна"); texturePath.setText("Выберите картинку заново"); }
            }
        }
        private void importMinecraftModel() throws java.io.IOException {
            JFileChooser chooser = chooser("Модель Minecraft Java — JSON или ресурс-пак", false);
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setFileFilter(new FileNameExtensionFilter("Модель Java (.json), ресурс-пак (.zip) или ресурсы мода (.jar)", "json", "zip", "jar"));
            chooser.setAccessory(new JLabel("<html><body style='width:220px;padding:10px'>"
                    + "<b>Что выбрать</b><br>Модель JSON вместе с PNG рядом с ней, ZIP ресурс-пака или его папку.<br><br>"
                    + "Редактор сам подключит текстуры, родительские модели и кадры. Исходные файлы останутся на месте.<br><br>"
                    + "В Blockbench нужен экспорт <b>Java Block/Item</b>. Файл .bbmodel — исходник редактора, не ресурс Minecraft.<br><br>"
                    + "Модель задаёт внешний вид. Тип и действия объекта задаются в «Свойства в игре».</body></html>"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path path = chooser.getSelectedFile().toPath();
            try (MinecraftModel.Source source = MinecraftModel.open(path)) {
                String model = MinecraftModel.selectedModel(path);
                if (model == null) {
                    java.util.List<String> available = source.models();
                    if (available.isEmpty()) throw new java.io.IOException("В выбранном ресурс-паке нет моделей Java в assets/<имя>/models/.");
                    DefaultListModel<String> options = new DefaultListModel<>(); options.addAll(available);
                    JList<String> models = new JList<>(options); models.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); models.setSelectedIndex(0);
                    JTextField filter = new JTextField(); filter.putClientProperty("JTextField.placeholderText", "Поиск модели по имени");
                    filter.getDocument().addDocumentListener(listener(() -> {
                        String query = filter.getText().trim().toLowerCase(Locale.ROOT); options.clear();
                        options.addAll(available.stream().filter(value -> value.contains(query)).toList()); if (!options.isEmpty()) models.setSelectedIndex(0);
                    }));
                    JPanel choices = new JPanel(new BorderLayout(0,10)); choices.add(new JLabel("Выберите модель для создаваемого объекта"), BorderLayout.NORTH);
                    JPanel center = new JPanel(new BorderLayout(0,8)); center.add(filter,BorderLayout.NORTH); center.add(new JScrollPane(models),BorderLayout.CENTER);
                    choices.add(center,BorderLayout.CENTER); choices.setPreferredSize(new Dimension(520,340));
                    if (JOptionPane.showConfirmDialog(this, choices, "Модели ресурс-пака", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION || models.getSelectedValue() == null) return;
                    model = models.getSelectedValue();
                }
                String selectedModel = model;
                JDialog progress = new JDialog(SwingUtilities.getWindowAncestor(this), "Импорт модели", Dialog.ModalityType.APPLICATION_MODAL);
                progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                JPanel message = new JPanel(new BorderLayout(0,12)); message.setBorder(padding(20,20,20,20));
                message.add(new JLabel("Читаем модель и связанные текстуры…"),BorderLayout.NORTH);
                JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); message.add(bar,BorderLayout.CENTER);
                progress.setContentPane(message); progress.pack(); progress.setLocationRelativeTo(this);
                new SwingWorker<MinecraftModel,Void>() {
                    protected MinecraftModel doInBackground() throws Exception { return MinecraftModel.load(source,selectedModel); }
                    protected void done() {
                        progress.dispose();
                        try {
                            MinecraftModel result=get(); current.importedModel=result; imported.setSelected(true); changed(); updateImage();
                            status.setText("Модель подключена: " + result.root + " · " + result.files.size() + " файлов. Сохраните и установите в Minecraft.");
                        } catch (Exception failure) { error(failure.getCause() instanceof Exception cause ? cause : failure); }
                    }
                }.execute();
                progress.setVisible(true);
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
        void selectItem(String identifier) { for (int i=0; i<listModel.size(); i++) if (listModel.get(i).id.equals(identifier)) { list.setSelectedIndex(i); return; } }
        void openList(Path path) throws java.io.IOException {
            ItemProject loaded = ItemProject.load(path);
            refreshing = true; project = loaded; current = null; selectedGroup = ""; search.setText(""); refreshing = false; rebuildGroupTabs();
            refreshList(project.items.get(0));
            document = path; dirty = false; status.setText(loaded.hasLegacyData() ? "Старые характеристики не используются. При сохранении будет создана резервная копия." : "Открыт список: " + path.getFileName()); status.setToolTipText(path.toString()); updateSaveState();
        }
        private void install() {
            commit(); project.validate();
            new InstallationDialog(this, project).setVisible(true);
        }
        private void export() {
            guarded(() -> {
                commit(); project.validate(); JFileChooser chooser = chooser("Экспорт комплекта для переноса — выберите новую или пустую папку", true);
                if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path target = chooser.getSelectedFile().toPath(); project.export(target);
                status.setText("Подготовлено предметов: " + project.items.size() + ". Папка: " + target);
                JOptionPane.showMessageDialog(this, "Комплект экспортирован: " + target + "\nЭто ещё не установка в игру.\n\n"
                        + "1. config/item-content → в config сервера и каждого клиента.\n"
                        + "2. resourcepacks/item-content → в resourcepacks клиента; включите в игре.\n"
                        + "3. Перезапустите сервер и клиент.\n"
                        + "4. Если есть datapacks/item-content, скопируйте в datapacks вашего мира.\n"
                        + "Предметы появятся в назначенных вкладках.\n\n"
                        + "«Сохранить» сохраняет список для дальнейшего редактирования.\n"
                        + "Item Content работает самостоятельно.", "Предметы готовы к установке", JOptionPane.INFORMATION_MESSAGE);
            });
        }
        boolean mayClose() { return confirmUnsaved(true); }
        boolean mayDiscard() { return confirmUnsaved(false); }
        private boolean confirmUnsaved(boolean closing) {
            if (!dirty) return true;
            String[] options = closing
                    ? new String[]{"Сохранить и закрыть", "Закрыть без сохранения", "Остаться в редакторе"}
                    : new String[]{"Сохранить изменения", "Продолжить без сохранения", "Отмена"};
            int answer = JOptionPane.showOptionDialog(this,
                    closing ? "Есть несохранённые изменения. Сохранить их перед закрытием редактора?" : "Сохранить изменения текущего списка перед продолжением?",
                    closing ? "Закрыть редактор" : "Несохранённые изменения",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            return answer == 1 || answer == 0 && save();
        }
        private void help() {
            JOptionPane.showMessageDialog(this, "1. «Создать предмет» добавляет предмет в список слева.\n"
                    + "2. Выберите категорию и тип: меч, еда, обычный блок и другие.\n"
                    + "3. В «Название и внешний вид» импортируйте модель Minecraft, добавьте простую текстуру или выберите образец из игры.\n"
                    + "4. В «Свойства в игре» задайте урон, прочность или другие свойства выбранного типа.\n"
                    + "5. «Сохранить» запоминает весь список для дальнейшего редактирования.\n"
                    + "6. «Установить в Minecraft» записывает мод, настройки и текстуры в выбранную папку игры.\n\n"
                    + "Список предметов — ваши сохранённые настройки. Новый мод при этом не создаётся.\n"
                    + "Один мод Item Content загружает все подготовленные вами предметы.\n\n"
                    + "Подробная инструкция: docs/editor.md.", "Как создать предмет", JOptionPane.INFORMATION_MESSAGE);
        }
        private JPanel selector(String title, JComponent input) {
            JPanel panel = panel(new BorderLayout(0, 5)); panel.add(label(title, 12, true, MUTED), BorderLayout.NORTH); panel.add(input, BorderLayout.CENTER); return panel;
        }
        private Map<String, String> groupNames() {
            Map<String, String> result = new LinkedHashMap<>(BuiltinTypes.GROUPS);
            if (catalog != null) for (var group : catalog.groups()) result.put(group.id(), group.name());
            for (var group : project.groups) result.put(group.id(), group.name()); return result;
        }
        private void rebuildGroupTabs() {
            boolean previous = refreshing; refreshing = true; groupTabs.removeAll(); tabGroups.clear();
            groupTabs.addTab("Все", new JPanel()); tabGroups.add("");
            groupNames().forEach((key, value) -> { groupTabs.addTab(value, new JPanel()); tabGroups.add(key); });
            int selected = tabGroups.indexOf(selectedGroup); groupTabs.setSelectedIndex(Math.max(0, selected)); refreshing = previous;
        }
        private void changeType(TypeDefinition type) {
            if (current == null || current.existing || current.type.equals(type.id())) return;
            commit(); JsonObject previous = current.properties; JsonObject values = new JsonObject();
            for (var property : type.properties()) if (previous.has(property.id())) try { property.validate(previous.get(property.id())); values.add(property.id(), previous.get(property.id())); } catch (IllegalArgumentException ignored) { }
            current.type = type.id(); current.properties = type.validate(values, false); current.group = type.defaultGroup();
            current.maxCount = current.properties.has("durability") && current.properties.get("durability").getAsInt() > 0 ? 1 : 64;
            if (!current.customModel && !current.importedAppearance) current.appearance = switch (type.id()) {
                case "itemcontent:food" -> "minecraft:apple"; case "itemcontent:sword" -> "minecraft:iron_sword"; case "itemcontent:bow" -> "minecraft:bow"; case "itemcontent:crossbow" -> "minecraft:crossbow";
                case "itemcontent:pickaxe" -> "minecraft:iron_pickaxe"; case "itemcontent:axe" -> "minecraft:iron_axe"; case "itemcontent:shovel" -> "minecraft:iron_shovel"; case "itemcontent:hoe" -> "minecraft:iron_hoe";
                case "itemcontent:armor" -> "minecraft:iron_chestplate"; case "itemcontent:decorative_furnace" -> "minecraft:furnace"; default -> type.block() ? "minecraft:stone" : "minecraft:iron_ingot";
            };
            selectedGroup = ""; rebuildGroupTabs(); showCurrent(); changed();
        }
        private void addGroup() {
            JTextField title = new JTextField("Новая вкладка", 20), address = new JTextField("my_items", 20);
            JPanel form = new JPanel(new GridLayout(0,1,0,8)); form.add(new JLabel("Название вкладки")); form.add(title); form.add(new JLabel("ID после itemcontent: (латиница, цифры, _)")); form.add(address);
            form.add(new JLabel("<html>Добавьте предметы в эту вкладку, установите настройки и перезапустите игру.<br>После этого она появится в творческом инвентаре Minecraft.</html>"));
            if (JOptionPane.showConfirmDialog(this, form, "Новая вкладка креатива", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            String key = "itemcontent:" + address.getText().trim(); Identifiers.check(key);
            if (groupNames().containsKey(key) || key.equals("itemcontent:keep") || title.getText().isBlank()) throw new IllegalArgumentException("Укажите уникальный ID и название вкладки");
            commit(); project.groups.add(new GameCatalog.Group(key, title.getText().trim())); rebuildGroupTabs(); showCurrent(); changed();
        }
        private void openCatalog() throws java.io.IOException {
            if (chooseCatalog()) browseCatalog();
        }
        private boolean chooseCatalog() throws java.io.IOException {
            JFileChooser chooser = chooser("Подключить сборку — выберите папку Minecraft", true);
            Path folder = GameInstallation.defaultGameDirectory();
            if (Files.isDirectory(folder)) chooser.setCurrentDirectory(folder.toFile());
            chooser.setSelectedFile(folder.toFile());
            chooser.setApproveButtonText("Подключить сборку");
            chooser.setAccessory(new JLabel("<html><div style='width:240px'>"
                    +"<b>Выберите папку нужной сборки</b><br><br>"
                    +"В ней находятся mods, config и resourcepacks. Найти её можно через папку сборки в вашем лаунчере.<br><br>"
                    +"Перед первым подключением запустите Minecraft с Item Content и войдите в мир. Мод подготовит список предметов.<br><br>"
                    +"Не нужно открывать JAR мода, искать PNG внутри Minecraft или выбирать служебные файлы."
                    +"</div></html>"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            loadCatalog(CatalogPaths.locate(chooser.getSelectedFile().toPath())); return true;
        }
        private void pickAppearance() throws java.io.IOException {
            if (current == null || catalog == null && !chooseCatalog()) return;
            commit();
            boolean block = project.type(current).block();
            CatalogDialog.appearance(this, project, catalog, catalogFile.getParent(), block, value -> {
                String previous=current.appearance;
                current.appearance=value;
                try { project.validate(); }
                catch (IllegalArgumentException failure) { current.appearance=previous; throw failure; }
                appearance.setText(value); changed(); updateImage();
            }).setVisible(true);
        }
        void loadCatalog(Path path) throws java.io.IOException {
            GameCatalog loaded = GameCatalog.load(path); commit(); project.importTypes(loaded); catalog = loaded; catalogFile = path.toAbsolutePath();
            rebuildGroupTabs(); showCurrent(); status.setText("Каталог: " + loaded.items().size() + " предметов. " + catalogFile.getFileName()); changed();
        }
        private void browseCatalog() throws java.io.IOException {
            if (catalog == null) { openCatalog(); return; }
            commit(); new CatalogDialog(this, project, catalog, catalogFile.getParent(), entry -> {
                if (!project.items.contains(entry)) project.items.add(entry); selectedGroup = ""; refreshing = true; search.setText(""); refreshing = false;
                rebuildGroupTabs(); refreshList(entry); changed();
            }).setVisible(true);
        }
        private String[] textureSlots() {
            if (current == null) return new String[0]; var type = project.type(current);
            if (type.block() && current.existing) return new String[0];
            if (type.block()) return new String[]{"top", "bottom", "side", "front", "back"};
            if (type.model().equals("bow")) return new String[]{"pulling_0", "pulling_1", "pulling_2"};
            if (type.model().equals("crossbow")) return new String[]{"pulling_0", "pulling_1", "pulling_2", "arrow", "firework"};
            return current.type.equals("itemcontent:armor") ? new String[]{"armor_layer_1", "armor_layer_2"} : new String[0];
        }
        private void textures() throws Exception {
            if (current == null) return; Map<String, String> values = new LinkedHashMap<>(current.textures);
            JPanel form = new JPanel(new GridLayout(0, 1, 0, 10));
            for (String slot : textureSlots()) {
                String title = switch (slot) { case "top" -> "Верх"; case "bottom" -> "Низ"; case "side" -> "Боковые стороны"; case "front" -> "Передняя сторона"; case "back" -> "Задняя сторона"; case "armor_layer_1" -> "Броня: слой 1"; case "armor_layer_2" -> "Броня: слой 2"; case "arrow" -> "Заряжен стрелой"; case "firework" -> "Заряжен фейерверком"; default -> "Натяжение: " + slot.substring(slot.length() - 1); };
                JPanel row = new JPanel(new BorderLayout(8, 0)); JLabel path = new JLabel(values.containsKey(slot) ? Path.of(values.get(slot)).getFileName().toString() : "Основная текстура");
                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); JButton choose = new JButton("PNG…"), clear = new JButton("Сбросить"); buttons.add(choose); buttons.add(clear);
                row.add(new JLabel(title), BorderLayout.WEST); row.add(path, BorderLayout.CENTER); row.add(buttons, BorderLayout.EAST); form.add(row);
                choose.addActionListener(e -> guarded(() -> { JFileChooser file = chooser("Выберите PNG: " + title, false); file.setFileFilter(new FileNameExtensionFilter("PNG", "png")); if (file.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { ItemProject.validatePng(file.getSelectedFile().toPath()); values.put(slot, file.getSelectedFile().getAbsolutePath()); path.setText(file.getSelectedFile().getName()); } }));
                clear.addActionListener(e -> { values.remove(slot); path.setText("Основная текстура"); });
            }
            if (JOptionPane.showConfirmDialog(this, form, "Другие текстуры", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) { current.textures = values; changed(); }
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
