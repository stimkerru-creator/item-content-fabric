package dev.itemcontent.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorPanelTest {
    @TempDir Path temp;
    @BeforeAll static void theme() throws Exception { SwingUtilities.invokeAndWait(EditorTheme::setup); }

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
    @Test void editorContainsOnlyItemControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            for (String absent : new String[]{"parameters", "addField", "removeField", "formula", "itemTabs"})
                assertNull(find(panel, absent), absent);
            assertNotNull(find(panel, "itemName"));
            assertNotNull(find(panel, "choosePng"));
        });
    }

    @Test void searchCommitsEditsAndRestoresTheCorrectItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            var name = (JTextField) find(panel, "itemName");
            var search = (JTextField) find(panel, "searchItems");
            var list = (JList<?>) find(panel, "items");
            name.setText("Пластина");
            ((JButton) find(panel, "addItem")).doClick(); name.setText("Шарнир");
            search.setText("пласт"); assertEquals(1, list.getModel().getSize());
            assertEquals("Пластина", name.getText());
            search.setText("несуществующий"); assertEquals(0, list.getModel().getSize());
            assertFalse(name.isEnabled());
            search.setText(""); assertEquals(2, list.getModel().getSize());
            list.setSelectedIndex(1); assertEquals("Шарнир", name.getText());
        });
    }

    @Test void duplicateHasIndependentPropertiesAndANewId() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            var name = (JTextField) find(panel, "itemName");
            var id = (JTextField) find(panel, "itemId");
            var list = (JList<?>) find(panel, "items");
            name.setText("Пластина"); String originalId = id.getText();
            ((JTextField) find(panel, "appearance")).setText("minecraft:gold_ingot");
            ((JButton) find(panel, "duplicateItem")).doClick();
            assertEquals(2, list.getModel().getSize()); assertNotEquals(originalId, id.getText());
            var appearance = (JTextField) find(panel, "appearance");
            assertEquals("minecraft:gold_ingot", appearance.getText()); appearance.setText("minecraft:copper_ingot");
            list.setSelectedIndex(0); assertEquals("Пластина", name.getText()); assertEquals("minecraft:gold_ingot", appearance.getText());
            list.setSelectedIndex(1); assertEquals("minecraft:copper_ingot", appearance.getText());
        });
    }

    @Test void saveShortcutUpdatesTheOpenedListWithoutDroppingOtherItems() throws Exception {
        var project = new ItemProject(); project.items.add(new ItemProject.Entry());
        var second = new ItemProject.Entry(); second.id = "itemcontent:second"; second.name = "Шарнир"; project.items.add(second);
        Path file = temp.resolve("catalog.icproject"); project.save(file);
        SwingUtilities.invokeAndWait(() -> {
            try {
                var panel = new ItemContentEditor.EditorPanel(); panel.openList(file);
                ((JTextField) find(panel, "itemName")).setText("Стальная пластина");
                ((JTextField) find(panel, "appearance")).setText("minecraft:gold_ingot");
                panel.getActionMap().get("saveList").actionPerformed(new java.awt.event.ActionEvent(panel, 0, "save"));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        var loaded = ItemProject.load(file);
        assertEquals(2, loaded.items.size()); assertEquals("Стальная пластина", loaded.items.get(0).name);
        assertEquals("minecraft:gold_ingot", loaded.items.get(0).appearance); assertEquals("Шарнир", loaded.items.get(1).name);
    }

    @Test void initialWindowFitsTheAvailableSmallDesktop() {
        Dimension size = ItemContentEditor.windowSize(new Rectangle(0, 0, 1366, 728));
        assertTrue(size.width < 1366); assertTrue(size.height < 728);
        assertTrue(size.width >= 980); assertTrue(size.height >= 580);
    }

    @Test void mainActionsStayInsideTheWindowAtCompactSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Dimension size : new Dimension[]{new Dimension(1264, 641), new Dimension(964, 581)}) {
                var panel = new ItemContentEditor.EditorPanel(); panel.setSize(size); layout(panel);
                for (String action : new String[]{"addItem", "saveProject", "export", "fileMenu"}) {
                    Component component = find(panel, action);
                    Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel);
                    assertTrue(new Rectangle(size).contains(bounds), action + " clipped at " + size);
                    assertTrue(bounds.width > 40 && bounds.height > 20, action);
                }
            }
        });
    }
    @Test void openingASavedPngItemShowsItsImageWithCorrectProportions() throws Exception {
        var image = new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); graphics.setColor(Color.RED); graphics.fillRect(0, 0, 64, 16);
        graphics.setColor(Color.BLUE); graphics.fillRect(0, 16, 64, 16); graphics.dispose();
        Path texture = temp.resolve("texture.png"); javax.imageio.ImageIO.write(image, "png", texture.toFile());
        var project = new ItemProject(); var item = new ItemProject.Entry(); item.customModel = true; item.texture = texture.toString(); project.items.add(item);
        Path file = temp.resolve("png.icproject"); project.save(file);
        SwingUtilities.invokeAndWait(() -> {
            try {
                var panel = new ItemContentEditor.EditorPanel(); panel.openList(file);
                var preview = (JComponent) find(panel, "itemPreview"); preview.setSize(230, 230);
                var rendered = new java.awt.image.BufferedImage(230, 230, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var output = rendered.createGraphics(); preview.paint(output); output.dispose();
                assertEquals(Color.RED.getRGB(), rendered.getRGB(115, 90));
                assertEquals(Color.BLUE.getRGB(), rendered.getRGB(115, 140));
                assertNotEquals(Color.RED.getRGB(), rendered.getRGB(115, 30));
                assertFalse(((JTextField) find(panel, "appearance")).isEnabled());
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    @Test void textFieldsHaveEnoughInteriorHeightForTheirFont() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ItemContentEditor.EditorPanel();
            panel.setSize(1264, 641); layout(panel);
            assertTextHeight(panel);
        });
    }

    private void assertTextHeight(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTextField field) {
                Insets insets = field.getInsets();
                int interior = field.getHeight() - insets.top - insets.bottom;
                assertTrue(interior >= field.getFontMetrics(field.getFont()).getHeight(),
                        field.getName() + ": interior=" + interior + ", font=" + field.getFontMetrics(field.getFont()).getHeight());
            }
            if (child instanceof Container container) assertTextHeight(container);
        }
    }

    private void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }

    private Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component nested = find(container, name); if (nested != null) return nested; }
        }
        return null;
    }
}
