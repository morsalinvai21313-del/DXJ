package com.example.activity;

import com.example.R;
import com.example.compiler.KotlinToJavaConverter;
import com.example.util.CyberTemplates;

public class KotlinToJavaActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_kotlin_to_java;
    }

    @Override
    protected String getModuleTitle() {
        return "KOTLIN TO JAVA";
    }

    @Override
    protected String getModuleBadge() {
        return "JAVA TRANSPILER";
    }

    @Override
    protected String getActionLabel() {
        return "CONVERT TO JAVA";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.KOTLIN_JAVA_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "Converted.java";
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
        KotlinToJavaConverter.ConvertResult res = KotlinToJavaConverter.convert(source);
        mainHandler.post(() -> {
            if (res.success) {
                onExecutionSuccess(logs, "[SYS:STATUS] >> TRANSPILED TO STANDARD JAVA", res.javaCode, null, null);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
