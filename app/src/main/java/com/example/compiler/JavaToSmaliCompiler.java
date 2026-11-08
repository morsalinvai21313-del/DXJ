package com.example.compiler;

import android.content.Context;

import java.io.File;

public class JavaToSmaliCompiler {

    public static class CompileResult {
        public final boolean success;
        public final String smaliCode;
        public final String className;
        public final String packageName;
        public final String message;
        public final File dexFile;

        public CompileResult(boolean success, String smaliCode, String className, String packageName, String message, File dexFile) {
            this.success = success;
            this.smaliCode = smaliCode;
            this.className = className;
            this.packageName = packageName;
            this.message = message;
            this.dexFile = dexFile;
        }
    }

    public static CompileResult compile(String javaSource) {
        return compile(null, javaSource);
    }

    /**
     * Compiles Java source into JVM bytecode (via ECJ), converts to Dalvik DEX (via D8),
     * and disassembles into 100% genuine Dalvik Smali bytecode (via Baksmali).
     */
    public static CompileResult compile(Context context, String javaSource) {
        if (javaSource == null || javaSource.trim().isEmpty()) {
            return new CompileResult(false, "", "", "", "Java source code cannot be empty.", null);
        }

        JavaToDexCompiler.JavaToDexResult dexRes = JavaToDexCompiler.compile(context, javaSource);
        if (!dexRes.success) {
            return new CompileResult(false, "", "", "", dexRes.message, null);
        }

        DexDisassembler.DisassembleResult disRes = DexDisassembler.disassemble(context, dexRes.dexFile);
        if (!disRes.success) {
            return new CompileResult(false, "", "", "", "Bytecode Disassembly Failed: " + disRes.message, dexRes.dexFile);
        }

        String primaryClass = !dexRes.classNames.isEmpty() ? dexRes.classNames.get(0) : "CompiledClass";

        StringBuilder summary = new StringBuilder();
        summary.append("SUCCESS: Real Java -> Smali Compilation Complete!\n")
                .append("• ECJ: Java Source -> JVM Bytecode\n")
                .append("• D8: Bytecode -> Dalvik classes.dex\n")
                .append("• Baksmali: classes.dex -> Authentic Dalvik Smali\n")
                .append("• Classes: ").append(dexRes.classCount).append("\n")
                .append("• Methods: ").append(dexRes.methodCount).append("\n")
                .append("• Fields: ").append(dexRes.fieldCount).append("\n");

        return new CompileResult(true, disRes.smaliCode, primaryClass, "", summary.toString(), dexRes.dexFile);
    }
}
