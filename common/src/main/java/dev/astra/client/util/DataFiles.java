package dev.astra.client.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.astra.client.Astra;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Small JSON files shared by every version (favourite servers, waypoints, screenshot stars), kept
 * in %APPDATA%\.astra\data next to the instances folder.
 */
public final class DataFiles {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private DataFiles() {
    }

    /** %APPDATA%\.astra (the folder that holds instances/), or the game folder when run elsewhere. */
    public static File astraHome() {
        File game = Astra.platform().getGameDir();
        File parent = game.getParentFile();
        return parent != null && parent.getName().equals("instances") && parent.getParentFile() != null
                ? parent.getParentFile() : game;
    }

    public static File file(String name) {
        File dir = new File(astraHome(), "data");
        dir.mkdirs();
        return new File(dir, name);
    }

    /** The file's JSON, or null if it's missing or broken. */
    @SuppressWarnings("deprecation")
    public static JsonElement read(String name) {
        File f = file(name);
        if (!f.isFile()) {
            return null;
        }
        try {
            Reader r = new InputStreamReader(new FileInputStream(f), UTF8);
            try {
                return new JsonParser().parse(r);
            } finally {
                r.close();
            }
        } catch (Exception e) {
            System.err.println("[Astra] Couldn't read " + f + ": " + e);
            return null;
        }
    }

    /** Writes through a temporary file, so a crash never leaves half a file behind. */
    public static void write(String name, JsonElement json) {
        File f = file(name);
        File tmp = new File(f.getPath() + ".tmp");
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), UTF8);
            try {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, w);
            } finally {
                w.close();
            }
            if (f.exists() && !f.delete()) {
                throw new java.io.IOException("can't replace " + f);
            }
            if (!tmp.renameTo(f)) {
                throw new java.io.IOException("can't rename " + tmp);
            }
        } catch (Exception e) {
            System.err.println("[Astra] Couldn't save " + f + ": " + e);
        }
    }
}
