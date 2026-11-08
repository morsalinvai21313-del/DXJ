package com.example.compiler;

import android.content.Context;

import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.example.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DexDisassembler {

    public static class DisassembleResult {
        public final boolean success;
        public final String smaliCode;
        public final int classCount;
        public final List<File> smaliFiles;
        public final File zipArchive;
        public final String message;
        public final List<String> classDescriptors;

        public DisassembleResult(boolean success, String smaliCode, int classCount, List<File> smaliFiles,
                                 File zipArchive, String message, List<String> classDescriptors) {
            this.success = success;
            this.smaliCode = smaliCode;
            this.classCount = classCount;
            this.smaliFiles = smaliFiles != null ? smaliFiles : new ArrayList<>();
            this.zipArchive = zipArchive;
            this.message = message;
            this.classDescriptors = classDescriptors != null ? classDescriptors : new ArrayList<>();
        }
    }

    /**
     * Disassembles a real Android classes.dex file into clean Smali assembly using Baksmali.
     */
    public static DisassembleResult disassemble(Context context, File dexFile) {
        if (dexFile == null || !dexFile.exists() || dexFile.length() == 0) {
            return new DisassembleResult(false, "", 0, null, null, "DEX file not found or empty.", null);
        }

        try {
            File parentDir = context != null && context.getCacheDir() != null ?
                    context.getCacheDir() :
                    new File(System.getProperty("java.io.tmpdir", "/tmp"));

            File outDir = new File(parentDir, "baksmali_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
            if (!outDir.exists()) {
                outDir.mkdirs();
            }

            // Verify DEX magic header
            byte[] header = new byte[8];
            try (FileInputStream fis = new FileInputStream(dexFile)) {
                int read = fis.read(header);
                if (read < 4 || header[0] != 'd' || header[1] != 'e' || header[2] != 'x' || header[3] != '\n') {
                    return new DisassembleResult(false, "", 0, null, null,
                            "Invalid DEX magic bytes. File does not appear to be a valid Android Dalvik executable.", null);
                }
            }

            DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
            int classCount = dexBacked.getClasses().size();
            List<String> descriptors = new ArrayList<>();
            for (ClassDef cd : dexBacked.getClasses()) {
                descriptors.add(cd.getType());
            }

            BaksmaliOptions options = new BaksmaliOptions();
            options.apiLevel = AndroidPlatformManager.DEFAULT_MIN_API;
            options.parameterRegisters = true;
            options.localsDirective = true;
            options.sequentialLabels = true;

            boolean success = Baksmali.disassembleDexFile(dexBacked, outDir, 1, options);
            if (!success) {
                return new DisassembleResult(false, "", 0, null, null, "Baksmali disassembler encountered fatal errors.", descriptors);
            }

            List<File> generatedFiles = new ArrayList<>();
            collectFiles(outDir, generatedFiles);

            StringBuilder combined = new StringBuilder();
            for (File f : generatedFiles) {
                if (f.getName().endsWith(".smali")) {
                    if (combined.length() > 0) {
                        combined.append("\n# ========================================================\n");
                        combined.append("# Class: ").append(f.getName()).append("\n");
                        combined.append("# ========================================================\n\n");
                    }
                    combined.append(FileUtils.readFile(f)).append("\n");
                }
            }

            // Create ZIP archive containing all disassembled Smali files
            File zipFile = new File(parentDir, "smali_extracted_" + System.currentTimeMillis() + ".zip");
            createZip(outDir, zipFile);

            StringBuilder summary = new StringBuilder();
            summary.append("SUCCESS: DEX successfully disassembled with Baksmali.\n")
                    .append("• Total Classes: ").append(classCount).append("\n")
                    .append("• Disassembled Files: ").append(generatedFiles.size()).append("\n")
                    .append("• ZIP Archive: ").append(zipFile.getName()).append("\n\n")
                    .append("Class Descriptors:\n");
            for (String d : descriptors) {
                summary.append("  • ").append(d).append("\n");
            }

            return new DisassembleResult(true, combined.toString(), classCount, generatedFiles, zipFile, summary.toString(), descriptors);

        } catch (Throwable e) {
            return new DisassembleResult(false, "", 0, null, null, "Disassembly Exception: " + e.getMessage(), null);
        }
    }

    public static DisassembleResult disassembleBytes(Context context, byte[] dexBytes) {
        if (dexBytes == null || dexBytes.length == 0) {
            return new DisassembleResult(false, "", 0, null, null, "DEX byte array is null or empty.", null);
        }
        try {
            File parentDir = context != null && context.getCacheDir() != null ?
                    context.getCacheDir() :
                    new File(System.getProperty("java.io.tmpdir", "/tmp"));
            File tempDex = new File(parentDir, "temp_in_" + System.currentTimeMillis() + ".dex");
            try (FileOutputStream fos = new FileOutputStream(tempDex)) {
                fos.write(dexBytes);
            }
            return disassemble(context, tempDex);
        } catch (Throwable e) {
            return new DisassembleResult(false, "", 0, null, null, "Failed to write temp dex: " + e.getMessage(), null);
        }
    }

    private static void collectFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, list);
            } else {
                list.add(f);
            }
        }
    }

    private static void createZip(File sourceDir, File zipFile) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addDirToZip(sourceDir, sourceDir, zos);
        } catch (Throwable ignored) {}
    }

    private static void addDirToZip(File rootDir, File currentDir, ZipOutputStream zos) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[4096];
        for (File file : files) {
            if (file.isDirectory()) {
                addDirToZip(rootDir, file, zos);
            } else {
                String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }
}
