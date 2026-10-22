package me.texyle.startreminders.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;


import net.minecraft.client.Minecraft;

/**
 * Native file pickers. The dialog runs on a helper thread (it would block the game otherwise);
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
                    // java.awt.FileDialog is the operating system's own dialog (on Windows the familiar Explorer one).
                    FileDialog dialog = new FileDialog((Frame) null, title, save ? FileDialog.SAVE : FileDialog.LOAD);
                    // On Windows a pattern in the file name field filters the list.
                    dialog.setFile(suggestedName != null ? suggestedName : "*.json");
                    dialog.setAlwaysOnTop(true);
                    dialog.setVisible(true);

                    String name = dialog.getFile();
                    String directory = dialog.getDirectory();
                    dialog.dispose();
                    if (name == null || directory == null) return;

                    final File chosen = new File(directory, name);
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            handler.onFile(chosen);
                        }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, "StratReminders-FileDialog").start();
    }
}
