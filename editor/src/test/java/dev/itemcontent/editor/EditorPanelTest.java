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
    @Test void addingFieldAfterDeletingMiddleRowKeepsIdsUnique() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            var table = (JTable) find(panel, "parameters");
            var add = (JButton) find(panel, "addField");
            add.doClick(); add.doClick(); add.doClick();
            table.setRowSelectionInterval(1, 1);
            ((JButton) find(panel, "removeField")).doClick(); add.doClick();
            var ids = new java.util.HashSet<>();
            for (int row = 0; row < table.getRowCount(); row++) assertTrue(ids.add(table.getValueAt(row, 0)));
        });
    }

    @Test void switchingCommitsAnActiveParameterCellToTheCorrectItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            var list = (JList<?>) find(panel, "items");
            var table = (JTable) find(panel, "parameters");
            ((JButton) find(panel, "addField")).doClick();
            assertTrue(table.editCellAt(0, 1));
            ((JTextField) table.getEditorComponent()).setText("12,34");
            ((JButton) find(panel, "addItem")).doClick();
            assertEquals(0, table.getRowCount());
            list.setSelectedIndex(0);
            assertEquals("12,34", table.getValueAt(0, 1));
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
