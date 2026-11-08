package com.example.activity;

import com.example.R;
import com.example.compiler.JavaToKotlinConverter;
import com.example.util.CyberTemplates;

public class JavaToKotlinActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_java_to_kotlin;
    }

    @Override
    protected String getModuleTitle() {
        return "JAVA TO KOTLIN";
    }

    @Override
    protected String getModuleBadge() {
        return "KOTLIN TRANSPILER";
    }

    @Override
    protected String getActionLabel() {
        return "CONVERT TO KOTLIN";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.JAVA_KOTLIN_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "Converted.kt";
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
        JavaToKotlinConverter.ConvertResult res = JavaToKotlinConverter.convert(source);
        mainHandler.post(() -> {
            if (res.success) {
                onExecutionSuccess(logs, "[SYS:STATUS] >> TRANSPILED TO IDIOMATIC KOTLIN", res.kotlinCode, null, null);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
