package dev.itemcontent.editor;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/** Shared desktop styling; never included in the Fabric runtime. */
final class EditorTheme {
    static final Color BACKGROUND = new Color(0xf3f5f9);
    static final Color INK = new Color(0x1d293b);
    static final Color MUTED = new Color(0x69778c);
    static final Color LINE = new Color(0xe3e8f0);
    static final Color ACCENT = new Color(0x3066d6);
    private EditorTheme() {}

    static void setup() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 14f));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.accentColor", ACCENT);
        UIManager.put("Button.margin", new Insets(9, 16, 9, 16));
        UIManager.put("TextField.margin", new Insets(8, 10, 8, 10));
        UIManager.put("TabbedPane.tabHeight", 42);
        UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("Table.rowHeight", 36);
        UIManager.put("TableHeader.height", 36);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", INK);
    }
    static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(UIManager.getFont("Label.font").deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size)); label.setForeground(color); return label;
    }
    static java.awt.image.BufferedImage windowIcon() {
        var image = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(ACCENT); g.fillRoundRect(0, 0, 64, 64, 16, 16);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 28));
            FontMetrics metrics = g.getFontMetrics(); g.drawString("IC", (64 - metrics.stringWidth("IC")) / 2, (64 - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally { g.dispose(); }
        return image;
    }
    static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }
    static Border padding(int top, int left, int bottom, int right) { return BorderFactory.createEmptyBorder(top, left, bottom, right); }
    static void primary(JButton button) {
        button.setBackground(ACCENT); button.setForeground(Color.WHITE); button.putClientProperty("JButton.buttonType", "roundRect");
    }
    static void quiet(JButton button) { button.putClientProperty("JButton.buttonType", "borderless"); button.setForeground(MUTED); }

    static final class ImagePreview extends JComponent {
        private java.awt.image.BufferedImage image;
        private String message = "Выберите PNG";
        ImagePreview() { setPreferredSize(new Dimension(230, 230)); setMinimumSize(new Dimension(160, 160)); }
        void showImage(java.awt.image.BufferedImage value, String emptyMessage) { image = value; message = emptyMessage; repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                for (int y = 0; y < getHeight(); y += 16)
                    for (int x = 0; x < getWidth(); x += 16) {
                        g.setColor(((x / 16 + y / 16) & 1) == 0 ? new Color(0xf0f3f8) : new Color(0xe8edf4)); g.fillRect(x, y, 16, 16);
                    }
                if (image != null) {
                    double scale = Math.min((getWidth() - 40.0) / image.getWidth(), (getHeight() - 40.0) / image.getHeight());
                    int width = Math.max(1, (int) (image.getWidth() * scale)), height = Math.max(1, (int) (image.getHeight() * scale));
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
                } else {
                    g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.PLAIN, 13)); FontMetrics metrics = g.getFontMetrics();
                    g.drawString(message, Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2), getHeight() / 2);
                }
            } finally { g.dispose(); }
        }
    }
}
