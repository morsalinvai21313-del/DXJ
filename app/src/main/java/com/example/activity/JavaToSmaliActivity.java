package com.example.activity;

import com.example.R;
import com.example.compiler.JavaToSmaliCompiler;
import com.example.util.CyberTemplates;

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
    protected void executeCompilation(String source, StringBuilder logs) {
        JavaToSmaliCompiler.CompileResult res = JavaToSmaliCompiler.compile(this, source);
        mainHandler.post(() -> {
            if (res.success) {
                onExecutionSuccess(logs, "[SYS:STATUS] >> COMPILED " + res.className + ".smali", res.smaliCode, null, null);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
