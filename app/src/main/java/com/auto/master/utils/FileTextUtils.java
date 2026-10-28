package com.auto.master.utils;

import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** UTF-8 file helpers that work on every supported Android version. */
public final class FileTextUtils {
    private static final int BUFFER_SIZE = 8192;

    private FileTextUtils() {
    }

    public static String readUtf8(File file) throws IOException {
        if (file == null) {
            throw new IOException("File is null");
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static void writeUtf8Atomically(File file, String content) throws IOException {
        if (file == null) {
            throw new IOException("File is null");
        }
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create parent directory");
        }
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            output.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(output);
        } catch (IOException e) {
            if (output != null) {
                atomicFile.failWrite(output);
            }
            throw e;
        }
    }
}
