package dev.itemcontent.editor;
import javax.swing.*;
import java.nio.file.*;
/** Optional real-window probe; run with tools/check-editor-close.ps1 outside headless JUnit. */
public class CloseDialogNativeProbe {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            try {
                EditorTheme.setup(); var panel=new ItemContentEditor.EditorPanel();
                var frame=new JFrame("Close dialog probe"); frame.setContentPane(panel); frame.setSize(1100,700); frame.setLocationRelativeTo(null); frame.setVisible(true);
                ((JTextField)find(panel,"itemName")).setText("Unsaved probe");
                Path root=Path.of(args[0]); Files.writeString(root.resolve("ready"),"ready");
                var watcher=new Timer(100,null);
                watcher.addActionListener(e -> {
                    if(!Files.exists(root.resolve("go")))return; watcher.stop();
                    var close=new Timer(900,event -> {
                        for(var window:java.awt.Window.getWindows()) if(window instanceof JDialog dialog) {
                            System.out.println("DIALOG "+dialog.getWidth()+"x"+dialog.getHeight()+" children="+dialog.getContentPane().getComponentCount());
                            
                            java.awt.Component component=findPane(dialog.getContentPane());
                            if(component instanceof JOptionPane pane) {
                                Object[] options=pane.getOptions(); System.out.println("OPTIONS "+java.util.Arrays.toString(options));
                                if(args.length>1 && args[1].equals("discard")) { pane.setValue(options[1]); continue; }
                                if(args.length>1 && args[1].equals("cancel")) { pane.setValue(options[2]); continue; }
                            }
                            dialog.dispose();
                        }
                    }); close.setRepeats(false); close.start();
                    try { System.out.println("RESULT "+panel.mayClose()); } catch(Throwable failure) { failure.printStackTrace(); }
                    frame.dispose(); System.exit(0);
                }); watcher.start();
            } catch(Exception failure) { failure.printStackTrace(); System.exit(1); }
        });
    }
    static java.awt.Component findPane(java.awt.Container parent) { for(var child:parent.getComponents()) { if(child instanceof JOptionPane)return child; if(child instanceof java.awt.Container nested) { var result=findPane(nested); if(result!=null)return result; } }return null; }
    static java.awt.Component find(java.awt.Container parent,String name) {
        for(var child:parent.getComponents()) { if(name.equals(child.getName()))return child; if(child instanceof java.awt.Container next) { var result=find(next,name); if(result!=null)return result; } } return null;
    }
}
