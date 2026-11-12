package dev.itemcontent.editor;

import dev.itemcontent.definition.*;
import com.google.gson.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Browse actual registry categories; copy supported behavior or override only declared editable fields. */
final class CatalogDialog extends JDialog {
    private final GameCatalog catalog;
    private final Path root;
    private final Map<String, Icon> icons = new LinkedHashMap<>(128,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Icon> entry) { return size()>512; }
    };
    private final ItemProject project;
    private final Consumer<ItemProject.Entry> accept;
    private final Consumer<String> appearanceAccept;
    private final boolean blocksOnly;
    private final DefaultListModel<GameCatalog.Entry> model = new DefaultListModel<>();
    private final JList<GameCatalog.Entry> list = new JList<>(model);
    private final JTextField search = new JTextField(24);
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final java.util.List<String> groupIds = new ArrayList<>();
    private final JLabel title = new JLabel(), info = new JLabel();
    private final DefaultTableModel properties = new DefaultTableModel(new String[]{"Свойство", "Значение", "Изменение исходника"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    CatalogDialog(Component owner, ItemProject project, GameCatalog catalog, Path root, Consumer<ItemProject.Entry> accept) {
        this(owner, project, catalog, root, accept, null, false);
    }
    static CatalogDialog appearance(Component owner, ItemProject project, GameCatalog catalog, Path root,
                                    boolean blocksOnly, Consumer<String> accept) {
        return new CatalogDialog(owner, project, catalog, root, null, accept, blocksOnly);
    }
    private CatalogDialog(Component owner, ItemProject project, GameCatalog catalog, Path root,
                          Consumer<ItemProject.Entry> accept, Consumer<String> appearanceAccept, boolean blocksOnly) {
        super(SwingUtilities.getWindowAncestor(owner), appearanceAccept == null ? "Каталог предметов игры" : "Выбрать готовый внешний вид", ModalityType.APPLICATION_MODAL);
        this.project = project; this.catalog = catalog; this.root = root.toAbsolutePath().normalize(); this.accept = accept;
        this.appearanceAccept = appearanceAccept; this.blocksOnly = blocksOnly;
        JPanel content = new JPanel(new BorderLayout(14, 14)); content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18)); setContentPane(content);
        JPanel top = new JPanel(new BorderLayout(0, 8)); search.putClientProperty("JTextField.placeholderText", "Поиск по названию, ID или моду"); top.add(search, BorderLayout.NORTH);
        groupIds.add(""); tabs.addTab("Все", new JPanel());
        for (var group : catalog.groups()) { groupIds.add(group.id()); tabs.addTab(group.name(), new JPanel()); }
        tabs.setPreferredSize(new Dimension(900, 42)); top.add(tabs, BorderLayout.SOUTH); content.add(top, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(42);
        list.setCellRenderer(new DefaultListCellRenderer() { public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus); var item = (GameCatalog.Entry) value;
            setText(item.name() + "  ·  " + item.id()); setIcon(icon(item)); setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8)); return this;
        }});
        if (!GraphicsEnvironment.isHeadless()) list.setDragEnabled(true); list.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override protected Transferable createTransferable(JComponent c) { var item = list.getSelectedValue(); return item == null ? null : new StringSelection(item.id()); }
        });
        tabs.setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) { return appearanceAccept == null && support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor); }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Point point = support.getDropLocation().getDropPoint(); int index = tabs.indexAtLocation(point.x, point.y);
                    if (index <= 0) return false;
                    String id = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    var source = catalog.items().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
                    if (source.variants()) throw new IllegalArgumentException("У предмета есть NBT-варианты. Его исходные вкладки сохраняются.");
                    var entry = project.items.stream().filter(item -> item.id.equals(id)).findFirst().orElse(null);
                    if (entry == null) entry = fromCatalog(project, source, true);
                    entry.group = groupIds.get(index); accept.accept(entry); dispose(); return true;
                } catch (Exception failure) { JOptionPane.showMessageDialog(CatalogDialog.this, failure.getMessage(), "Перемещение", JOptionPane.ERROR_MESSAGE); return false; }
            }
        });
        JPanel details = new JPanel(new BorderLayout(8, 10)); JPanel heading = new JPanel(new BorderLayout(0,6)); heading.add(title, BorderLayout.NORTH); heading.add(info, BorderLayout.CENTER); details.add(heading, BorderLayout.NORTH);
        JTable table = new JTable(properties); table.setRowHeight(30); table.getColumnModel().getColumn(0).setPreferredWidth(270); table.getColumnModel().getColumn(1).setPreferredWidth(110); table.getColumnModel().getColumn(2).setPreferredWidth(230); if (appearanceAccept == null) details.add(new JScrollPane(table), BorderLayout.CENTER);
        details.add(new JLabel(appearanceAccept != null ? "<html>Выбирается только внешний вид.<br>Название, тип и игровые свойства вашего предмета сохранятся.</html>" : "<html>Показаны свойства поддерживаемого типа. Чужие Java-поля не извлекаются автоматически.<br>Копия получает новый ID; специфическая механика, NBT-варианты и эффекты требуют поддержки типа.</html>"), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), details); split.setResizeWeight(0.5); content.add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); JButton copy = new JButton(appearanceAccept == null ? "Создать новый на основе…" : "Использовать этот внешний вид"), edit = new JButton("Настроить существующий"), close = new JButton("Закрыть");
        actions.add(copy); if (appearanceAccept == null) actions.add(edit); actions.add(close); content.add(actions, BorderLayout.SOUTH);
        copy.addActionListener(e -> add(false)); edit.addActionListener(e -> add(true)); close.addActionListener(e -> dispose());
        search.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e) { filter(); } public void removeUpdate(DocumentEvent e) { filter(); } public void changedUpdate(DocumentEvent e) { filter(); } });
        tabs.addChangeListener(e -> filter()); list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) selected(); });
        filter(); setDefaultCloseOperation(DISPOSE_ON_CLOSE); Rectangle available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1180, available.width - 30), Math.min(700, available.height - 30)); setLocationRelativeTo(owner);
    }
    private Icon icon(GameCatalog.Entry entry) {
        if (entry.icon().isBlank()) return null;
        if (icons.containsKey(entry.id())) return icons.get(entry.id());
        Icon result = null;
        try {
            Path file = root.resolve(entry.icon()).normalize();
            if (file.startsWith(root) && Files.isRegularFile(file) && Files.size(file) <= 1024 * 1024) {
                ItemProject.validatePng(file);
                var source = javax.imageio.ImageIO.read(file.toFile());
                double scale = Math.min(30.0 / source.getWidth(), 30.0 / source.getHeight());
                result = new ImageIcon(source.getScaledInstance(Math.max(1, (int)(source.getWidth()*scale)), Math.max(1, (int)(source.getHeight()*scale)), Image.SCALE_FAST));
            }
        } catch (Exception ignored) { }
        icons.put(entry.id(), result); return result;
    }
    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT), group = groupIds.get(Math.max(0, tabs.getSelectedIndex())); model.clear();
        for (var item : catalog.items()) if ((!blocksOnly || project.types().containsKey(item.type()) && project.types().get(item.type()).block()) && (group.isEmpty() || item.groups().contains(group)) && (item.name().toLowerCase(Locale.ROOT).contains(query) || item.id().contains(query))) model.addElement(item);
        if (!model.isEmpty()) list.setSelectedIndex(0); else selected();
    }
    private void selected() {
        var item = list.getSelectedValue(); properties.setRowCount(0);
        if (item == null) { title.setText("Ничего не найдено"); info.setText(""); return; }
        title.setText(item.name()); info.setText("<html>" + BehaviorForm.escape(item.id()) + "<br>" + (appearanceAccept == null ? BehaviorForm.escape(item.javaClass()) : "Меняет только внешний вид") + "</html>");
        properties.addRow(new Object[]{"Размер стака", item.maxCount(), "Да"}); properties.addRow(new Object[]{"Прочность", item.durability(), "Да"});
        properties.addRow(new Object[]{"Не сгорает", item.fireproof() ? "Да" : "Нет", "Да"});
        var definition = project.types().get(item.type());
        if (definition != null) for (var p : definition.properties()) if (!p.id().equals("durability") && item.properties().has(p.id())) properties.addRow(new Object[]{p.label(), p.kind().equals("boolean") ? item.properties().get(p.id()).getAsBoolean() ? "Да" : "Нет" : p.kind().equals("choice") && item.type().startsWith("itemcontent:") ? BehaviorForm.choiceLabel(item.properties().get(p.id()).getAsString()) : item.properties().get(p.id()).getAsString(), p.editExisting() ? "Да" : "Только у новой копии"});
        if (item.variants()) properties.addRow(new Object[]{"Есть NBT-варианты", "Да", "Категории исходника сохраняются"});
    }
    private void add(boolean existing) {
        var source = list.getSelectedValue(); if (source == null) return;
        try {
            if (appearanceAccept != null) { appearanceAccept.accept(source.id()); dispose(); return; }
            if (existing && project.items.stream().anyMatch(e -> e.id.equals(source.id()))) throw new IllegalArgumentException("Этот предмет уже есть в вашем списке. Отредактируйте его карточку.");
            var entry = fromCatalog(project, source, existing);
            if (!source.icon().isBlank()) {
                Path icon = root.resolve(source.icon()).normalize();
                if (icon.startsWith(root) && Files.isRegularFile(icon)) entry.texture = icon.toString();
            }
            accept.accept(entry); dispose();
        } catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Не удалось добавить", JOptionPane.ERROR_MESSAGE); }
    }
    static ItemProject.Entry fromCatalog(ItemProject project, GameCatalog.Entry source, boolean existing) {
        if (source.maxCount() > 99) throw new IllegalArgumentException("Просмотр доступен, но этот предмет имеет стак " + source.maxCount() + ". Редактор пока настраивает стаки от 1 до 99.");
        var definition = project.types().get(source.type());
        if (definition == null) throw new IllegalArgumentException("Нет схемы типа: " + source.type());
        var entry = new ItemProject.Entry(); entry.existing = existing; entry.categoryLocked = existing && source.variants(); entry.type = source.type(); entry.name = source.name(); entry.appearance = source.id();
        entry.group = existing ? "itemcontent:keep" : source.groups().isEmpty() ? definition.defaultGroup() : source.groups().get(0);
        entry.maxCount = source.maxCount(); entry.fireproof = source.fireproof();
        for (var p : definition.properties()) if (source.properties().has(p.id())) {
            JsonElement value = source.properties().get(p.id()); p.validate(value); entry.referenceProperties.add(p.id(), value.deepCopy());
            if (!existing || p.editExisting()) entry.properties.add(p.id(), value.deepCopy());
        }
        if (entry.type.equals("itemcontent:block") && existing) entry.properties = new JsonObject();
        if (existing) entry.id = source.id();
        else {
            String base = "itemcontent:" + source.id().substring(source.id().indexOf(':') + 1) + "_copy"; int suffix = 1;
            String candidate = base;
            while (contains(project, candidate)) candidate = base + "_" + (++suffix);
            entry.id = candidate;
        }
        return entry;
    }
    private static boolean contains(ItemProject project, String id) { return project.items.stream().anyMatch(e -> e.id.equals(id)); }
}
