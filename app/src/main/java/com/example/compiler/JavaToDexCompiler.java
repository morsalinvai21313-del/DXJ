package com.example.compiler;

import android.content.Context;
import android.util.Log;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.example.util.FileUtils;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaToDexCompiler {

    private static final String TAG = "JavaToDexCompiler";

    public static class JavaToDexResult {
        public final boolean success;
        public final File dexFile;
        public final byte[] dexBytes;
        public final String message;
        public final int classCount;
        public final int methodCount;
        public final int fieldCount;
        public final String sha256;
        public final List<String> classNames;

        public JavaToDexResult(boolean success, File dexFile, byte[] dexBytes, String message,
                               int classCount, int methodCount, int fieldCount, String sha256, List<String> classNames) {
            this.success = success;
            this.dexFile = dexFile;
            this.dexBytes = dexBytes;
            this.message = message;
            this.classCount = classCount;
            this.methodCount = methodCount;
            this.fieldCount = fieldCount;
            this.sha256 = sha256;
            this.classNames = classNames != null ? classNames : new ArrayList<>();
        }
    }

    /**
     * Compiles Java source into JVM .class bytecode using ECJ,
     * then transforms bytecode into an Android classes.dex using D8.
     * Supports complex Java, generics, lambdas, interfaces, inner classes, multi-class files,
     * raw method snippets, auto-import resolution, and AndroidX stubs.
     */
    public static JavaToDexResult compile(Context context, String javaSource) {
        if (javaSource == null || javaSource.trim().isEmpty()) {
            return new JavaToDexResult(false, null, null, "Java source code cannot be empty.", 0, 0, 0, "", null);
        }

        File baseTempDir = null;
        try {
            File parentDir = context != null && context.getCacheDir() != null ?
                    context.getCacheDir() :
                    new File(System.getProperty("java.io.tmpdir", "/tmp"));

            baseTempDir = new File(parentDir, "j2dex_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
            if (!baseTempDir.exists()) {
                baseTempDir.mkdirs();
            }

            File srcDir = new File(baseTempDir, "src");
            File binDir = new File(baseTempDir, "bin");
            File dexDir = new File(baseTempDir, "dex");
            srcDir.mkdirs();
            binDir.mkdirs();
            dexDir.mkdirs();

            // Normalize source: wrap raw snippets and auto-inject standard imports if missing
            String processedSource = normalizeJavaSource(javaSource);

            // Generate AndroidX / Support library stubs if referenced by user code
            provisionFrameworkStubs(srcDir, processedSource);

            // Prepare source files with robust brace-aware top-level class detection
            List<File> javaFiles = prepareJavaSources(srcDir, processedSource);
            if (javaFiles.isEmpty()) {
                return new JavaToDexResult(false, null, null, "Could not prepare Java source files.", 0, 0, 0, "", null);
            }

            // Detect Java version compliance (supports var in Java 11)
            String complianceVersion = processedSource.contains("var ") ? "-11" : "-1.8";

            // Step 1: ECJ Java Compilation
            StringWriter ecjErr = new StringWriter();
            StringWriter ecjOut = new StringWriter();
            PrintWriter errWriter = new PrintWriter(ecjErr);
            PrintWriter outWriter = new PrintWriter(ecjOut);

            List<String> ecjArgs = new ArrayList<>();
            ecjArgs.add(complianceVersion);
            ecjArgs.add("-nowarn");
            ecjArgs.add("-proc:none"); // Disable annotation processing on mobile
            ecjArgs.add("-encoding");
            ecjArgs.add("UTF-8");
            ecjArgs.add("-d");
            ecjArgs.add(binDir.getAbsolutePath());
            ecjArgs.add("-sourcepath");
            ecjArgs.add(srcDir.getAbsolutePath());

            // Android Framework Boot Classpath integration via AndroidPlatformManager
            File androidJar = AndroidPlatformManager.getAndroidJar(context);
            if (androidJar != null && androidJar.exists()) {
                if ("-1.8".equals(complianceVersion)) {
                    ecjArgs.add("-bootclasspath");
                    ecjArgs.add(androidJar.getAbsolutePath());
                }
                ecjArgs.add("-classpath");
                ecjArgs.add(androidJar.getAbsolutePath());
            } else {
                String bootClasspath = AndroidPlatformManager.getBootClasspath(context);
                if (bootClasspath != null && !bootClasspath.isEmpty()) {
                    if ("-1.8".equals(complianceVersion)) {
                        ecjArgs.add("-bootclasspath");
                        ecjArgs.add(bootClasspath);
                    }
                    ecjArgs.add("-classpath");
                    ecjArgs.add(bootClasspath);
                }
            }

            // Add all source files (including generated stubs)
            List<File> allSources = new ArrayList<>();
            collectSourceFiles(srcDir, allSources);
            for (File jf : allSources) {
                ecjArgs.add(jf.getAbsolutePath());
            }

            boolean ecjSuccess = BatchCompiler.compile(ecjArgs.toArray(new String[0]), outWriter, errWriter, null);

            String ecjErrorOutput = ecjErr.toString().trim();
            if (!ecjSuccess) {
                String cleanError = ecjErrorOutput.isEmpty() ?
                        "ECJ Compilation failed. Please verify Java syntax, imports, and types." :
                        cleanEcjOutput(ecjErrorOutput, srcDir.getAbsolutePath());
                return new JavaToDexResult(false, null, null, "Java Compilation Error:\n" + cleanError, 0, 0, 0, "", null);
            }

            // Collect generated .class files
            List<File> classFiles = new ArrayList<>();
            collectClassFiles(binDir, classFiles);

            if (classFiles.isEmpty()) {
                return new JavaToDexResult(false, null, null, "No JVM .class files were produced by compiler.", 0, 0, 0, "", null);
            }

            // Step 2: D8 DEX Compilation with Android Framework library resolution
            File outDex = new File(dexDir, "classes.dex");
            try {
                D8Command.Builder d8Builder = D8Command.builder();
                for (File cf : classFiles) {
                    d8Builder.addProgramFiles(cf.toPath());
                }
                d8Builder.setOutput(dexDir.toPath(), OutputMode.DexIndexed);
                d8Builder.setMinApiLevel(AndroidPlatformManager.DEFAULT_MIN_API);

                if (androidJar != null && androidJar.exists()) {
                    d8Builder.addLibraryFiles(androidJar.toPath());
                }

                D8.run(d8Builder.build());
            } catch (Throwable d8Ex) {
                Log.w(TAG, "D8 compilation error: " + d8Ex.getMessage());
                return new JavaToDexResult(false, null, null, "D8 DEX Conversion Failed: " + d8Ex.getMessage(), 0, 0, 0, "", null);
            }

            if (!outDex.exists() || outDex.length() == 0) {
                return new JavaToDexResult(false, null, null, "D8 finished without producing classes.dex.", 0, 0, 0, "", null);
            }

            byte[] dexBytes = FileUtils.readBytes(outDex);

            // Step 3: Validate generated DEX structure via dexlib2
            DexBackedDexFile dexBacked = DexFileFactory.loadDexFile(outDex, Opcodes.getDefault());
            int classCount = dexBacked.getClasses().size();
            int methodCount = 0;
            int fieldCount = 0;
            List<String> classNames = new ArrayList<>();

            for (ClassDef cd : dexBacked.getClasses()) {
                classNames.add(cd.getType());
                for (Object ignored : cd.getMethods()) methodCount++;
                for (Object ignored : cd.getFields()) fieldCount++;
            }

            // Calculate SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(dexBytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }

            StringBuilder summary = new StringBuilder();
            summary.append("SUCCESS: Java source successfully compiled to Dalvik DEX!\n")
                    .append("• Compiler Pipeline: Eclipse ECJ 3.26.0 -> Android D8 8.2.33\n")
                    .append("• Total Classes: ").append(classCount).append("\n")
                    .append("• Total Methods: ").append(methodCount).append("\n")
                    .append("• Total Fields: ").append(fieldCount).append("\n")
                    .append("• Binary Size: ").append(FileUtils.formatFileSize(dexBytes.length)).append(" (").append(dexBytes.length).append(" bytes)\n")
                    .append("• SHA-256: ").append(hex).append("\n\n")
                    .append("Classes in Generated DEX:\n");
            for (String cn : classNames) {
                summary.append("  • ").append(cn).append("\n");
            }

            return new JavaToDexResult(
                    true,
                    outDex,
                    dexBytes,
                    summary.toString(),
                    classCount,
                    methodCount,
                    fieldCount,
                    hex.toString(),
                    classNames
            );

        } catch (Throwable t) {
            return new JavaToDexResult(false, null, null, "Compilation Exception: " + t.getMessage(), 0, 0, 0, "", null);
        }
    }

    /**
     * Normalizes Java code: wraps bare snippets in a class and injects standard Android & Java packages.
     */
    private static String normalizeJavaSource(String source) {
        String trimmed = source.trim();

        // Check if there is any class/interface/enum/record declaration
        Pattern classPattern = Pattern.compile("(?m)^\\s*(?:(?:public|private|protected|abstract|final|static|sealed|non-sealed)\\s+)*(class|interface|enum|record|@interface)\\s+[a-zA-Z0-9_]+");
        boolean hasClass = classPattern.matcher(trimmed).find();

        StringBuilder sb = new StringBuilder();

        // Auto-inject missing standard imports for Android & Java core APIs
        boolean hasPackage = trimmed.matches("(?s)^\\s*package\\s+[a-zA-Z0-9_.]+\\s*;.*");
        String pkgLine = "";
        String body = trimmed;
        if (hasPackage) {
            int semi = trimmed.indexOf(';');
            pkgLine = trimmed.substring(0, semi + 1) + "\n\n";
            body = trimmed.substring(semi + 1).trim();
        }

        sb.append(pkgLine);

        // Core Android & Java imports (safe wildcards)
        if (!body.contains("import android.app.")) sb.append("import android.app.*;\n");
        if (!body.contains("import android.content.")) sb.append("import android.content.*;\n");
        if (!body.contains("import android.content.res.")) sb.append("import android.content.res.*;\n");
        if (!body.contains("import android.content.pm.")) sb.append("import android.content.pm.*;\n");
        if (!body.contains("import android.os.")) sb.append("import android.os.*;\n");
        if (!body.contains("import android.view.")) sb.append("import android.view.*;\n");
        if (!body.contains("import android.view.animation.")) sb.append("import android.view.animation.*;\n");
        if (!body.contains("import android.widget.")) sb.append("import android.widget.*;\n");
        if (!body.contains("import android.graphics.")) sb.append("import android.graphics.*;\n");
        if (!body.contains("import android.net.")) sb.append("import android.net.*;\n");
        if (!body.contains("import android.util.")) sb.append("import android.util.*;\n");
        if (!body.contains("import java.util.")) sb.append("import java.util.*;\n");
        if (!body.contains("import java.io.")) sb.append("import java.io.*;\n");
        sb.append("\n");

        if (!hasClass) {
            // Snippet mode: wrap in a public class
            sb.append("public class MainModule {\n\n");
            // Check if user provided method definitions or just statements
            boolean hasMethod = body.contains("void ") || body.contains("int ") || body.contains("String ") ||
                    body.contains("public ") || body.contains("private ") || body.contains("protected ");
            if (hasMethod && body.contains("{")) {
                sb.append(body).append("\n");
            } else {
                sb.append("    public static void run() {\n");
                sb.append("        ").append(body.replace("\n", "\n        ")).append("\n");
                sb.append("    }\n");
            }
            sb.append("}\n");
        } else {
            sb.append(body);
        }

        return sb.toString();
    }

    /**
     * Generates lightweight AndroidX / Jetpack stubs if user code references them,
     * allowing ECJ to compile modern Android code without missing dependency errors.
     */
    private static void provisionFrameworkStubs(File srcDir, String source) {
        if (source.contains("androidx.appcompat.app.AppCompatActivity") || source.contains("AppCompatActivity")) {
            writeStub(srcDir, "androidx/appcompat/app/AppCompatActivity.java",
                    "package androidx.appcompat.app;\npublic class AppCompatActivity extends android.app.Activity {}\n");
        }
        if (source.contains("androidx.appcompat.widget.Toolbar") || source.contains("Toolbar")) {
            writeStub(srcDir, "androidx/appcompat/widget/Toolbar.java",
                    "package androidx.appcompat.widget;\npublic class Toolbar extends android.view.ViewGroup {\n" +
                    "    public Toolbar(android.content.Context c) { super(c); }\n" +
                    "    public void setTitle(CharSequence title) {}\n" +
                    "    public void setSubtitle(CharSequence subtitle) {}\n" +
                    "    @Override protected void onLayout(boolean c, int l, int t, int r, int b) {}\n" +
                    "}\n");
        }
        if (source.contains("androidx.core.view.ViewCompat") || source.contains("ViewCompat")) {
            writeStub(srcDir, "androidx/core/view/ViewCompat.java",
                    "package androidx.core.view;\npublic class ViewCompat {\n" +
                    "    public static void setElevation(android.view.View v, float e) {}\n" +
                    "}\n");
        }
        if (source.contains("androidx.recyclerview.widget.RecyclerView") || source.contains("RecyclerView")) {
            writeStub(srcDir, "androidx/recyclerview/widget/RecyclerView.java",
                    "package androidx.recyclerview.widget;\npublic class RecyclerView extends android.view.ViewGroup {\n" +
                    "    public RecyclerView(android.content.Context c) { super(c); }\n" +
                    "    @Override protected void onLayout(boolean c, int l, int t, int r, int b) {}\n" +
                    "    public static abstract class ViewHolder {\n" +
                    "        public final android.view.View itemView;\n" +
                    "        public ViewHolder(android.view.View v) { this.itemView = v; }\n" +
                    "    }\n" +
                    "    public static abstract class Adapter<VH extends ViewHolder> {\n" +
                    "        public abstract VH onCreateViewHolder(android.view.ViewGroup p, int vt);\n" +
                    "        public abstract void onBindViewHolder(VH holder, int pos);\n" +
                    "        public abstract int getItemCount();\n" +
                    "    }\n" +
                    "    public void setAdapter(Adapter a) {}\n" +
                    "    public void setLayoutManager(Object lm) {}\n" +
                    "}\n");
        }
        if (source.contains("androidx.fragment.app.FragmentActivity") || source.contains("FragmentActivity")) {
            writeStub(srcDir, "androidx/fragment/app/FragmentActivity.java",
                    "package androidx.fragment.app;\npublic class FragmentActivity extends android.app.Activity {}\n");
        }
        if (source.contains("androidx.fragment.app.Fragment") || source.contains("Fragment")) {
            writeStub(srcDir, "androidx/fragment/app/Fragment.java",
                    "package androidx.fragment.app;\npublic class Fragment {}\n");
        }
        if (source.contains("androidx.core.app.ComponentActivity") || source.contains("ComponentActivity")) {
            writeStub(srcDir, "androidx/core/app/ComponentActivity.java",
                    "package androidx.core.app;\npublic class ComponentActivity extends android.app.Activity {}\n");
        }
        if (source.contains("androidx.core.app.ActivityCompat") || source.contains("ActivityCompat")) {
            writeStub(srcDir, "androidx/core/app/ActivityCompat.java",
                    "package androidx.core.app;\npublic class ActivityCompat {}\n");
        }
        if (source.contains("androidx.core.content.ContextCompat") || source.contains("ContextCompat")) {
            writeStub(srcDir, "androidx/core/content/ContextCompat.java",
                    "package androidx.core.content;\npublic class ContextCompat {}\n");
        }
        if (source.contains("NonNull") || source.contains("androidx.annotation.")) {
            writeStub(srcDir, "androidx/annotation/NonNull.java",
                    "package androidx.annotation;\nimport java.lang.annotation.*;\n@Retention(RetentionPolicy.CLASS)\npublic @interface NonNull {}\n");
            writeStub(srcDir, "androidx/annotation/Nullable.java",
                    "package androidx.annotation;\nimport java.lang.annotation.*;\n@Retention(RetentionPolicy.CLASS)\npublic @interface Nullable {}\n");
        }
    }

    private static void writeStub(File srcDir, String relativePath, String content) {
        try {
            File stub = new File(srcDir, relativePath.replace('/', File.separatorChar));
            File parent = stub.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(stub)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private static List<File> prepareJavaSources(File srcDir, String javaSource) {
        List<File> files = new ArrayList<>();

        // Detect package
        String pkg = "";
        Matcher pkgMatcher = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;").matcher(javaSource);
        if (pkgMatcher.find()) {
            pkg = pkgMatcher.group(1).trim();
        }

        File pkgDir = pkg.isEmpty() ? srcDir : new File(srcDir, pkg.replace('.', File.separatorChar));
        pkgDir.mkdirs();

        // Find true top-level class declarations by tracking brace depth
        List<TopLevelClass> topLevelClasses = findTopLevelClasses(javaSource);

        if (topLevelClasses.isEmpty()) {
            File fallback = new File(pkgDir, "MainModule.java");
            try (FileOutputStream fos = new FileOutputStream(fallback)) {
                fos.write(javaSource.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            files.add(fallback);
            return files;
        }

        if (topLevelClasses.size() == 1) {
            String primaryName = topLevelClasses.get(0).name;
            File singleFile = new File(pkgDir, primaryName + ".java");
            try (FileOutputStream fos = new FileOutputStream(singleFile)) {
                fos.write(javaSource.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            files.add(singleFile);
            return files;
        }

        // Multiple top-level classes
        TopLevelClass primaryPublic = null;
        for (TopLevelClass tlc : topLevelClasses) {
            if (tlc.isPublic) {
                primaryPublic = tlc;
                break;
            }
        }
        if (primaryPublic == null) {
            primaryPublic = topLevelClasses.get(0);
        }

        String sanitizedSource = sanitizeSecondaryPublicClasses(javaSource, primaryPublic.startIndex);

        File primaryFile = new File(pkgDir, primaryPublic.name + ".java");
        try (FileOutputStream fos = new FileOutputStream(primaryFile)) {
            fos.write(sanitizedSource.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        files.add(primaryFile);

        return files;
    }

    private static class TopLevelClass {
        final String name;
        final boolean isPublic;
        final int startIndex;

        TopLevelClass(String name, boolean isPublic, int startIndex) {
            this.name = name;
            this.isPublic = isPublic;
            this.startIndex = startIndex;
        }
    }

    private static List<TopLevelClass> findTopLevelClasses(String source) {
        List<TopLevelClass> result = new ArrayList<>();
        int braceDepth = 0;
        boolean inSingleComment = false;
        boolean inMultiComment = false;
        boolean inString = false;
        boolean inChar = false;

        Pattern classPattern = Pattern.compile("(?<![a-zA-Z0-9_])(?:(public)\\s+)?(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*(class|interface|enum|record|@interface)\\s+([a-zA-Z0-9_]+)");

        String[] lines = source.split("\n", -1);
        int charOffset = 0;

        for (String line : lines) {
            if (!inMultiComment && !inString && braceDepth == 0) {
                Matcher m = classPattern.matcher(line);
                if (m.find()) {
                    boolean isPublic = m.group(1) != null;
                    String className = m.group(3);
                    result.add(new TopLevelClass(className, isPublic, charOffset + m.start()));
                }
            }

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';

                if (inSingleComment) continue;
                if (inMultiComment) {
                    if (c == '*' && next == '/') {
                        inMultiComment = false;
                        i++;
                    }
                    continue;
                }
                if (inString) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (inChar) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '\'') {
                        inChar = false;
                    }
                    continue;
                }

                if (c == '/' && next == '/') {
                    inSingleComment = true;
                    break;
                } else if (c == '/' && next == '*') {
                    inMultiComment = true;
                    i++;
                } else if (c == '"') {
                    inString = true;
                } else if (c == '\'') {
                    inChar = true;
                } else if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    if (braceDepth > 0) braceDepth--;
                }
            }

            inSingleComment = false;
            charOffset += line.length() + 1;
        }

        return result;
    }

    private static String sanitizeSecondaryPublicClasses(String source, int primaryClassIndex) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*)public\\s+(?:final\\s+|abstract\\s+|static\\s+)*(class|interface|enum|record)\\s+([a-zA-Z0-9_]+)");
        Matcher matcher = pattern.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            if (matcher.start() != primaryClassIndex) {
                String replacement = matcher.group().replaceFirst("\\bpublic\\s+", "");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void collectClassFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassFiles(f, list);
            } else if (f.getName().endsWith(".class")) {
                list.add(f);
            }
        }
    }

    private static void collectSourceFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectSourceFiles(f, list);
            } else if (f.getName().endsWith(".java")) {
                list.add(f);
            }
        }
    }

    private static String cleanEcjOutput(String rawOutput, String srcPath) {
        String clean = rawOutput.replace(srcPath, "");
        if (clean.startsWith(File.separator)) {
            clean = clean.substring(1);
        }
        return clean;
    }
}
