package dev.itemcontent.editor;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorPanelTest {
    @Test void addingAndSwitchingItemsPreservesIndependentValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            var items = (JList<?>) find(panel, "items");
            var name = (JTextField) find(panel, "itemName");
            name.setText("Пластина"); ((JButton) find(panel, "addItem")).doClick();
            assertEquals(2, items.getModel().getSize()); name.setText("Шарнир");
            items.setSelectedIndex(0); assertEquals("Пластина", name.getText());
            items.setSelectedIndex(1); assertEquals("Шарнир", name.getText());
            assertNull(find(panel, "formula"));
        });
    }
    private Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component nested = find(container, name); if (nested != null) return nested; }
        }
        return null;
    }
}
