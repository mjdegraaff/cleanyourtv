package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Keeps a copy of the owner's choices (tile order, App library picks, card decisions) in the external
 * files directory, next to config.json, so they survive an uninstall and reinstall.
 *
 * Builds are signed with a fresh key every time (no signing key is kept in the repository), so an update
 * cannot install over the previous build and is installed after an uninstall instead. The updater copies
 * {@code state/} out before the uninstall and back after the first start; on the next start
 * {@link #restoreIfMissing} puts each file back into private storage when it is missing there.
 *
 * Only plain files the launcher already writes are copied, never more than {@link #MAX_BYTES} each.
 */
final class StateBackup {
    private static final String TAG = "TvHome.StateBackup";
    private static final String DIR = "state";
    private static final long MAX_BYTES = 256 * 1024;
    private static final String[] FILES = {
            Order.FILE_NAME, "library_apps.txt", "library_home_removed.txt", Cards.STORE_FILE
    };

    private StateBackup() {}

    /** Copies backed-up files into private storage where the private copy is missing. Never throws. */
    static void restoreIfMissing(Context ctx) {
        try {
            File ext = Config.dir(ctx);
            if (ext == null) return;
            File backup = new File(ext, DIR);
            if (!backup.isDirectory()) return;
            for (String name : FILES) {
                File from = new File(backup, name);
                File to = new File(ctx.getFilesDir(), name);
                if (from.isFile() && !to.exists()) copy(from, to);
            }
        } catch (Exception e) {
            Log.w(TAG, "restore failed: " + e.getMessage());
        }
    }

    /** Copies the private files that exist to the backup folder; a missing private file leaves its backup alone. */
    static void save(Context ctx) {
        try {
            File ext = Config.dir(ctx);
            if (ext == null) return;
            File backup = new File(ext, DIR);
            if (!backup.isDirectory() && !backup.mkdirs()) return;
            for (String name : FILES) {
                File from = new File(ctx.getFilesDir(), name);
                if (from.isFile() && from.length() > 0) copy(from, new File(backup, name));
            }
        } catch (Exception e) {
            Log.w(TAG, "save failed: " + e.getMessage());
        }
    }

    private static void copy(File from, File to) throws Exception {
        if (from.length() > MAX_BYTES) return;
        File tmp = new File(to.getParentFile(), to.getName() + ".tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(to)) {
            to.delete();
            if (!tmp.renameTo(to)) tmp.delete();
        }
    }
}
