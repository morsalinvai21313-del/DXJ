package com.example.activity;

import com.example.R;
import com.example.compiler.DexDisassembler;
import com.example.compiler.SmaliAssembler;
import com.example.util.CyberTemplates;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DexToSmaliActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_dex_to_smali;
    }

    @Override
    protected String getModuleTitle() {
        return "DEX TO SMALI";
    }

    @Override
    protected String getModuleBadge() {
        return "DALVIK DISASSEMBLER";
    }

    @Override
    protected String getActionLabel() {
        return "DISASSEMBLE DEX";
    }

    @Override
    protected String getDefaultTemplate() {
        return "# DEX to Smali Disassembler ready.\n# Tap '📁 Open' to load classes.dex, or use sample bytecode below:\n" + CyberTemplates.SMALI_DEX_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "classes.smali";
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
        actions.add(new PipelineAction("🚀 Re-assemble in Smali to DEX", SmaliToDexActivity.class, output, null, false));
        actions.add(new PipelineAction("⚡ Compare with Java to Smali", JavaToSmaliActivity.class, null, null, false));
        return actions;
    }

    @Override
    protected void executeCompilation(String source, StringBuilder logs) {
        File dexFile = importedDexFile;
        if (dexFile == null || !dexFile.exists()) {
            if (importedFileBytes != null) {
                File cacheDex = new File(getCacheDir(), "imported_dex_" + System.currentTimeMillis() + ".dex");
                try (FileOutputStream fos = new FileOutputStream(cacheDex)) {
                    fos.write(importedFileBytes);
                } catch (Exception ignored) {}
                dexFile = cacheDex;
                importedDexFile = cacheDex;
            }
        }
        if (dexFile == null || !dexFile.exists()) {
            dexFile = lastGeneratedFile;
        }
        if (dexFile == null || !dexFile.exists()) {
            // Assemble from the source code box if user hasn't loaded an external DEX
            SmaliAssembler.AssembleResult asmRes = SmaliAssembler.assemble(this, source);
            if (asmRes.success) {
                dexFile = asmRes.dexFile;
            }
        }

        if (dexFile != null && dexFile.exists()) {
            DexDisassembler.DisassembleResult res = DexDisassembler.disassemble(this, dexFile);
            mainHandler.post(() -> {
                if (res.success) {
                    onExecutionSuccess(logs, "[SYS:STATUS] >> EXTRACTED " + res.classCount + " CLASSES FROM DEX", res.smaliCode, null, null);
                } else {
                    onExecutionError(logs, res.message);
                }
            });
        } else {
            mainHandler.post(() -> onExecutionError(logs, "ERROR: No valid DEX binary loaded. Tap '📁 Open File' to choose your classes.dex from phone memory."));
        }
    }
}
