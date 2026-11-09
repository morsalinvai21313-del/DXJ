package com.example.activity;

import com.example.R;
import com.example.compiler.SmaliAssembler;
import com.example.util.CyberTemplates;
import com.example.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SmaliToDexActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_smali_to_dex;
    }

    @Override
    protected String getModuleTitle() {
        return "SMALI TO DEX";
    }

    @Override
    protected String getModuleBadge() {
        return "SMALI ASSEMBLER";
    }

    @Override
    protected String getActionLabel() {
        return "ASSEMBLE DEX";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.SMALI_DEX_SAMPLE;
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
    protected List<PipelineAction> getPipelineActions(String output, byte[] binary, File file) {
        List<PipelineAction> actions = new ArrayList<>();
        if (file != null && file.exists()) {
            actions.add(new PipelineAction("🔍 Inspect in DEX to Smali", DexToSmaliActivity.class, null, file.getAbsolutePath(), true));
        }
        actions.add(new PipelineAction("✏️ Edit in Java to Smali", JavaToSmaliActivity.class, null, null, false));
        return actions;
    }

    @Override
    protected void executeCompilation(String source, StringBuilder logs) {
        SmaliAssembler.AssembleResult res = SmaliAssembler.assemble(this, source);
        mainHandler.post(() -> {
            if (res.success) {
                lastGeneratedBinary = res.dexBytes;
                lastGeneratedFile = res.dexFile;
                String hexDump = buildHexSummary(res.dexBytes);
                String output = res.message + "\n\n# --- DEX BYTECODE STREAM ---\n" + hexDump;
                onExecutionSuccess(logs, "[SYS:STATUS] >> SMALI ASSEMBLED TO CLASSES.DXJ.DEX (" + FileUtils.formatFileSize(res.dexBytes.length) + ")", output, res.dexBytes, res.dexFile);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
