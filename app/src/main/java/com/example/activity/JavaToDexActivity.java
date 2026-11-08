package com.example.activity;

import com.example.R;
import com.example.compiler.JavaToDexCompiler;
import com.example.util.CyberTemplates;
import com.example.util.FileUtils;

public class JavaToDexActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_java_to_dex;
    }

    @Override
    protected String getModuleTitle() {
        return "JAVA TO DEX";
    }

    @Override
    protected String getModuleBadge() {
        return "DEX COMPILER";
    }

    @Override
    protected String getActionLabel() {
        return "BUILD DEX";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.JAVA_DEX_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "classes.dxj.dex";
    }

    @Override
    protected String getMimeType() {
        return "application/octet-stream";
    }

    @Override
    protected boolean hasLivePreview() {
        return false;
    }

    @Override
    protected void executeCompilation(String source, StringBuilder logs) {
        JavaToDexCompiler.JavaToDexResult res = JavaToDexCompiler.compile(this, source);
        mainHandler.post(() -> {
            if (res.success) {
                lastGeneratedBinary = res.dexBytes;
                lastGeneratedFile = res.dexFile;
                String hexDump = buildHexSummary(res.dexBytes);
                String output = res.message + "\n\n# --- DEX BYTECODE STREAM ---\n" + hexDump;
                onExecutionSuccess(logs, "[SYS:STATUS] >> CLASSES.DXJ.DEX GENERATED (" + FileUtils.formatFileSize(res.dexBytes.length) + ")", output, res.dexBytes, res.dexFile);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
