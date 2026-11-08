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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DexDisassembler {

    public static class DisassembleResult {
        public final boolean success;
        public final String smaliCode;
        public final int classCount;
        public final List<File> smaliFiles;
        public final String message;

        public DisassembleResult(boolean success, String smaliCode, int classCount, List<File> smaliFiles, String message) {
            this.success = success;
            this.smaliCode = smaliCode;
            this.classCount = classCount;
            this.smaliFiles = smaliFiles;
            this.message = message;
        }
    }

    public static DisassembleResult disassemble(Context context, File dexFile) {
        if (dexFile == null || !dexFile.exists() || dexFile.length() == 0) {
            return new DisassembleResult(false, "", 0, null, "DEX file not found or empty.");
        }

        try {
            File outDir = new File(context.getCacheDir(), "baksmali_out_" + System.currentTimeMillis());
            if (!outDir.exists()) {
                outDir.mkdirs();
            }

            DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
            int classCount = dexBacked.getClasses().size();

            BaksmaliOptions options = new BaksmaliOptions();
            options.apiLevel = 26;
            options.parameterRegisters = true;
            options.localsDirective = true;

            boolean success = Baksmali.disassembleDexFile(dexBacked, outDir, 1, options);
            if (!success) {
                return new DisassembleResult(false, "", 0, null, "Baksmali failed to disassemble classes.dex");
            }

            List<File> generatedFiles = new ArrayList<>();
            collectFiles(outDir, generatedFiles);

            StringBuilder combined = new StringBuilder();
            for (File f : generatedFiles) {
                if (f.getName().endsWith(".smali")) {
                    if (combined.length() > 0) {
                        combined.append("\n# ========================================================\n");
                        combined.append("# File: ").append(f.getName()).append("\n");
                        combined.append("# ========================================================\n\n");
                    }
                    combined.append(FileUtils.readFile(f)).append("\n");
                }
            }

            String msg = "SUCCESS: Disassembled " + classCount + " class(es) into Smali.\nTotal files: " + generatedFiles.size();
            return new DisassembleResult(true, combined.toString(), classCount, generatedFiles, msg);

        } catch (Exception e) {
            return new DisassembleResult(false, "", 0, null, "Disassembly Exception: " + e.getMessage());
        }
    }

    public static DisassembleResult disassembleBytes(Context context, byte[] dexBytes) {
        try {
            File tempDex = new File(context.getCacheDir(), "temp_input_" + System.currentTimeMillis() + ".dex");
            try (FileOutputStream fos = new FileOutputStream(tempDex)) {
                fos.write(dexBytes);
            }
            return disassemble(context, tempDex);
        } catch (Exception e) {
            return new DisassembleResult(false, "", 0, null, "Failed to write temp dex: " + e.getMessage());
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
}
