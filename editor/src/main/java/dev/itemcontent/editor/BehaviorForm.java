package dev.itemcontent.editor;

import com.google.gson.*;
import dev.itemcontent.definition.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;

/** Controls are generated only from a validated behavior schema, never arbitrary object fields. */
final class BehaviorForm extends JPanel {
    private final Map<PropertyDefinition, JComponent> inputs = new LinkedHashMap<>();
    private final Runnable changed;
    private boolean loading;
    private final java.util.List<JPanel> rows = new ArrayList<>();
    private JLabel description;
    private JPanel spacer;
    private int columns = -1;
    BehaviorForm(Runnable changed) {
        super(new GridBagLayout()); this.changed = changed; setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        addComponentListener(new java.awt.event.ComponentAdapter() { @Override public void componentResized(java.awt.event.ComponentEvent event) { reflow(); } });
    }
    void show(ItemProject.Entry item, TypeDefinition type) {
        loading = true; removeAll(); inputs.clear(); rows.clear(); columns=-1;
        description = new JLabel("<html>" + escape(type.help()) + (item.existing ? "<br>Затемнённые свойства показаны для справки; менять их у существующего предмета этот тип не умеет." : "") + "</html>");
        add(description);
        for (var property : type.properties()) {
            JsonElement value = item.properties.has(property.id()) ? item.properties.get(property.id()) : item.referenceProperties.has(property.id()) ? item.referenceProperties.get(property.id()) : property.initial();
            JComponent input;
            if (property.kind().equals("boolean")) { var check = new JCheckBox(property.label(), value.getAsBoolean()); check.addActionListener(e -> changed()); input = check; }
            else if (property.kind().equals("choice")) {
                var combo = new JComboBox<>(property.choices().toArray(String[]::new)); combo.setRenderer(new DefaultListCellRenderer() {
                    @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                        super.getListCellRendererComponent(list,value,index,selected,focus); setText(item.type.startsWith("itemcontent:") ? choiceLabel(Objects.toString(value,"")) : Objects.toString(value,"")); return this;
                    }
                }); combo.setSelectedItem(value.getAsString()); combo.addActionListener(e -> changed()); input = combo;
            } else {
                var text = new JTextField(value.getAsString(), 16);
                text.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e) { changed(); } public void removeUpdate(DocumentEvent e) { changed(); } public void changedUpdate(DocumentEvent e) { changed(); } });
                input = text;
            }
            input.setName("behavior." + property.id()); input.setEnabled(!item.existing || property.editExisting()); input.setToolTipText(property.help().isBlank() ? null : property.help());
            JPanel line = new JPanel(new BorderLayout(12, 4));
            if (!property.kind().equals("boolean")) line.add(new JLabel("<html>" + escape(property.label()) + "</html>"), BorderLayout.NORTH);
            line.add(input, BorderLayout.CENTER);
            if (!property.help().isBlank()) { var help = new JLabel("<html>" + escape(property.help()) + "</html>"); help.setForeground(EditorTheme.MUTED); help.setFont(help.getFont().deriveFont(11f)); line.add(help, BorderLayout.SOUTH); }
            line.setMinimumSize(new Dimension(120,0)); rows.add(line); add(line); inputs.put(property, input);
        }
        spacer=new JPanel(); add(spacer); reflow(); loading = false; revalidate(); repaint();
    }
    private void reflow() {
        int next=getWidth()>=720 ? 2 : 1;
        if(description==null || spacer==null || columns==next)return;
        columns=next; var layout=(GridBagLayout)getLayout(); var c=new GridBagConstraints();
        c.gridx=0; c.gridy=0; c.gridwidth=columns; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; c.anchor=GridBagConstraints.NORTHWEST; c.insets=new Insets(0,0,14,0); layout.setConstraints(description,c);
        c.gridwidth=1;
        for(int index=0;index<rows.size();index++) {
            c.gridx=index%columns; c.gridy=1+index/columns; c.insets=new Insets(0,0,12,columns==2 && c.gridx==0 ? 24 : 0); layout.setConstraints(rows.get(index),c);
        }
        c.gridx=0; c.gridy=1+(rows.size()+columns-1)/columns; c.gridwidth=columns; c.weighty=1; c.fill=GridBagConstraints.BOTH; c.insets=new Insets(0,0,0,0); layout.setConstraints(spacer,c);
        revalidate();
    }
    JsonObject read() {
        JsonObject result = new JsonObject();
        for (var entry : inputs.entrySet()) {
            var p = entry.getKey(); var component = entry.getValue(); if (!component.isEnabled()) continue;
            JsonElement value;
            if (component instanceof JCheckBox check) value = new JsonPrimitive(check.isSelected());
            else if (component instanceof JComboBox<?> combo) value = new JsonPrimitive(Objects.toString(combo.getSelectedItem(), ""));
            else {
                String text = ((JTextField) component).getText().trim();
                try { value = p.kind().equals("number") || p.kind().equals("integer") ? new JsonPrimitive(new java.math.BigDecimal(text.replace(',', '.'))) : new JsonPrimitive(text); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Введите число: " + p.label()); }
            }
            p.validate(value); result.add(p.id(), value);
        }
        return result;
    }
    static String choiceLabel(String value) {
        return switch(value) { case "helmet" -> "Шлем"; case "chestplate" -> "Нагрудник"; case "leggings" -> "Поножи"; case "boots" -> "Ботинки";
            case "stone" -> "Камень"; case "wood" -> "Дерево"; case "metal" -> "Металл"; case "glass" -> "Стекло"; case "gravel" -> "Гравий"; case "wool" -> "Шерсть";
            case "none" -> "Без инструмента"; case "pickaxe" -> "Кирка"; case "axe" -> "Топор"; case "shovel" -> "Лопата"; case "hoe" -> "Мотыга";
            case "leather" -> "Кожа"; case "chainmail" -> "Кольчуга"; case "iron" -> "Железо"; case "gold" -> "Золото"; case "diamond" -> "Алмаз"; case "netherite" -> "Незерит"; case "custom" -> "Своя текстура"; default -> value; };
    }
    private void changed() { if (!loading) changed.run(); }
    static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
