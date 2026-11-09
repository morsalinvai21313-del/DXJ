package com.example.activity;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.R;
import com.example.util.CyberSoundEffects;
import com.example.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseModuleActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_CODE = "com.example.EXTRA_SOURCE_CODE";
    public static final String EXTRA_DEX_PATH = "com.example.EXTRA_DEX_PATH";
    public static final String EXTRA_AUTO_RUN = "com.example.EXTRA_AUTO_RUN";

    public static class PipelineAction {
        public final String label;
        public final Class<?> targetActivity;
        public final String sourceCode;
        public final String dexPath;
        public final boolean autoRun;

        public PipelineAction(String label, Class<?> targetActivity, String sourceCode, String dexPath, boolean autoRun) {
            this.label = label;
            this.targetActivity = targetActivity;
            this.sourceCode = sourceCode;
            this.dexPath = dexPath;
            this.autoRun = autoRun;
        }
    }

    protected ScrollView mainScrollView;
    protected ImageView btnBack;
    protected TextView tvModuleTitle;
    protected TextView tvModuleBadge;

    protected TextView tvSourceTitle;
    protected EditText etSourceCode;
    protected TextView btnPrimaryAction;

    // File import & Clipboard HUD
    protected TextView btnActionPaste;
    protected TextView btnActionImport;
    protected TextView btnActionTemplate;
    protected TextView btnActionClear;
    protected LinearLayout layoutLoadedFileBanner;
    protected TextView tvLoadedFileInfo;
    protected TextView btnResetLoadedFile;

    protected byte[] importedFileBytes = null;
    protected String importedFileName = null;
    protected File importedDexFile = null;

    // Terminal HUD
    protected LinearLayout layoutTerminalConsole;
    protected TextView tvTerminalLogs;
    protected ProgressBar terminalProgress;

    // Output Artifact Box
    protected LinearLayout layoutOutputCard;
    protected TextView tvOutputTitle;
    protected EditText etOutputCode;
    protected TextView btnCopyOutput;
    protected TextView btnDownloadOutput;
    protected TextView btnShareOutput;
    protected LinearLayout layoutPipelineContainer;

    // Live Preview elements (for XML modules)
    protected LinearLayout layoutPreviewTabs;
    protected TextView btnTabCode;
    protected TextView btnTabPreview;
    protected LinearLayout layoutVisualPreviewCard;
    protected FrameLayout containerLivePreview;

    protected byte[] lastGeneratedBinary = null;
    protected File lastGeneratedFile = null;

    protected final ExecutorService executor = Executors.newSingleThreadExecutor();
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handleSelectedFile(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResourceId());

        bindViews();
        setupListeners();
        initModuleContent();
        handleIncomingIntent();
    }

    protected abstract int getLayoutResourceId();
    protected abstract String getModuleTitle();
    protected abstract String getModuleBadge();
    protected abstract String getActionLabel();
    protected abstract String getDefaultTemplate();
    protected abstract String getDefaultFilename();
    protected abstract String getMimeType();
    protected abstract boolean hasLivePreview();
    protected abstract void executeCompilation(String source, StringBuilder logs);

    protected List<PipelineAction> getPipelineActions(String output, byte[] binary, File file) {
        return new ArrayList<>();
    }

    protected void bindViews() {
        mainScrollView = findViewById(R.id.main_scroll_view);
        btnBack = findViewById(R.id.btn_back);
        tvModuleTitle = findViewById(R.id.tv_module_title);
        tvModuleBadge = findViewById(R.id.tv_module_badge);

        tvSourceTitle = findViewById(R.id.tv_source_title);
        etSourceCode = findViewById(R.id.et_source_code);
        btnPrimaryAction = findViewById(R.id.btn_primary_action);

        btnActionPaste = findViewById(R.id.btn_action_paste);
        btnActionImport = findViewById(R.id.btn_action_import);
        btnActionTemplate = findViewById(R.id.btn_action_template);
        btnActionClear = findViewById(R.id.btn_action_clear);

        layoutLoadedFileBanner = findViewById(R.id.layout_loaded_file_banner);
        tvLoadedFileInfo = findViewById(R.id.tv_loaded_file_info);
        btnResetLoadedFile = findViewById(R.id.btn_reset_loaded_file);

        layoutTerminalConsole = findViewById(R.id.layout_terminal_console);
        tvTerminalLogs = findViewById(R.id.tv_terminal_logs);
        terminalProgress = findViewById(R.id.terminal_progress);

        layoutOutputCard = findViewById(R.id.layout_output_card);
        tvOutputTitle = findViewById(R.id.tv_output_title);
        etOutputCode = findViewById(R.id.et_output_code);
        btnCopyOutput = findViewById(R.id.btn_copy_output);
        btnDownloadOutput = findViewById(R.id.btn_download_output);
        btnShareOutput = findViewById(R.id.btn_share_output);

        layoutPreviewTabs = findViewById(R.id.layout_preview_tabs);
        btnTabCode = findViewById(R.id.btn_tab_code);
        btnTabPreview = findViewById(R.id.btn_tab_preview);
        layoutVisualPreviewCard = findViewById(R.id.layout_visual_preview_card);
        containerLivePreview = findViewById(R.id.container_live_preview);
    }

    protected void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (etSourceCode != null) {
            etSourceCode.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateSourceStats();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnActionPaste != null) {
            btnActionPaste.setOnClickListener(v -> pasteFromClipboard());
        }

        if (btnActionTemplate != null) {
            btnActionTemplate.setOnClickListener(v -> resetLoadedFile());
        }

        if (btnActionClear != null) {
            btnActionClear.setOnClickListener(v -> {
                if (etSourceCode != null) etSourceCode.setText("");
                if (etOutputCode != null) etOutputCode.setText("");
                if (containerLivePreview != null) containerLivePreview.removeAllViews();
                lastGeneratedBinary = null;
                lastGeneratedFile = null;
                importedFileBytes = null;
                importedFileName = null;
                importedDexFile = null;
                if (layoutLoadedFileBanner != null) layoutLoadedFileBanner.setVisibility(View.GONE);
                if (layoutTerminalConsole != null) layoutTerminalConsole.setVisibility(View.GONE);
                if (layoutOutputCard != null) layoutOutputCard.setVisibility(View.GONE);
                updateSourceStats();
            });
        }

        if (btnActionImport != null) {
            btnActionImport.setOnClickListener(v -> launchFilePicker());
        }

        if (btnResetLoadedFile != null) {
            btnResetLoadedFile.setOnClickListener(v -> resetLoadedFile());
        }

        if (btnPrimaryAction != null) {
            btnPrimaryAction.setOnClickListener(v -> startExecution());
        }

        if (btnCopyOutput != null) {
            btnCopyOutput.setOnClickListener(v -> {
                String text = etOutputCode != null ? etOutputCode.getText().toString() : "";
                if (text.isEmpty()) {
                    Toast.makeText(this, "No output to copy.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("CyberDex Artifact", text));
                    Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnDownloadOutput != null) {
            btnDownloadOutput.setOnClickListener(v -> downloadToPhoneStorage());
        }

        if (btnShareOutput != null) {
            btnShareOutput.setOnClickListener(v -> shareOutputArtifact());
        }

        if (btnTabCode != null) {
            btnTabCode.setOnClickListener(v -> showOutputTab(true));
        }
        if (btnTabPreview != null) {
            btnTabPreview.setOnClickListener(v -> showOutputTab(false));
        }
    }

    protected void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        String incomingSource = intent.getStringExtra(EXTRA_SOURCE_CODE);
        if (incomingSource != null && !incomingSource.trim().isEmpty()) {
            if (etSourceCode != null) {
                etSourceCode.setText(incomingSource);
                etSourceCode.setSelection(incomingSource.length());
            }
            Toast.makeText(this, "Loaded code from pipeline module", Toast.LENGTH_SHORT).show();
        }

        String incomingDexPath = intent.getStringExtra(EXTRA_DEX_PATH);
        if (incomingDexPath != null) {
            File f = new File(incomingDexPath);
            if (f.exists()) {
                importedDexFile = f;
                try {
                    importedFileBytes = FileUtils.readBytes(f);
                } catch (Exception ignored) {}
                importedFileName = f.getName();
                if (layoutLoadedFileBanner != null && tvLoadedFileInfo != null) {
                    layoutLoadedFileBanner.setVisibility(View.VISIBLE);
                    tvLoadedFileInfo.setText("📁 PIPELINE: " + f.getName() + " (" + FileUtils.formatFileSize(f.length()) + ")");
                }
                Toast.makeText(this, "Loaded DEX artifact from pipeline", Toast.LENGTH_SHORT).show();
            }
        }

        if (intent.getBooleanExtra(EXTRA_AUTO_RUN, false)) {
            mainHandler.postDelayed(this::startExecution, 400);
        }
    }

    protected void pasteFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).coerceToText(this);
                    if (text != null && text.length() > 0) {
                        if (etSourceCode != null) {
                            etSourceCode.setText(text);
                            etSourceCode.setSelection(text.length());
                        }
                        updateSourceStats();
                        Toast.makeText(this, "Code pasted from clipboard!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            Toast.makeText(this, "Clipboard is empty!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Paste failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    protected void initModuleContent() {
        if (tvModuleTitle != null) tvModuleTitle.setText(getModuleTitle());
        if (tvModuleBadge != null) tvModuleBadge.setText(getModuleBadge());
        if (btnPrimaryAction != null) btnPrimaryAction.setText(getActionLabel());
        if (tvSourceTitle != null) tvSourceTitle.setText(getModuleTitle() + " SOURCE");

        if (layoutPreviewTabs != null) {
            layoutPreviewTabs.setVisibility(hasLivePreview() ? View.VISIBLE : View.GONE);
        }
        showOutputTab(true);
        // Box is left empty by default until user pastes or loads a file/template
        if (etSourceCode != null) {
            etSourceCode.setText("");
            etSourceCode.setHint("Ready for input. Paste code, open file, or tap 'Template'...");
        }
        if (etOutputCode != null) {
            etOutputCode.setText("");
        }
        if (containerLivePreview != null) {
            containerLivePreview.removeAllViews();
        }
        if (layoutLoadedFileBanner != null) {
            layoutLoadedFileBanner.setVisibility(View.GONE);
        }
        if (layoutTerminalConsole != null) {
            layoutTerminalConsole.setVisibility(View.GONE);
        }
        if (layoutOutputCard != null) {
            layoutOutputCard.setVisibility(View.GONE);
        }
        updateSourceStats();
    }

    protected void loadTemplate() {
        if (etSourceCode != null) {
            etSourceCode.setText(getDefaultTemplate());
        }
        if (etOutputCode != null) {
            etOutputCode.setText("");
        }
        if (containerLivePreview != null) {
            containerLivePreview.removeAllViews();
        }
        if (layoutLoadedFileBanner != null) {
            layoutLoadedFileBanner.setVisibility(View.GONE);
        }
        if (layoutTerminalConsole != null) {
            layoutTerminalConsole.setVisibility(View.GONE);
        }
        if (layoutOutputCard != null) {
            layoutOutputCard.setVisibility(View.GONE);
        }
        updateSourceStats();
    }

    protected void resetLoadedFile() {
        importedFileBytes = null;
        importedFileName = null;
        importedDexFile = null;
        loadTemplate();
        Toast.makeText(this, "Reverted to default template.", Toast.LENGTH_SHORT).show();
    }

    protected void updateSourceStats() {
        // Line stats updated dynamically
    }

    protected void launchFilePicker() {
        try {
            filePickerLauncher.launch(new String[]{"*/*"});
        } catch (Exception e) {
            Toast.makeText(this, "File Picker error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    protected void handleSelectedFile(Uri uri) {
        try {
            String fileName = FileUtils.getFileNameFromUri(this, uri);
            byte[] bytes = FileUtils.readBytesFromUri(this, uri);
            if (bytes == null || bytes.length == 0) {
                Toast.makeText(this, "Selected file is empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            importedFileBytes = bytes;
            importedFileName = fileName;

            if (layoutTerminalConsole != null) layoutTerminalConsole.setVisibility(View.GONE);
            if (layoutOutputCard != null) layoutOutputCard.setVisibility(View.GONE);

            boolean isDex = isDexBinary(fileName, bytes);
            if (isDex) {
                File cacheDex = new File(getCacheDir(), "imported_" + System.currentTimeMillis() + ".dex");
                try (FileOutputStream fos = new FileOutputStream(cacheDex)) {
                    fos.write(bytes);
                }
                importedDexFile = cacheDex;

                String summary = "# ========================================================\n"
                        + "# FILE LOADED: " + fileName + "\n"
                        + "# SIZE: " + FileUtils.formatFileSize(bytes.length) + " (" + bytes.length + " bytes)\n"
                        + "# STATUS: DEX BINARY READY\n"
                        + "# ========================================================\n"
                        + "# Press '" + getActionLabel() + "' to execute.\n";
                if (etSourceCode != null) etSourceCode.setText(summary);
            } else {
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (etSourceCode != null) etSourceCode.setText(text);
            }

            if (layoutLoadedFileBanner != null && tvLoadedFileInfo != null) {
                layoutLoadedFileBanner.setVisibility(View.VISIBLE);
                tvLoadedFileInfo.setText("📁 STORAGE: " + fileName + " (" + FileUtils.formatFileSize(bytes.length) + ")");
            }

            updateSourceStats();
            Toast.makeText(this, "Loaded " + fileName + " (" + FileUtils.formatFileSize(bytes.length) + ")", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to read file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isDexBinary(String fileName, byte[] bytes) {
        if (fileName != null && fileName.toLowerCase().endsWith(".dex")) return true;
        if (bytes != null && bytes.length >= 4) {
            return bytes[0] == 'd' && bytes[1] == 'e' && bytes[2] == 'x' && bytes[3] == '\n';
        }
        return false;
    }

    protected void showOutputTab(boolean showCode) {
        if (btnTabCode != null) {
            btnTabCode.setSelected(showCode);
            btnTabCode.setTextColor(showCode ? Color.parseColor("#00F0FF") : Color.parseColor("#94A3B8"));
        }
        if (btnTabPreview != null) {
            btnTabPreview.setSelected(!showCode);
            btnTabPreview.setTextColor(!showCode ? Color.parseColor("#00F0FF") : Color.parseColor("#94A3B8"));
        }
        if (etOutputCode != null) {
            etOutputCode.setVisibility(showCode ? View.VISIBLE : View.GONE);
        }
        if (layoutVisualPreviewCard != null) {
            layoutVisualPreviewCard.setVisibility(!showCode ? View.VISIBLE : View.GONE);
        }
    }

    protected void startExecution() {
        if (etSourceCode == null) return;
        final String source = etSourceCode.getText().toString();
        if (source.trim().isEmpty() && importedDexFile == null && importedFileBytes == null) {
            Toast.makeText(this, "Please enter or load source code first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Play authentic continuous robotic compilation sound stream during animation
        CyberSoundEffects.startRoboticAnimationSound();

        if (layoutTerminalConsole != null) layoutTerminalConsole.setVisibility(View.VISIBLE);
        if (layoutOutputCard != null) layoutOutputCard.setVisibility(View.GONE);
        if (terminalProgress != null) terminalProgress.setVisibility(View.VISIBLE);
        if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(false);

        final StringBuilder logs = new StringBuilder();
        logs.append("[SYS:INIT] > ").append(getModuleTitle()).append(" ENGINE ONLINE...\n");
        if (importedFileName != null) {
            logs.append("[SYS:MOUNT] > SOURCE: ").append(importedFileName).append("\n");
        }
        if (tvTerminalLogs != null) tvTerminalLogs.setText(logs.toString());

        // Step 1: Scanner (after 320ms)
        mainHandler.postDelayed(() -> {
            logs.append("[SYS:PARSER] > SCANNING TOKENS & DIRECTIVES...\n");
            if (tvTerminalLogs != null) tvTerminalLogs.setText(logs.toString());
        }, 320);

        // Step 2: Register Allocation / Dalvik synthesis (after 750ms)
        mainHandler.postDelayed(() -> {
            logs.append("[SYS:DALVIK] > ALLOCATING REGISTERS & INSTRUCTION MAP...\n");
            if (tvTerminalLogs != null) tvTerminalLogs.setText(logs.toString());
        }, 750);

        // Step 3: Core compile (after 1200ms)
        mainHandler.postDelayed(() -> {
            logs.append("[SYS:EXEC] > COMPILING BYTECODE ARTIFACT...\n");
            if (tvTerminalLogs != null) tvTerminalLogs.setText(logs.toString());

            executor.execute(() -> {
                try {
                    Thread.sleep(200);
                    executeCompilation(source, logs);
                } catch (Exception e) {
                    mainHandler.post(() -> onExecutionError(logs, "EXCEPTION: " + e.getMessage()));
                }
            });
        }, 1200);
    }

    /**
     * Prepends Morsalin Modding Tools signature watermark comment to the generated code.
     */
    protected String applyWatermark(String content) {
        if (content == null || content.isEmpty()) return content;
        if (content.contains("Morsalin Modding Tools")) return content;

        String mime = getMimeType();
        String header;
        if ("text/xml".equals(mime)) {
            header = "<!--\n" +
                    "========================================================\n" +
                    "# Morsalin Modding Tools\n" +
                    "# Developed & Powered by Morsalin\n" +
                    "# All Rights Reserved\n" +
                    "========================================================\n" +
                    "-->\n\n";
        } else if (getModuleTitle().contains("JAVA") || getModuleTitle().contains("KOTLIN")) {
            header = "// ========================================================\n" +
                    "// Morsalin Modding Tools\n" +
                    "// Developed & Powered by Morsalin\n" +
                    "// All Rights Reserved\n" +
                    "// ========================================================\n\n";
        } else {
            header = "# ========================================================\n" +
                    "# Morsalin Modding Tools\n" +
                    "# Developed & Powered by Morsalin\n" +
                    "# All Rights Reserved\n" +
                    "# ========================================================\n\n";
        }
        return header + content;
    }

    protected void onExecutionSuccess(StringBuilder logs, String successStatus, String outputContent, byte[] binary, File file) {
        CyberSoundEffects.stopRoboticAnimationSound();
        CyberSoundEffects.playSuccessChirp();

        logs.append(successStatus).append("\n");
        if (tvTerminalLogs != null) tvTerminalLogs.setText(logs.toString());
        if (terminalProgress != null) terminalProgress.setVisibility(View.GONE);
        if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(true);

        String watermarkedOutput = applyWatermark(outputContent);
        if (etOutputCode != null) etOutputCode.setText(watermarkedOutput);
        this.lastGeneratedBinary = binary;
        this.lastGeneratedFile = file;

        // Build Smart Pipeline Quick Actions
        buildPipelineActionsUI(watermarkedOutput, binary, file);

        if (layoutOutputCard != null) {
            layoutOutputCard.setVisibility(View.VISIBLE);
            layoutOutputCard.setAlpha(0f);
            ObjectAnimator.ofFloat(layoutOutputCard, "alpha", 0f, 1f).setDuration(300).start();
        }

        if (mainScrollView != null && layoutOutputCard != null) {
            mainScrollView.postDelayed(() -> mainScrollView.smoothScrollTo(0, layoutOutputCard.getTop()), 100);
        }
    }

    protected void buildPipelineActionsUI(String output, byte[] binary, File file) {
        if (layoutOutputCard == null) return;

        // Remove existing pipeline container if already added
        if (layoutPipelineContainer != null) {
            layoutOutputCard.removeView(layoutPipelineContainer);
            layoutPipelineContainer = null;
        }

        List<PipelineAction> actions = getPipelineActions(output, binary, file);
        if (actions == null || actions.isEmpty()) return;

        layoutPipelineContainer = new LinearLayout(this);
        layoutPipelineContainer.setOrientation(LinearLayout.VERTICAL);
        layoutPipelineContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int padTop = (int) (14 * getResources().getDisplayMetrics().density);
        layoutPipelineContainer.setPadding(0, padTop, 0, 0);

        // Pipeline Section Header
        TextView header = new TextView(this);
        header.setText("⚡ INTERCONNECT PIPELINE / SEND TO MODULE:");
        header.setTextColor(Color.parseColor("#00F0FF"));
        header.setTextSize(11f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setLetterSpacing(0.04f);
        try {
            header.setTypeface(ResourcesCompat.getFont(this, R.font.jetbrains_mono), android.graphics.Typeface.BOLD);
        } catch (Exception ignored) {}
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);
        header.setLayoutParams(hlp);
        layoutPipelineContainer.addView(header);

        for (PipelineAction act : actions) {
            TextView btn = new TextView(this);
            btn.setText(act.label);
            btn.setTextColor(Color.parseColor("#00F0FF"));
            btn.setTextSize(12f);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            try {
                btn.setTypeface(ResourcesCompat.getFont(this, R.font.jetbrains_mono), android.graphics.Typeface.BOLD);
            } catch (Exception ignored) {}
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.btn_cyber_pipeline);
            btn.setClickable(true);
            btn.setFocusable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (44 * getResources().getDisplayMetrics().density));
            lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                Intent i = new Intent(BaseModuleActivity.this, act.targetActivity);
                if (act.sourceCode != null) {
                    i.putExtra(EXTRA_SOURCE_CODE, act.sourceCode);
                }
                if (act.dexPath != null) {
                    i.putExtra(EXTRA_DEX_PATH, act.dexPath);
                }
                i.putExtra(EXTRA_AUTO_RUN, act.autoRun);
                startActivity(i);
            });

            layoutPipelineContainer.addView(btn);
        }

        layoutOutputCard.addView(layoutPipelineContainer);
    }

    protected void onExecutionError(StringBuilder logs, String errorMsg) {
        CyberSoundEffects.stopRoboticAnimationSound();
        CyberSoundEffects.playErrorBuzz();

        logs.append("[ERROR] >> EXECUTION FAILED: ").append(errorMsg).append("\n");
        if (tvTerminalLogs != null) tvTerminalLogs.setText(logs.toString());
        if (terminalProgress != null) terminalProgress.setVisibility(View.GONE);
        if (btnPrimaryAction != null) btnPrimaryAction.setEnabled(true);
        if (layoutOutputCard != null) layoutOutputCard.setVisibility(View.GONE);
        Toast.makeText(this, "Execution failed. Check terminal logs.", Toast.LENGTH_SHORT).show();
    }

    protected void downloadToPhoneStorage() {
        String textOutput = etOutputCode != null ? etOutputCode.getText().toString() : "";
        if (textOutput.isEmpty() && lastGeneratedBinary == null) {
            Toast.makeText(this, "No output to download.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File savedFile;
            if (lastGeneratedBinary != null) {
                savedFile = FileUtils.saveToPublicDownloads(this, getDefaultFilename(), lastGeneratedBinary, getMimeType());
            } else {
                savedFile = FileUtils.saveTextToPublicDownloads(this, getDefaultFilename(), textOutput, getMimeType());
            }
            this.lastGeneratedFile = savedFile;
            Toast.makeText(this, "Downloaded to Phone: " + savedFile.getName() + " in Downloads/CyberDex", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    protected void shareOutputArtifact() {
        String textOutput = etOutputCode != null ? etOutputCode.getText().toString() : "";
        if (textOutput.isEmpty() && lastGeneratedBinary == null) {
            Toast.makeText(this, "No output to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File fileToShare = lastGeneratedFile;
            if (fileToShare == null || !fileToShare.exists()) {
                if (lastGeneratedBinary != null) {
                    fileToShare = FileUtils.saveBinaryFile(this, getDefaultFilename(), lastGeneratedBinary);
                } else {
                    fileToShare = FileUtils.saveTextFile(this, getDefaultFilename(), textOutput);
                }
                this.lastGeneratedFile = fileToShare;
            }
            FileUtils.shareFile(this, fileToShare, getMimeType());
        } catch (Exception e) {
            Toast.makeText(this, "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    protected static String buildHexSummary(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(bytes.length, 128);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02X ", bytes[i]));
            if ((i + 1) % 16 == 0) sb.append("\n");
        }
        if (bytes.length > limit) {
            sb.append("\n... (" + (bytes.length - limit) + " more bytes)");
        }
        return sb.toString();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CyberSoundEffects.stopRoboticAnimationSound();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CyberSoundEffects.stopRoboticAnimationSound();
        executor.shutdown();
    }
}
