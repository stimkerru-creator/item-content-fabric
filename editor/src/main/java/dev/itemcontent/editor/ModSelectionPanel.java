package dev.itemcontent.editor;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Selection belongs to this build, never to the full game catalog. */
final class ModSelectionPanel extends JPanel {
    final JTextField name = new JTextField(24), id = new JTextField(24), version = new JTextField(12);
    final JTextField requiredMods = new JTextField(24);
    private final List<ItemProject.Entry> items;
    private final Set<String> selected = new LinkedHashSet<>();
    private final JLabel count = new JLabel();
    private final AbstractTableModel model;
    ModSelectionPanel(ItemProject project) {
        super(new BorderLayout(0, 8)); setBorder(BorderFactory.createEmptyBorder(12, 18, 8, 18));
        items = List.copyOf(project.items); items.forEach(item -> { if (project.modItems == null || project.modItems.contains(item.id)) selected.add(item.id); });
        name.setName("modName"); name.setText(project.modName); id.setName("modId"); id.setText(project.modId);
        version.setName("modVersion"); version.setText(project.modVersion);
        requiredMods.setName("requiredMods"); requiredMods.setText(String.join(", ", project.requiredMods));
        requiredMods.setToolTipText("Оставьте пустым для собственных предметов и Minecraft. Если ссылаетесь на предметы другого мода, укажите его ID из fabric.mod.json, например industrial_mod.");
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(new JLabel("Minecraft 1.20.1 / Fabric · Только выбранные предметы"), BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridBagLayout());
        field(fields, 0, "Название мода", name, 0.45); field(fields, 1, "ID мода", id, 0.4); field(fields, 2, "Версия", version, 0.15);
        id.setToolTipText("Своё постоянное имя: например bronze_parts. В игре предмет получит ID bronze_parts:имя_предмета. Для обновлений не меняйте его.");
        JPanel metadata = new JPanel(new BorderLayout(0, 8)); metadata.add(fields, BorderLayout.NORTH);
        JCheckBox dependencies = new JCheckBox("Мод использует предметы других модов"); dependencies.setSelected(!project.requiredMods.isEmpty());
        JPanel optional = new JPanel(new BorderLayout(0, 4)); optional.add(dependencies, BorderLayout.NORTH); optional.add(requiredMods, BorderLayout.CENTER);
        requiredMods.setVisible(dependencies.isSelected()); requiredMods.putClientProperty("JTextField.placeholderText", "ID обязательных модов через запятую");
        dependencies.addActionListener(e -> { requiredMods.setVisible(dependencies.isSelected()); if (!dependencies.isSelected()) requiredMods.setText(""); revalidate(); });
        metadata.add(optional, BorderLayout.CENTER); top.add(metadata, BorderLayout.CENTER);
        top.add(new JLabel("ID bronze_parts → предмет bronze_parts:plate"), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        model = new AbstractTableModel() {
            public int getRowCount() { return items.size(); }
            public int getColumnCount() { return 4; }
            public String getColumnName(int column) { return List.of("Включить", "Предмет", "Тип", "ID в готовом моде").get(column); }
            public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }
            public boolean isCellEditable(int row, int column) { return column == 0; }
            public Object getValueAt(int row, int column) {
                var item = items.get(row);
                return switch (column) {
                    case 0 -> selected.contains(item.id); case 1 -> item.name;
                    case 2 -> project.type(item).label();
                    default -> item.existing ? item.id : id.getText().trim() + item.id.substring(item.id.indexOf(':'));
                };
            }
            public void setValueAt(Object value, int row, int column) {
                if (Boolean.TRUE.equals(value)) selected.add(items.get(row).id); else selected.remove(items.get(row).id);
                fireTableRowsUpdated(row, row); updateCount();
            }
        };
        JTable table = new JTable(model); table.setName("modItems"); table.setRowHeight(32); table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(90); table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(240);
        var sorter = new TableRowSorter<>(model); table.setRowSorter(sorter);
        JPanel center = new JPanel(new BorderLayout(0, 8)); JTextField search = new JTextField(); search.setName("modSearch");
        search.putClientProperty("JTextField.placeholderText", "Поиск предмета по названию или ID");
        search.getDocument().addDocumentListener(changes(() -> sorter.setRowFilter(search.getText().isBlank() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(search.getText()), 1, 3))));
        center.add(search, BorderLayout.NORTH); center.add(new JScrollPane(table), BorderLayout.CENTER); add(center, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 10)); JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton all = new JButton("Выбрать все"), none = new JButton("Снять все"); all.setName("selectAllModItems"); none.setName("clearModItems");
        all.addActionListener(e -> { items.forEach(item -> selected.add(item.id)); model.fireTableDataChanged(); updateCount(); });
        none.addActionListener(e -> { selected.clear(); model.fireTableDataChanged(); updateCount(); });
        buttons.add(all); buttons.add(none); buttons.add(count); bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(new JLabel("Игрокам нужен JAR + Fabric API. Редактор не нужен."), BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH); id.getDocument().addDocumentListener(changes(() -> model.fireTableDataChanged())); updateCount();
        setPreferredSize(new Dimension(800, 560));
    }
    Set<String> selection() { return Set.copyOf(selected); }
    StandaloneMod.Settings settings() {
        Set<String> dependencies = new LinkedHashSet<>();
        for (String value : requiredMods.getText().split(",")) if (!value.isBlank()) dependencies.add(value.trim());
        return new StandaloneMod.Settings(id.getText().trim(), name.getText().trim(), version.getText().trim(), dependencies);
    }
    private void updateCount() { count.setText("В мод войдёт: " + selected.size() + " из " + items.size()); }
    private static void field(JPanel panel, int column, String label, JTextField input, double weight) {
        var c = new GridBagConstraints(); c.gridx = column; c.gridy = 0; c.weightx = weight; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 5, column == 2 ? 0 : 12); panel.add(new JLabel(label), c);
        c.gridy = 1; panel.add(input, c);
    }
    private static DocumentListener changes(Runnable action) { return new DocumentListener() {
        public void insertUpdate(DocumentEvent e) { action.run(); } public void removeUpdate(DocumentEvent e) { action.run(); } public void changedUpdate(DocumentEvent e) { action.run(); }
    }; }
}
