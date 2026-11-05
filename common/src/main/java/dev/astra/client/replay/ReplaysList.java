package dev.astra.client.replay;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The .areplay files in the replays folder, newest first. */
final class ReplaysList {

    private ReplaysList() {
    }

    static List<File> files() {
        File[] all = Replay.folder().listFiles();
        if (all == null) {
            return Collections.emptyList();
        }
        List<File> out = new ArrayList<File>();
        for (File f : all) {
            if (f.isFile() && f.getName().endsWith(".areplay")) {
                out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName()); // names start with the date
            }
        });
        return out;
    }

    static void delete(File f) {
        f.delete();
        ReplayFile.infoFile(f).delete();
    }

    static List<File> sorted(File[] files) {
        return Arrays.asList(files);
    }
}
