package com.example.compiler;

import android.content.Context;
import android.util.Log;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Universal Bytecode & Dalvik Disassembler.
 * Supports:
 * 1. Android Dalvik Executable (.dex)
 * 2. Multi-DEX APK and ZIP archives (extracts and disassembles all classes*.dex)
 * 3. Java JVM Bytecode (.class) via on-the-fly D8 translation to Dalvik
 */
public class DexDisassembler {

    private static final String TAG = "DexDisassembler";

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
     * Disassembles any DEX, multi-DEX APK/ZIP, or JVM .class file into genuine Dalvik Smali assembly.
     */
    public static DisassembleResult disassemble(Context context, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            return new DisassembleResult(false, "", 0, null, null, "Source file not found or empty.", null);
        }

        try {
            File parentDir = context != null && context.getCacheDir() != null ?
                    context.getCacheDir() :
                    new File(System.getProperty("java.io.tmpdir", "/tmp"));

            File workDir = new File(parentDir, "disasm_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
            workDir.mkdirs();

            byte[] header = new byte[8];
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                fis.read(header);
            }

            List<File> targetDexFiles = new ArrayList<>();
            String fileTypeDescription = "DEX Binary";

            // Check format by magic bytes or extension
            if (isZipOrApk(header, sourceFile.getName())) {
                fileTypeDescription = "APK/ZIP Archive (Multi-DEX)";
                File dexExtractDir = new File(workDir, "extracted_dex");
                dexExtractDir.mkdirs();
                extractDexFilesFromZip(sourceFile, dexExtractDir, targetDexFiles);
                if (targetDexFiles.isEmpty()) {
                    return new DisassembleResult(false, "", 0, null, null,
                            "Archive does not contain any classes*.dex files.", null);
                }
            } else if (isClassFile(header, sourceFile.getName())) {
                fileTypeDescription = "JVM .class Bytecode";
                File dexOutDir = new File(workDir, "d8_out");
                dexOutDir.mkdirs();
                File generatedDex = convertClassToDex(context, sourceFile, dexOutDir);
                if (generatedDex == null || !generatedDex.exists() || generatedDex.length() == 0) {
                    return new DisassembleResult(false, "", 0, null, null,
                            "Could not convert JVM .class bytecode to Dalvik DEX via D8.", null);
                }
                targetDexFiles.add(generatedDex);
            } else if (isDexFile(header, sourceFile.getName())) {
                fileTypeDescription = "Dalvik DEX";
                targetDexFiles.add(sourceFile);
            } else {
                // Try as DEX fallback if length >= 40
                if (sourceFile.length() >= 40) {
                    targetDexFiles.add(sourceFile);
                } else {
                    return new DisassembleResult(false, "", 0, null, null,
                            "Unrecognized file format. Expected .dex, .class, .apk, or .zip archive.", null);
                }
            }

            File outDir = new File(workDir, "smali_out");
            outDir.mkdirs();

            BaksmaliOptions options = new BaksmaliOptions();
            options.apiLevel = AndroidPlatformManager.DEFAULT_MIN_API;
            options.parameterRegisters = true;
            options.localsDirective = true;
            options.sequentialLabels = true;

            int totalClassCount = 0;
            List<String> allDescriptors = new ArrayList<>();

            for (File dex : targetDexFiles) {
                try {
                    DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(dex, Opcodes.getDefault());
                    totalClassCount += dexBacked.getClasses().size();
                    for (ClassDef cd : dexBacked.getClasses()) {
                        allDescriptors.add(cd.getType());
                    }
                    Baksmali.disassembleDexFile(dexBacked, outDir, 1, options);
                } catch (Throwable t) {
                    Log.w(TAG, "Error disassembling dex: " + dex.getName() + " -> " + t.getMessage());
                }
            }

            List<File> generatedSmaliFiles = new ArrayList<>();
            collectFiles(outDir, generatedSmaliFiles);

            if (generatedSmaliFiles.isEmpty()) {
                return new DisassembleResult(false, "", 0, null, null,
                        "Baksmali completed without producing .smali output files.", allDescriptors);
            }

            StringBuilder combined = new StringBuilder();
            for (File f : generatedSmaliFiles) {
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
            summary.append("SUCCESS: Disassembly Complete!\n")
                    .append("• Source Type: ").append(fileTypeDescription).append("\n")
                    .append("• DEX Files Processed: ").append(targetDexFiles.size()).append("\n")
                    .append("• Total Classes: ").append(totalClassCount).append("\n")
                    .append("• Smali Files Extracted: ").append(generatedSmaliFiles.size()).append("\n")
                    .append("• ZIP Archive: ").append(zipFile.getName()).append("\n\n")
                    .append("Classes Disassembled:\n");
            for (String d : allDescriptors) {
                summary.append("  • ").append(d).append("\n");
            }

            return new DisassembleResult(true, combined.toString(), totalClassCount, generatedSmaliFiles, zipFile, summary.toString(), allDescriptors);

        } catch (Throwable e) {
            return new DisassembleResult(false, "", 0, null, null, "Disassembly Exception: " + e.getMessage(), null);
        }
    }

    public static DisassembleResult disassembleBytes(Context context, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new DisassembleResult(false, "", 0, null, null, "Byte array is null or empty.", null);
        }
        try {
            File parentDir = context != null && context.getCacheDir() != null ?
                    context.getCacheDir() :
                    new File(System.getProperty("java.io.tmpdir", "/tmp"));
            String ext = (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') ? ".zip" : ".dex";
            File tempFile = new File(parentDir, "temp_in_" + System.currentTimeMillis() + ext);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
            }
            return disassemble(context, tempFile);
        } catch (Throwable e) {
            return new DisassembleResult(false, "", 0, null, null, "Failed to write temp file: " + e.getMessage(), null);
        }
    }

    private static boolean isDexFile(byte[] header, String name) {
        if (name != null && name.toLowerCase().endsWith(".dex")) return true;
        return header != null && header.length >= 4 &&
                header[0] == 'd' && header[1] == 'e' && header[2] == 'x' && header[3] == '\n';
    }

    private static boolean isZipOrApk(byte[] header, String name) {
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".apk") || lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".aar")) {
                return true;
            }
        }
        return header != null && header.length >= 4 &&
                header[0] == 'P' && header[1] == 'K' && header[2] == 0x03 && header[3] == 0x04;
    }

    private static boolean isClassFile(byte[] header, String name) {
        if (name != null && name.toLowerCase().endsWith(".class")) return true;
        return header != null && header.length >= 4 &&
                (header[0] & 0xFF) == 0xCA && (header[1] & 0xFF) == 0xFE &&
                (header[2] & 0xFF) == 0xBA && (header[3] & 0xFF) == 0xBE;
    }

    private static void extractDexFilesFromZip(File zipFile, File outDir, List<File> dexList) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.endsWith(".dex")) {
                    String safeName = entryName.replace('/', '_').replace('\\', '_');
                    File outFile = new File(outDir, safeName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int read;
                        while ((read = zis.read(buf)) != -1) {
                            fos.write(buf, 0, read);
                        }
                    }
                    if (outFile.length() > 0) {
                        dexList.add(outFile);
                    }
                }
            }
        }
    }

    private static File convertClassToDex(Context context, File classFile, File outDir) {
        try {
            D8Command.Builder d8 = D8Command.builder();
            d8.addProgramFiles(classFile.toPath());
            d8.setOutput(outDir.toPath(), OutputMode.DexIndexed);
            d8.setMinApiLevel(AndroidPlatformManager.DEFAULT_MIN_API);

            File androidJar = AndroidPlatformManager.getAndroidJar(context);
            if (androidJar != null && androidJar.exists()) {
                d8.addLibraryFiles(androidJar.toPath());
            }

            D8.run(d8.build());
            File dex = new File(outDir, "classes.dex");
            return dex.exists() && dex.length() > 0 ? dex : null;
        } catch (Throwable t) {
            Log.w(TAG, "D8 .class conversion error: " + t.getMessage());
            return null;
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
