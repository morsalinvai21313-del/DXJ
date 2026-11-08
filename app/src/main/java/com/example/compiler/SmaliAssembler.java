package com.example.compiler;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import com.example.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.security.MessageDigest;

public class SmaliAssembler {

    public static class AssembleResult {
        public final boolean success;
        public final File dexFile;
        public final byte[] dexBytes;
        public final String message;
        public final int classCount;
        public final String sha256;

        public AssembleResult(boolean success, File dexFile, byte[] dexBytes, String message, int classCount, String sha256) {
            this.success = success;
            this.dexFile = dexFile;
            this.dexBytes = dexBytes;
            this.message = message;
            this.classCount = classCount;
            this.sha256 = sha256;
        }
    }

    public static AssembleResult assemble(Context context, String smaliCode) {
        if (smaliCode == null || smaliCode.trim().isEmpty()) {
            return new AssembleResult(false, null, null, "Smali source code cannot be empty.", 0, "");
        }

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errCapture));

        File tempDir = null;
        try {
            tempDir = new File(context.getCacheDir(), "smali_build_" + System.currentTimeMillis());
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            File smaliFile = new File(tempDir, "input.smali");
            FileUtils.saveBinaryFile(context, "input.smali", smaliCode.getBytes("UTF-8"));
            // Also write directly in tempDir
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(smaliFile)) {
                fos.write(smaliCode.getBytes("UTF-8"));
            }

            File outDex = new File(tempDir, "classes.dex");

            SmaliOptions options = new SmaliOptions();
            options.apiLevel = 26;
            options.outputDexFile = outDex.getAbsolutePath();
            options.jobs = 1;
            options.verboseErrors = true;

            boolean assembled = Smali.assemble(options, smaliFile.getAbsolutePath());

            if (!assembled || !outDex.exists() || outDex.length() == 0) {
                String error = errCapture.toString("UTF-8");
                if (error.isEmpty()) {
                    error = "Smali assembly failed. Please verify syntax, registers, and directives.";
                }
                return new AssembleResult(false, null, null, error, 0, "");
            }

            byte[] dexBytes = FileUtils.readBytes(outDex);
            DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(outDex, Opcodes.getDefault());
            int classCount = dexBacked.getClasses().size();

            // Calculate SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(dexBytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }

            String summary = "SUCCESS: Assembled valid classes.dex\n" +
                    "Classes count: " + classCount + "\n" +
                    "Binary size: " + FileUtils.formatFileSize(dexBytes.length) + "\n" +
                    "DEX Magic: dex\\n035\\0\n" +
                    "SHA-256: " + hex.toString();

            return new AssembleResult(true, outDex, dexBytes, summary, classCount, hex.toString());

        } catch (Exception e) {
            String errorMsg = errCapture.toString();
            if (errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            return new AssembleResult(false, null, null, "Assembly Exception: " + errorMsg, 0, "");
        } finally {
            System.setErr(originalErr);
        }
    }
}
