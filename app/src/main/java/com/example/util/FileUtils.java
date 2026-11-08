package com.example.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class FileUtils {

    private FileUtils() {}

    /**
     * Saves binary data directly to the phone's public Downloads directory.
     * Accessible immediately from the phone's File Manager / Downloads.
     */
    public static File saveToPublicDownloads(Context context, String filename, byte[] data, String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CyberDex");

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            if (uri != null) {
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) {
                        os.write(data);
                        os.flush();
                    }
                }
            }
        }

        // Also save to standard app documents file for sharing via FileProvider
        File appDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "CyberDex");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        File targetFile = new File(appDir, filename);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(data);
            fos.flush();
        }

        return targetFile;
    }

    public static File saveTextToPublicDownloads(Context context, String filename, String text, String mimeType) throws IOException {
        return saveToPublicDownloads(context, filename, text.getBytes(StandardCharsets.UTF_8), mimeType);
    }

    public static File saveBinaryFile(Context context, String filename, byte[] data) throws IOException {
        return saveToPublicDownloads(context, filename, data, "application/octet-stream");
    }

    public static File saveTextFile(Context context, String filename, String content) throws IOException {
        return saveTextToPublicDownloads(context, filename, content, "text/plain");
    }

    public static String readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = fis.read(buffer);
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    public static byte[] readBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        }
    }

    public static void shareFile(Context context, File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Exported from Cyber Dex Studio");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(context, "Saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    public static byte[] readBytesFromUri(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (is == null) {
                throw new IOException("Cannot open input stream for URI: " + uri);
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        }
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        String result = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        if (result == null || result.trim().isEmpty()) {
            result = "imported_file";
        }
        return result;
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format("%.1f %sB", (double) bytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}

