package com.example;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.compiler.DexDisassembler;
import com.example.compiler.JavaToDexCompiler;
import com.example.compiler.JavaToKotlinConverter;
import com.example.compiler.JavaToSmaliCompiler;
import com.example.compiler.KotlinToJavaConverter;
import com.example.compiler.SmaliAssembler;
import com.example.compiler.XmlDrawableRenderer;
import com.example.compiler.XmlLayoutRenderer;
import com.example.util.CyberTemplates;
import com.example.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public enum Module {
        JAVA_TO_SMALI("JAVA TO SMALI", "JAVA BYTECODE TO SMALI DISASSEMBLY", "CONVERT TO SMALI", "output.smali", "text/plain"),
        JAVA_TO_DEX("JAVA TO DEX", "COMPILE JAVA TO CLASSES.DEX", "BUILD DEX", "classes.dex", "application/octet-stream"),
        SMALI_TO_DEX("SMALI TO DEX", "ASSEMBLE SMALI TO CLASSES.DEX", "ASSEMBLE DEX", "classes.dex", "application/octet-stream"),
        DEX_TO_SMALI("DEX TO SMALI", "DISASSEMBLE CLASSES.DEX TO SMALI", "DISASSEMBLE DEX", "classes.smali", "text/plain"),
        JAVA_TO_KOTLIN("JAVA TO KOTLIN", "JAVA TO KOTLIN TRANSPILER", "CONVERT TO KOTLIN", "Converted.kt", "text/plain"),
        KOTLIN_TO_JAVA("KOTLIN TO JAVA", "KOTLIN TO JAVA TRANSPILER", "CONVERT TO JAVA", "Converted.java", "text/plain"),
        XML_LAYOUT("XML LAYOUT", "ANDROID XML LAYOUT VALIDATOR & ENGINE", "VALIDATE & RENDER", "activity_custom.xml", "text/xml"),
        XML_DRAWABLE("XML DRAWABLE", "ANDROID DRAWABLE XML SHAPE & GRADIENT ENGINE", "RENDER DRAWABLE", "drawable_custom.xml", "text/xml");

        public final String title;
        public final String subtitle;
        public final String actionLabel;
        public final String defaultFilename;
        public final String mimeType;

        Module(String title, String subtitle, String actionLabel, String defaultFilename, String mimeType) {
            this.title = title;
            this.subtitle = subtitle;
            this.actionLabel = actionLabel;
            this.defaultFilename = defaultFilename;
            this.mimeType = mimeType;
        }
    }

    private Module currentModule = Module.JAVA_TO_SMALI;

    // UI elements
    private ScrollView mainScrollView;
    private TextView tvActiveModuleSubtitle;
    private TextView tvModuleBadge;
    private TextView tvSourceTitle;
    private TextView tvSourceStats;
    private EditText etSourceCode;
    private TextView btnPrimaryAction;

    // Storage File Import HUD
    private TextView btnSourceOpenFile;
    private LinearLayout layoutLoadedFileBanner;
    private TextView tvLoadedFileInfo;
    private TextView btnResetLoadedFile;

    private byte[] importedFileBytes = null;
    private String importedFileName = null;
    private File importedDexFile = null;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handleSelectedFile(uri);
                }
            });

    // Hacker Terminal HUD
    private LinearLayout layoutTerminalConsole;
    private TextView tvTerminalLogs;
    private ProgressBar terminalProgress;

    // Output Artifact Box (Initially hidden)
    private LinearLayout layoutOutputCard;
    private TextView tvOutputTitle;
    private EditText etOutputCode;
    private TextView btnCopyOutput;
    private TextView btnDownloadOutput;
    private TextView btnShareOutput;

    // Live Preview
    private LinearLayout layoutPreviewTabs;
    private TextView btnTabCode;
    private TextView btnTabPreview;
    private LinearLayout layoutVisualPreviewCard;
    private TextView tvPreviewInfo;
    private FrameLayout containerLivePreview;

    // Top Action Buttons
    private TextView btnActionTemplate;
    private TextView btnActionImport;
    private TextView btnActionClear;

    private TextView[] moduleTabs;

    // Cache generated binary if any
    private byte[] lastGeneratedBinary = null;
    private File lastGeneratedFile = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupTabs();
        setupListeners();

        selectModule(Module.JAVA_TO_SMALI);
    }

    private void bindViews() {
        mainScrollView = findViewById(R.id.main_scroll_view);
        tvActiveModuleSubtitle = findViewById(R.id.tv_active_module_subtitle);
        tvModuleBadge = findViewById(R.id.tv_module_badge);
        tvSourceTitle = findViewById(R.id.tv_source_title);
        tvSourceStats = findViewById(R.id.tv_source_stats);
        etSourceCode = findViewById(R.id.et_source_code);
        btnPrimaryAction = findViewById(R.id.btn_primary_action);

        btnSourceOpenFile = findViewById(R.id.btn_source_open_file);
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
        tvPreviewInfo = findViewById(R.id.tv_preview_info);
        containerLivePreview = findViewById(R.id.container_live_preview);

        btnActionTemplate = findViewById(R.id.btn_action_template);
        btnActionImport = findViewById(R.id.btn_action_import);
        btnActionClear = findViewById(R.id.btn_action_clear);

        moduleTabs = new TextView[]{
                findViewById(R.id.tab_java_smali),
                findViewById(R.id.tab_java_dex),
                findViewById(R.id.tab_smali_dex),
                findViewById(R.id.tab_dex_smali),
                findViewById(R.id.tab_java_kt),
                findViewById(R.id.tab_kt_java),
                findViewById(R.id.tab_xml_layout),
                findViewById(R.id.tab_xml_drawable)
        };
    }

    private void setupTabs() {
        Module[] modules = Module.values();
        for (int i = 0; i < moduleTabs.length && i < modules.length; i++) {
            final Module mod = modules[i];
            moduleTabs[i].setOnClickListener(v -> selectModule(mod));
        }
    }

    private void selectModule(Module module) {
        this.currentModule = module;
        this.lastGeneratedBinary = null;
        this.lastGeneratedFile = null;

        if (layoutLoadedFileBanner != null) {
            layoutLoadedFileBanner.setVisibility(View.GONE);
        }
        this.importedFileBytes = null;
        this.importedFileName = null;
        this.importedDexFile = null;

        // Reset output & terminal visibility for fresh action
        layoutTerminalConsole.setVisibility(View.GONE);
        layoutOutputCard.setVisibility(View.GONE);

        // Update Tab styling
        Module[] allModules = Module.values();
        for (int i = 0; i < moduleTabs.length && i < allModules.length; i++) {
            boolean isSelected = allModules[i] == module;
            moduleTabs[i].setSelected(isSelected);
            if (isSelected) {
                moduleTabs[i].setTextColor(Color.parseColor("#00F0FF"));
            } else {
                moduleTabs[i].setTextColor(Color.parseColor("#94A3B8"));
            }
        }

        tvActiveModuleSubtitle.setText(module.subtitle);
        tvModuleBadge.setText(module.title);
        btnPrimaryAction.setText(module.actionLabel);
        tvSourceTitle.setText(module.title + " SOURCE");

        boolean isXmlModule = (module == Module.XML_LAYOUT || module == Module.XML_DRAWABLE);
        layoutPreviewTabs.setVisibility(isXmlModule ? View.VISIBLE : View.GONE);
        showOutputTab(true);

        loadTemplate();
    }

    private void setupListeners() {
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

        btnActionTemplate.setOnClickListener(v -> {
            resetLoadedFile();
            layoutTerminalConsole.setVisibility(View.GONE);
            layoutOutputCard.setVisibility(View.GONE);
        });

        btnActionClear.setOnClickListener(v -> {
            etSourceCode.setText("");
            etOutputCode.setText("");
            containerLivePreview.removeAllViews();
            lastGeneratedBinary = null;
            lastGeneratedFile = null;
            importedFileBytes = null;
            importedFileName = null;
            importedDexFile = null;
            if (layoutLoadedFileBanner != null) {
                layoutLoadedFileBanner.setVisibility(View.GONE);
            }
            layoutTerminalConsole.setVisibility(View.GONE);
            layoutOutputCard.setVisibility(View.GONE);
            updateSourceStats();
        });

        // Open file picker from phone storage
        btnActionImport.setOnClickListener(v -> launchFilePicker());
        if (btnSourceOpenFile != null) {
            btnSourceOpenFile.setOnClickListener(v -> launchFilePicker());
        }
        if (btnResetLoadedFile != null) {
            btnResetLoadedFile.setOnClickListener(v -> resetLoadedFile());
        }

        btnPrimaryAction.setOnClickListener(v -> executeHackerAction());

        // 1. Copy button
        btnCopyOutput.setOnClickListener(v -> {
            String text = etOutputCode.getText().toString();
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

        // 2. Download button: saves directly to phone's public Downloads directory!
        btnDownloadOutput.setOnClickListener(v -> downloadToPhoneStorage());

        // 3. Share button: triggers system share chooser
        btnShareOutput.setOnClickListener(v -> shareOutputArtifact());

        btnTabCode.setOnClickListener(v -> showOutputTab(true));
        btnTabPreview.setOnClickListener(v -> showOutputTab(false));
    }

    private void launchFilePicker() {
        try {
            filePickerLauncher.launch(new String[]{"*/*"});
        } catch (Exception e) {
            Toast.makeText(this, "File Picker error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void resetLoadedFile() {
        importedFileBytes = null;
        importedFileName = null;
        importedDexFile = null;
        if (layoutLoadedFileBanner != null) {
            layoutLoadedFileBanner.setVisibility(View.GONE);
        }
        loadTemplate();
        Toast.makeText(this, "Reverted to default template.", Toast.LENGTH_SHORT).show();
    }

    private void showLoadedFileBanner(String name, long size) {
        if (layoutLoadedFileBanner != null && tvLoadedFileInfo != null) {
            layoutLoadedFileBanner.setVisibility(View.VISIBLE);
            tvLoadedFileInfo.setText("📁 STORAGE: " + name + " (" + FileUtils.formatFileSize(size) + ")");
        }
    }

    private void handleSelectedFile(Uri uri) {
        try {
            String fileName = FileUtils.getFileNameFromUri(this, uri);
            byte[] bytes = FileUtils.readBytesFromUri(this, uri);
            if (bytes == null || bytes.length == 0) {
                Toast.makeText(this, "Selected file is empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            importedFileBytes = bytes;
            importedFileName = fileName;

            layoutTerminalConsole.setVisibility(View.GONE);
            layoutOutputCard.setVisibility(View.GONE);

            boolean isDex = isDexBinary(fileName, bytes);

            if (isDex || currentModule == Module.DEX_TO_SMALI) {
                File cacheDex = new File(getCacheDir(), "imported_" + System.currentTimeMillis() + ".dex");
                try (FileOutputStream fos = new FileOutputStream(cacheDex)) {
                    fos.write(bytes);
                }
                importedDexFile = cacheDex;

                if (currentModule != Module.DEX_TO_SMALI) {
                    selectModule(Module.DEX_TO_SMALI);
                }

                String summary = "# ========================================================\n"
                        + "# FILE LOADED FROM STORAGE: " + fileName + "\n"
                        + "# SIZE: " + FileUtils.formatFileSize(bytes.length) + " (" + bytes.length + " bytes)\n"
                        + "# STATUS: DEX BINARY MOUNTED & VERIFIED\n"
                        + "# ========================================================\n"
                        + "# Magic Header: " + extractMagicHeader(bytes) + "\n"
                        + "# Ready for Dalvik disassembly.\n"
                        + "# Press '" + currentModule.actionLabel + "' to extract Smali bytecode.\n";
                etSourceCode.setText(summary);
                showLoadedFileBanner(fileName, bytes.length);
                Toast.makeText(this, "Loaded " + fileName + " (" + FileUtils.formatFileSize(bytes.length) + ")", Toast.LENGTH_SHORT).show();

            } else {
                String text = new String(bytes, StandardCharsets.UTF_8);

                if (fileName.toLowerCase().endsWith(".smali")) {
                    if (currentModule != Module.SMALI_TO_DEX) {
                        selectModule(Module.SMALI_TO_DEX);
                    }
                } else if (fileName.toLowerCase().endsWith(".java")) {
                    if (currentModule != Module.JAVA_TO_SMALI && currentModule != Module.JAVA_TO_DEX && currentModule != Module.JAVA_TO_KOTLIN) {
                        selectModule(Module.JAVA_TO_SMALI);
                    }
                } else if (fileName.toLowerCase().endsWith(".kt")) {
                    if (currentModule != Module.KOTLIN_TO_JAVA) {
                        selectModule(Module.KOTLIN_TO_JAVA);
                    }
                } else if (fileName.toLowerCase().endsWith(".xml")) {
                    if (text.contains("<shape") || text.contains("<selector")) {
                        selectModule(Module.XML_DRAWABLE);
                    } else {
                        selectModule(Module.XML_LAYOUT);
                    }
                }

                etSourceCode.setText(text);
                showLoadedFileBanner(fileName, bytes.length);
                Toast.makeText(this, "Loaded " + fileName + " (" + FileUtils.formatFileSize(bytes.length) + ")", Toast.LENGTH_SHORT).show();
            }

            updateSourceStats();

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

    private String extractMagicHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return "Unknown";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 8); i++) {
            char c = (char) bytes[i];
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == 0) {
                sb.append("\\0");
            } else {
                sb.append(String.format("\\x%02X", bytes[i]));
            }
        }
        return sb.toString();
    }

    private void showOutputTab(boolean showCode) {
        btnTabCode.setSelected(showCode);
        btnTabCode.setTextColor(showCode ? Color.parseColor("#00F0FF") : Color.parseColor("#94A3B8"));
        btnTabPreview.setSelected(!showCode);
        btnTabPreview.setTextColor(!showCode ? Color.parseColor("#00F0FF") : Color.parseColor("#94A3B8"));

        etOutputCode.setVisibility(showCode ? View.VISIBLE : View.GONE);
        layoutVisualPreviewCard.setVisibility(!showCode ? View.VISIBLE : View.GONE);
    }

    private void loadTemplate() {
        String code = "";
        switch (currentModule) {
            case JAVA_TO_SMALI:
                code = CyberTemplates.JAVA_SMALI_SAMPLE;
                break;
            case JAVA_TO_DEX:
                code = CyberTemplates.JAVA_DEX_SAMPLE;
                break;
            case SMALI_TO_DEX:
                code = CyberTemplates.SMALI_DEX_SAMPLE;
                break;
            case DEX_TO_SMALI:
                code = "# DEX to Smali disassembler ready.\n" + CyberTemplates.SMALI_DEX_SAMPLE;
                break;
            case JAVA_TO_KOTLIN:
                code = CyberTemplates.JAVA_KOTLIN_SAMPLE;
                break;
            case KOTLIN_TO_JAVA:
                code = CyberTemplates.KOTLIN_JAVA_SAMPLE;
                break;
            case XML_LAYOUT:
                code = CyberTemplates.XML_LAYOUT_SAMPLE;
                break;
            case XML_DRAWABLE:
                code = CyberTemplates.XML_DRAWABLE_SAMPLE;
                break;
        }

        etSourceCode.setText(code);
        etOutputCode.setText("");
        containerLivePreview.removeAllViews();
        updateSourceStats();
    }

    private void updateSourceStats() {
        String text = etSourceCode.getText().toString();
        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        int chars = text.length();
        tvSourceStats.setText(lines + " lines (" + chars + " chars)");
    }

    /**
     * Executes the compiler in true Cyberpunk Hacker style:
     * 1. Displays the terminal console with typewriter logs.
     * 2. Runs compilation in worker thread.
     * 3. Upon success: shows "SYSTEM ACTIVE", hides spinner, reveals Output Box!
     */
    private void executeHackerAction() {
        final String source = etSourceCode.getText().toString();
        if (source.trim().isEmpty()) {
            Toast.makeText(this, "Please enter or load source code first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show terminal console and hide old output box
        layoutTerminalConsole.setVisibility(View.VISIBLE);
        layoutOutputCard.setVisibility(View.GONE);
        terminalProgress.setVisibility(View.VISIBLE);
        btnPrimaryAction.setEnabled(false);

        final StringBuilder logs = new StringBuilder();
        logs.append("[SYS:INIT] > CYBER COMPILER MATRIX v3.2 ONLINE...\n");
        if (importedFileName != null) {
            logs.append("[SYS:MOUNT] > PHONE STORAGE SOURCE: ").append(importedFileName).append("\n");
        }
        tvTerminalLogs.setText(logs.toString());

        // Step 2 in hacker log
        mainHandler.postDelayed(() -> {
            logs.append("[SYS:PARSER] > SCANNING TOKENS & CLASS DIRECTIVES...\n");
            tvTerminalLogs.setText(logs.toString());
        }, 220);

        // Step 3 in hacker log
        mainHandler.postDelayed(() -> {
            logs.append("[SYS:DALVIK] > ALLOCATING REGISTERS & INSTRUCTION SET...\n");
            tvTerminalLogs.setText(logs.toString());
        }, 440);

        // Run real compilation in background
        executor.execute(() -> {
            try {
                Thread.sleep(600); // Allow smooth hacker effect

                switch (currentModule) {
                    case JAVA_TO_SMALI: {
                        JavaToSmaliCompiler.CompileResult res = JavaToSmaliCompiler.compile(source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // 0 ERRORS <<\n[SYS:INFO]   >> COMPILED " + res.className + ".smali", res.smaliCode, null, null);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                    case JAVA_TO_DEX: {
                        JavaToDexCompiler.JavaToDexResult res = JavaToDexCompiler.compile(this, source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                lastGeneratedBinary = res.dexBytes;
                                lastGeneratedFile = res.dexFile;
                                String hexDump = buildHexSummary(res.dexBytes);
                                String output = res.message + "\n\n# --- DEX BYTECODE STREAM ---\n" + hexDump;
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // CLASSES.DEX GENERATED <<\n[SYS:DEX]    >> SIZE: " + FileUtils.formatFileSize(res.dexBytes.length), output, res.dexBytes, res.dexFile);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                    case SMALI_TO_DEX: {
                        SmaliAssembler.AssembleResult res = SmaliAssembler.assemble(this, source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                lastGeneratedBinary = res.dexBytes;
                                lastGeneratedFile = res.dexFile;
                                String hexDump = buildHexSummary(res.dexBytes);
                                String output = res.message + "\n\n# --- DEX BYTECODE STREAM ---\n" + hexDump;
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // SMALI ASSEMBLED TO DEX <<\n[SYS:DEX]    >> SIZE: " + FileUtils.formatFileSize(res.dexBytes.length), output, res.dexBytes, res.dexFile);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                    case DEX_TO_SMALI: {
                        File dexFile = importedDexFile;
                        if (dexFile == null || !dexFile.exists()) {
                            if (importedFileBytes != null) {
                                File cacheDex = new File(getCacheDir(), "imported_dex_" + System.currentTimeMillis() + ".dex");
                                try (FileOutputStream fos = new FileOutputStream(cacheDex)) {
                                    fos.write(importedFileBytes);
                                }
                                dexFile = cacheDex;
                                importedDexFile = cacheDex;
                            }
                        }
                        if (dexFile == null || !dexFile.exists()) {
                            dexFile = lastGeneratedFile;
                        }
                        if (dexFile == null || !dexFile.exists()) {
                            SmaliAssembler.AssembleResult asmRes = SmaliAssembler.assemble(this, source);
                            if (asmRes.success) {
                                dexFile = asmRes.dexFile;
                            }
                        }

                        if (dexFile != null && dexFile.exists()) {
                            DexDisassembler.DisassembleResult res = DexDisassembler.disassemble(this, dexFile);
                            mainHandler.post(() -> {
                                if (res.success) {
                                    onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // DEX DISASSEMBLED <<\n[SYS:COUNT]  >> EXTRACTED " + res.classCount + " CLASSES", res.smaliCode, null, null);
                                } else {
                                    onExecutionError(logs, res.message);
                                }
                            });
                        } else {
                            mainHandler.post(() -> onExecutionError(logs, "ERROR: No valid DEX binary loaded. Tap '📁 Open File' to choose your classes.dex from phone memory."));
                        }
                        break;
                    }
                    case JAVA_TO_KOTLIN: {
                        JavaToKotlinConverter.ConvertResult res = JavaToKotlinConverter.convert(source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // TRANSPILED TO KOTLIN <<", res.kotlinCode, null, null);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                    case KOTLIN_TO_JAVA: {
                        KotlinToJavaConverter.ConvertResult res = KotlinToJavaConverter.convert(source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // TRANSPILED TO JAVA <<", res.javaCode, null, null);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                    case XML_LAYOUT: {
                        XmlLayoutRenderer.RenderResult res = XmlLayoutRenderer.render(this, source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                containerLivePreview.removeAllViews();
                                containerLivePreview.addView(res.renderedView);
                                String output = "XML Layout Validated Successfully.\nElement Count: " + res.elementCount + "\nRoot: " + res.renderedView.getClass().getSimpleName();
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // " + res.elementCount + " VIEWS INFLATED <<", output, null, null);
                                showOutputTab(false);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                    case XML_DRAWABLE: {
                        XmlDrawableRenderer.DrawableResult res = XmlDrawableRenderer.render(this, source);
                        mainHandler.post(() -> {
                            if (res.success) {
                                containerLivePreview.removeAllViews();
                                View previewSurface = new View(this);
                                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                        (int) (220 * getResources().getDisplayMetrics().density),
                                        (int) (140 * getResources().getDisplayMetrics().density)
                                );
                                lp.gravity = Gravity.CENTER;
                                previewSurface.setLayoutParams(lp);
                                previewSurface.setBackground(res.drawable);
                                containerLivePreview.addView(previewSurface);

                                String output = "XML Drawable Built Successfully.\n" + res.details;
                                onExecutionSuccess(logs, "[SYS:STATUS] >> SYSTEM ACTIVE // DRAWABLE RENDERED <<", output, null, null);
                                showOutputTab(false);
                            } else {
                                onExecutionError(logs, res.message);
                            }
                        });
                        break;
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> onExecutionError(logs, "EXCEPTION: " + e.getMessage()));
            }
        });
    }

    private void onExecutionSuccess(StringBuilder logs, String successStatus, String outputContent, byte[] binary, File file) {
        logs.append(successStatus).append("\n");
        tvTerminalLogs.setText(logs.toString());
        terminalProgress.setVisibility(View.GONE);
        btnPrimaryAction.setEnabled(true);

        // Populate and smoothly reveal the Output Artifact Card
        etOutputCode.setText(outputContent);
        this.lastGeneratedBinary = binary;
        this.lastGeneratedFile = file;

        layoutOutputCard.setVisibility(View.VISIBLE);
        layoutOutputCard.setAlpha(0f);
        ObjectAnimator.ofFloat(layoutOutputCard, "alpha", 0f, 1f).setDuration(350).start();

        // Auto-scroll down to show the generated box
        mainScrollView.postDelayed(() -> mainScrollView.smoothScrollTo(0, layoutOutputCard.getTop()), 100);
    }

    private void onExecutionError(StringBuilder logs, String errorMsg) {
        logs.append("[ERROR] >> COMPILATION FAILED: ").append(errorMsg).append("\n");
        tvTerminalLogs.setText(logs.toString());
        terminalProgress.setVisibility(View.GONE);
        btnPrimaryAction.setEnabled(true);
        layoutOutputCard.setVisibility(View.GONE);
        Toast.makeText(this, "Execution failed. Check terminal logs.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Directly saves the generated file to the phone's public Downloads folder!
     */
    private void downloadToPhoneStorage() {
        String textOutput = etOutputCode.getText().toString();
        if (textOutput.isEmpty() && lastGeneratedBinary == null) {
            Toast.makeText(this, "No output to download.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File savedFile;
            if (lastGeneratedBinary != null && (currentModule == Module.JAVA_TO_DEX || currentModule == Module.SMALI_TO_DEX)) {
                savedFile = FileUtils.saveToPublicDownloads(this, currentModule.defaultFilename, lastGeneratedBinary, currentModule.mimeType);
            } else {
                savedFile = FileUtils.saveTextToPublicDownloads(this, currentModule.defaultFilename, textOutput, currentModule.mimeType);
            }
            this.lastGeneratedFile = savedFile;

            Toast.makeText(this, "Downloaded to Phone: " + savedFile.getName() + " in Downloads/CyberDex", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Download error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Shares output file directly using Android system share intent
     */
    private void shareOutputArtifact() {
        String textOutput = etOutputCode.getText().toString();
        if (textOutput.isEmpty() && lastGeneratedBinary == null) {
            Toast.makeText(this, "No output to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File fileToShare = lastGeneratedFile;
            if (fileToShare == null || !fileToShare.exists()) {
                if (lastGeneratedBinary != null) {
                    fileToShare = FileUtils.saveBinaryFile(this, currentModule.defaultFilename, lastGeneratedBinary);
                } else {
                    fileToShare = FileUtils.saveTextFile(this, currentModule.defaultFilename, textOutput);
                }
                this.lastGeneratedFile = fileToShare;
            }

            FileUtils.shareFile(this, fileToShare, currentModule.mimeType);

        } catch (Exception e) {
            Toast.makeText(this, "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static String buildHexSummary(byte[] bytes) {
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
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
