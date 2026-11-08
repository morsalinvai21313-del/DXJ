package com.example.compiler;

import android.content.Context;

import java.io.File;

public class JavaToDexCompiler {

    public static class JavaToDexResult {
        public final boolean success;
        public final String smaliIntermediate;
        public final File dexFile;
        public final byte[] dexBytes;
        public final String message;
        public final int classCount;
        public final String sha256;

        public JavaToDexResult(boolean success, String smaliIntermediate, File dexFile, byte[] dexBytes, String message, int classCount, String sha256) {
            this.success = success;
            this.smaliIntermediate = smaliIntermediate;
            this.dexFile = dexFile;
            this.dexBytes = dexBytes;
            this.message = message;
            this.classCount = classCount;
            this.sha256 = sha256;
        }
    }

    public static JavaToDexResult compile(Context context, String javaSource) {
        JavaToSmaliCompiler.CompileResult smaliRes = JavaToSmaliCompiler.compile(javaSource);
        if (!smaliRes.success) {
            return new JavaToDexResult(false, "", null, null, "Java Compilation Failed:\n" + smaliRes.message, 0, "");
        }

        SmaliAssembler.AssembleResult assembleRes = SmaliAssembler.assemble(context, smaliRes.smaliCode);
        if (!assembleRes.success) {
            return new JavaToDexResult(false, smaliRes.smaliCode, null, null, "DEX Assembly Failed:\n" + assembleRes.message, 0, "");
        }

        String summary = "SUCCESS: Compiled Java source to valid classes.dex\n" +
                "Target Class: " + smaliRes.className + "\n" +
                "Package: " + smaliRes.packageName + "\n" +
                assembleRes.message;

        return new JavaToDexResult(
                true,
                smaliRes.smaliCode,
                assembleRes.dexFile,
                assembleRes.dexBytes,
                summary,
                assembleRes.classCount,
                assembleRes.sha256
        );
    }
}
