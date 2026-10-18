package me.texyle.startreminders.gui;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import net.minecraft.client.Minecraft;

/**
 * Native file pickers. The Swing dialog runs on a helper thread (it would block the game otherwise);
 * the chosen file is handed back on the Minecraft client thread.
 */
public final class FileDialogs {

    /** Receives the chosen file on the Minecraft client thread. */
    public interface FileHandler {
        void onFile(File file);
    }

    private FileDialogs() {}

    /** Shows a "save" dialog; the handler is not called if the user cancels. */
    public static void chooseSaveFile(final String title, final String suggestedName, final FileHandler handler) {
        choose(title, suggestedName, true, handler);
    }

    /** Shows an "open" dialog; the handler is not called if the user cancels. */
    public static void chooseOpenFile(final String title, final FileHandler handler) {
        choose(title, null, false, handler);
    }

    private static void choose(final String title, final String suggestedName, final boolean save, final FileHandler handler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File[] chosen = new File[1];

                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            JFileChooser chooser = new JFileChooser();
                            chooser.setDialogTitle(title);
                            if (suggestedName != null) {
                                chooser.setSelectedFile(new File(suggestedName));
                            }
                            int result = save ? chooser.showSaveDialog(null) : chooser.showOpenDialog(null);
                            if (result == JFileChooser.APPROVE_OPTION) {
                                chosen[0] = chooser.getSelectedFile();
                            }
                        }
                    });

                    if (chosen[0] == null) return;

                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            handler.onFile(chosen[0]);
                        }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }
}
