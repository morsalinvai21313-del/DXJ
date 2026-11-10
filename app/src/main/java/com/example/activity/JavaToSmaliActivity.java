package com.example.activity;

import com.example.R;
import com.example.compiler.JavaToSmaliCompiler;
import com.example.util.CyberTemplates;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaToSmaliActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_java_to_smali;
    }

    @Override
    protected String getModuleTitle() {
        return "JAVA TO SMALI";
    }

    @Override
    protected String getModuleBadge() {
        return "SMALI DISASSEMBLY";
    }

    @Override
    protected String getActionLabel() {
        return "CONVERT TO SMALI";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.JAVA_SMALI_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "output.smali";
    }

    @Override
    protected String getMimeType() {
        return "text/plain";
    }

    @Override
    protected boolean hasLivePreview() {
        return false;
    }

    @Override
    protected List<PipelineAction> getPipelineActions(String output, byte[] binary, File file) {
        List<PipelineAction> actions = new ArrayList<>();
        // Send generated Smali directly to SmaliToDexActivity
        actions.add(new PipelineAction("🚀 Send Smali to DEX Assembler", SmaliToDexActivity.class, output, null, false));
        // Transpile original Java source to Kotlin
        String src = etSourceCode != null ? etSourceCode.getText().toString() : "";
        if (!src.isEmpty()) {
            actions.add(new PipelineAction("🔄 Transpile Java to Kotlin", JavaToKotlinActivity.class, src, null, false));
            actions.add(new PipelineAction("🔨 Compile Java to DEX", JavaToDexActivity.class, src, null, false));
        }
        return actions;
    }

    @Override
    protected void executeCompilation(String source, StringBuilder logs) {
        if (importedDexFile != null && importedDexFile.exists()) {
            // User opened a binary DEX, JVM class, or APK/ZIP file in JavaToSmali
            com.example.compiler.DexDisassembler.DisassembleResult disRes =
                    com.example.compiler.DexDisassembler.disassemble(this, importedDexFile);
            mainHandler.post(() -> {
                if (disRes.success) {
                    onExecutionSuccess(logs, "[SYS:STATUS] >> EXTRACTED " + disRes.classCount + " CLASSES FROM ARTIFACT",
                            disRes.smaliCode, null, importedDexFile);
                } else {
                    onExecutionError(logs, disRes.message);
                }
            });
            return;
        }

        JavaToSmaliCompiler.CompileResult res = JavaToSmaliCompiler.compile(this, source);
        mainHandler.post(() -> {
            if (res.success) {
                onExecutionSuccess(logs, "[SYS:STATUS] >> COMPILED " + res.className + ".smali", res.smaliCode, null, res.dexFile);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
