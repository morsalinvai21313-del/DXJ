package com.example.compiler;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import com.example.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SmaliAssembler {

    public static class AssembleResult {
        public final boolean success;
        public final File dexFile;
        public final byte[] dexBytes;
        public final String message;
        public final int classCount;
        public final int methodCount;
        public final int fieldCount;
        public final String sha256;
        public final List<String> classDescriptors;

        public AssembleResult(boolean success, File dexFile, byte[] dexBytes, String message,
                              int classCount, int methodCount, int fieldCount, String sha256, List<String> classDescriptors) {
            this.success = success;
            this.dexFile = dexFile;
            this.dexBytes = dexBytes;
            this.message = message;
            this.classCount = classCount;
            this.methodCount = methodCount;
            this.fieldCount = fieldCount;
            this.sha256 = sha256;
            this.classDescriptors = classDescriptors != null ? classDescriptors : new ArrayList<>();
        }
    }

    /**
     * Assembles one or more Smali classes into a verified classes.dex file.
     * Supports multiple .class blocks in a single input string or multi-file directories.
     */
    public static AssembleResult assemble(Context context, String smaliSource) {
        if (smaliSource == null || smaliSource.trim().isEmpty()) {
            return new AssembleResult(false, null, null, "Smali source code cannot be empty.", 0, 0, 0, "", null);
        }

        File parentDir = context != null && context.getCacheDir() != null ?
                context.getCacheDir() :
                new File(System.getProperty("java.io.tmpdir", "/tmp"));

        File tempDir = null;
        PrintStream origErr = System.err;
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();

        try {
            tempDir = new File(parentDir, "smali_asm_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // Split multi-class smali if user pasted or imported multiple classes together
            List<File> smaliFiles = extractSmaliClasses(tempDir, smaliSource);
            if (smaliFiles.isEmpty()) {
                // If no .class header pattern matched, save as single smali file
                File single = new File(tempDir, "input.smali");
                try (FileOutputStream fos = new FileOutputStream(single)) {
                    fos.write(smaliSource.getBytes(StandardCharsets.UTF_8));
                }
                smaliFiles.add(single);
            }

            File outDex = new File(tempDir, "classes.dex");

            SmaliOptions options = new SmaliOptions();
            options.apiLevel = AndroidPlatformManager.DEFAULT_MIN_API;
            options.outputDexFile = outDex.getAbsolutePath();
            options.jobs = 1;
            options.verboseErrors = true;

            List<String> pathList = new ArrayList<>();
            for (File sf : smaliFiles) {
                pathList.add(sf.getAbsolutePath());
            }

            System.setErr(new PrintStream(errCapture));
            boolean assembled = Smali.assemble(options, pathList);

            String errorLogs = errCapture.toString("UTF-8").trim();

            if (!assembled || !outDex.exists() || outDex.length() == 0) {
                String cleanError = errorLogs.isEmpty() ?
                        "Smali assembly rejected bytecode. Please check instruction syntax, registers (v/p), and labels." :
                        cleanSmaliErrors(errorLogs, tempDir.getAbsolutePath());
                return new AssembleResult(false, null, null, "Smali Assembly Error:\n" + cleanError, 0, 0, 0, "", null);
            }

            byte[] dexBytes = FileUtils.readBytes(outDex);

            // Validate generated DEX with dexlib2
            DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(outDex, Opcodes.getDefault());
            int classCount = dexBacked.getClasses().size();
            int methodCount = 0;
            int fieldCount = 0;
            List<String> descriptors = new ArrayList<>();

            for (ClassDef cd : dexBacked.getClasses()) {
                descriptors.add(cd.getType());
                for (Object ignored : cd.getMethods()) methodCount++;
                for (Object ignored : cd.getFields()) fieldCount++;
            }

            // Calculate SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(dexBytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }

            StringBuilder summary = new StringBuilder();
            summary.append("SUCCESS: Genuine Smali -> classes.dex Assembly Complete!\n")
                    .append("• Assembler Engine: Google Smali 3.0.10\n")
                    .append("• Total Classes: ").append(classCount).append("\n")
                    .append("• Total Methods: ").append(methodCount).append("\n")
                    .append("• Total Fields: ").append(fieldCount).append("\n")
                    .append("• DEX Size: ").append(FileUtils.formatFileSize(dexBytes.length)).append(" (").append(dexBytes.length).append(" bytes)\n")
                    .append("• Magic Header: dex\\n035\\0\n")
                    .append("• SHA-256: ").append(hex).append("\n\n")
                    .append("Class Definitions in DEX:\n");
            for (String desc : descriptors) {
                summary.append("  • ").append(desc).append("\n");
            }

            return new AssembleResult(true, outDex, dexBytes, summary.toString(),
                    classCount, methodCount, fieldCount, hex.toString(), descriptors);

        } catch (Throwable t) {
            String captured = errCapture.toString().trim();
            String msg = captured.isEmpty() ? t.getMessage() : captured;
            return new AssembleResult(false, null, null, "Assembly Exception: " + msg, 0, 0, 0, "", null);
        } finally {
            System.setErr(origErr);
        }
    }

    /**
     * Assembles a directory of Smali files or a ZIP package into classes.dex.
     */
    public static AssembleResult assembleDirectoryOrZip(Context context, File sourceDirOrZip) {
        if (sourceDirOrZip == null || !sourceDirOrZip.exists()) {
            return new AssembleResult(false, null, null, "Source file/directory does not exist.", 0, 0, 0, "", null);
        }

        File parentDir = context != null && context.getCacheDir() != null ?
                context.getCacheDir() :
                new File(System.getProperty("java.io.tmpdir", "/tmp"));

        File workDir = new File(parentDir, "smali_batch_" + System.currentTimeMillis());
        workDir.mkdirs();

        try {
            if (sourceDirOrZip.getName().endsWith(".zip")) {
                unzip(sourceDirOrZip, workDir);
            } else if (sourceDirOrZip.isDirectory()) {
                copyDirectory(sourceDirOrZip, workDir);
            }

            List<File> smaliFiles = new ArrayList<>();
            collectSmaliFiles(workDir, smaliFiles);

            if (smaliFiles.isEmpty()) {
                return new AssembleResult(false, null, null, "No .smali files found in directory/zip archive.", 0, 0, 0, "", null);
            }

            File outDex = new File(workDir, "classes.dex");

            SmaliOptions options = new SmaliOptions();
            options.apiLevel = 26;
            options.outputDexFile = outDex.getAbsolutePath();
            options.jobs = 1;
            options.verboseErrors = true;

            List<String> paths = new ArrayList<>();
            for (File f : smaliFiles) paths.add(f.getAbsolutePath());

            PrintStream origErr = System.err;
            ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errCapture));

            boolean success = false;
            try {
                success = Smali.assemble(options, paths);
            } finally {
                System.setErr(origErr);
            }

            if (!success || !outDex.exists()) {
                return new AssembleResult(false, null, null, "Batch Smali assembly failed: " + errCapture.toString(), 0, 0, 0, "", null);
            }

            byte[] dexBytes = FileUtils.readBytes(outDex);
            DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(outDex, Opcodes.getDefault());
            int classCount = dexBacked.getClasses().size();

            return new AssembleResult(true, outDex, dexBytes, "SUCCESS: Batch assembled " + classCount + " classes.", classCount, 0, 0, "", null);

        } catch (Throwable t) {
            return new AssembleResult(false, null, null, "Batch Assembly Error: " + t.getMessage(), 0, 0, 0, "", null);
        }
    }

    private static List<File> extractSmaliClasses(File targetDir, String fullSource) {
        List<File> files = new ArrayList<>();
        // Split by .class directive
        String[] chunks = fullSource.split("(?m)(?=^\\.class\\s+)");
        int index = 0;

        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty()) continue;

            Matcher m = Pattern.compile("(?m)^\\.class.*?\\s+L([a-zA-Z0-9_/$]+);").matcher(trimmed);
            if (m.find()) {
                String classPath = m.group(1);
                File smaliFile = new File(targetDir, classPath + ".smali");
                File parent = smaliFile.getParentFile();
                if (parent != null) parent.mkdirs();

                try (FileOutputStream fos = new FileOutputStream(smaliFile)) {
                    fos.write(trimmed.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {}

                files.add(smaliFile);
            } else {
                File fallback = new File(targetDir, "Class_" + (index++) + ".smali");
                try (FileOutputStream fos = new FileOutputStream(fallback)) {
                    fos.write(trimmed.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
                files.add(fallback);
            }
        }
        return files;
    }

    private static void collectSmaliFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectSmaliFiles(f, list);
            else if (f.getName().endsWith(".smali")) list.add(f);
        }
    }

    private static void unzip(File zipFile, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File f = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    f.mkdirs();
                } else {
                    File p = f.getParentFile();
                    if (p != null) p.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        int len;
                        while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                }
            }
        }
    }

    private static void copyDirectory(File source, File destination) {
        if (source.isDirectory()) {
            if (!destination.exists()) destination.mkdirs();
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    copyDirectory(new File(source, file), new File(destination, file));
                }
            }
        } else {
            try {
                FileUtils.copyFile(source, destination);
            } catch (Exception ignored) {}
        }
    }

    private static String cleanSmaliErrors(String rawLogs, String rootPath) {
        return rawLogs.replace(rootPath, "").trim();
    }
}
