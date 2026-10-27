package dev.astra.client.util;

import java.awt.Desktop;
import java.io.File;
import java.util.Locale;

public final class Files {

    private Files() {
    }

    /** Opens a folder in the system file manager without blocking the game. */
    public static void open(final File dir) {
        dir.mkdirs();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                try {
                    if (os.contains("win")) {
                        new ProcessBuilder("explorer", dir.getAbsolutePath()).start();
                    } else if (os.contains("mac")) {
                        new ProcessBuilder("open", dir.getAbsolutePath()).start();
                    } else if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(dir);
                    } else {
                        new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                    }
                } catch (Exception e) {
                    System.err.println("[Astra] Couldn't open " + dir + ": " + e);
                }
            }
        }, "Astra open folder");
        t.setDaemon(true);
        t.start();
    }
}
