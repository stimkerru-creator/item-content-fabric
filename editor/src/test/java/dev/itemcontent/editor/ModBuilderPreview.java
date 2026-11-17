package dev.itemcontent.editor;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;

public final class ModBuilderPreview {
    public static void main(String[] args) throws Exception {
        EditorTheme.setup(); var project = ItemProject.load(Path.of(args[0]));
        SwingUtilities.invokeAndWait(() -> {
            var dialog = new ModBuildDialog(new JPanel(), project, () -> {}); dialog.setModalityType(java.awt.Dialog.ModalityType.MODELESS); dialog.setVisible(true);
            SwingUtilities.invokeLater(() -> {
                try {
                    var content = dialog.getContentPane(); var image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics(); content.printAll(graphics); graphics.dispose(); ImageIO.write(image, "png", Path.of(args[1]).toFile());
                } catch (Exception failure) { throw new IllegalStateException(failure); } finally { dialog.dispose(); }
            });
        });
    }
}
