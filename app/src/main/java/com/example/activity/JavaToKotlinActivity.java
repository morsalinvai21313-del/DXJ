package com.example.activity;

import com.example.R;
import com.example.compiler.JavaToKotlinConverter;
import com.example.util.CyberTemplates;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    protected List<PipelineAction> getPipelineActions(String output, byte[] binary, File file) {
        List<PipelineAction> actions = new ArrayList<>();
        actions.add(new PipelineAction("🔄 Convert Back: Kotlin to Java", KotlinToJavaActivity.class, output, null, false));
        String src = etSourceCode != null ? etSourceCode.getText().toString() : "";
        if (!src.isEmpty()) {
            actions.add(new PipelineAction("🔨 Compile Source (Java to DEX)", JavaToDexActivity.class, src, null, false));
            actions.add(new PipelineAction("📜 Compile Source (Java to Smali)", JavaToSmaliActivity.class, src, null, false));
        }
        return actions;
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
