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
import java.io.InputStream;
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
    private static String cachedClasspath = null;

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
     * Supports complex Java, generics, lambdas, interfaces, and multi-class files.
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

            // Prepare source files (handles multi-class source code gracefully)
            List<File> javaFiles = prepareJavaSources(srcDir, javaSource);
            if (javaFiles.isEmpty()) {
                return new JavaToDexResult(false, null, null, "Could not prepare Java source files.", 0, 0, 0, "", null);
            }

            // Step 1: ECJ Java Compilation
            StringWriter ecjErr = new StringWriter();
            StringWriter ecjOut = new StringWriter();
            PrintWriter errWriter = new PrintWriter(ecjErr);
            PrintWriter outWriter = new PrintWriter(ecjOut);

            List<String> ecjArgs = new ArrayList<>();
            ecjArgs.add("-1.8"); // Java 8 compatibility (lambdas, streams, default interface methods)
            ecjArgs.add("-nowarn");
            ecjArgs.add("-proc:none"); // Disable annotation processing on mobile
            ecjArgs.add("-d");
            ecjArgs.add(binDir.getAbsolutePath());
            ecjArgs.add("-sourcepath");
            ecjArgs.add(srcDir.getAbsolutePath());

            // Android Framework Boot Classpath integration via AndroidPlatformManager
            File androidJar = AndroidPlatformManager.getAndroidJar(context);
            if (androidJar != null && androidJar.exists()) {
                ecjArgs.add("-bootclasspath");
                ecjArgs.add(androidJar.getAbsolutePath());
                ecjArgs.add("-classpath");
                ecjArgs.add(androidJar.getAbsolutePath());
            } else {
                String bootClasspath = AndroidPlatformManager.getBootClasspath(context);
                if (bootClasspath != null && !bootClasspath.isEmpty()) {
                    ecjArgs.add("-classpath");
                    ecjArgs.add(bootClasspath);
                }
            }

            for (File jf : javaFiles) {
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
     * Resolves the compile classpath:
     * 1. Bundled android.jar extracted from assets
     * 2. Local Android SDK android.jar
     * 3. System framework jars (/system/framework/framework.jar)
     */
    public static synchronized String resolveClasspath(Context context) {
        if (cachedClasspath != null && new File(cachedClasspath).exists()) {
            return cachedClasspath;
        }

        // 1. Check if extracted android.jar already exists in internal storage
        if (context != null) {
            File filesDir = context.getFilesDir();
            if (filesDir != null) {
                File localJar = new File(filesDir, "android.jar");
                if (localJar.exists() && localJar.length() > 5000000) {
                    cachedClasspath = localJar.getAbsolutePath();
                    return cachedClasspath;
                }
                // Try extracting bundled android.jar from assets
                try (InputStream in = context.getAssets().open("android.jar");
                     FileOutputStream out = new FileOutputStream(localJar)) {
                    byte[] buf = new byte[32768];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                    out.flush();
                    if (localJar.exists() && localJar.length() > 5000000) {
                        cachedClasspath = localJar.getAbsolutePath();
                        return cachedClasspath;
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "Assets android.jar not loaded: " + t.getMessage());
                }
            }
        }

        // 2. Check Android SDK paths in local development/container environment
        String[] sdkPaths = {
                "/opt/android/sdk/platforms/android-36/android.jar",
                "/opt/android/sdk/platforms/android-36.1/android.jar",
                "/opt/android-sdk-linux/platforms/android-34/android.jar",
                System.getenv("ANDROID_HOME") != null ? System.getenv("ANDROID_HOME") + "/platforms/android-36/android.jar" : null,
                System.getenv("ANDROID_SDK_ROOT") != null ? System.getenv("ANDROID_SDK_ROOT") + "/platforms/android-36/android.jar" : null
        };
        for (String p : sdkPaths) {
            if (p != null) {
                File f = new File(p);
                if (f.exists() && f.isFile()) {
                    cachedClasspath = f.getAbsolutePath();
                    return cachedClasspath;
                }
            }
        }

        // 3. Fallback to device system framework on Android OS
        String[] sysJars = {
                "/system/framework/framework.jar",
                "/system/framework/core-oj.jar",
                "/apex/com.android.art/javalib/core-oj.jar",
                "/system/framework/core-libart.jar"
        };
        StringBuilder sb = new StringBuilder();
        for (String sj : sysJars) {
            File f = new File(sj);
            if (f.exists() && f.canRead()) {
                if (sb.length() > 0) sb.append(File.pathSeparator);
                sb.append(f.getAbsolutePath());
            }
        }
        if (sb.length() > 0) {
            cachedClasspath = sb.toString();
            return cachedClasspath;
        }

        return null;
    }

    /**
     * Splits multiple top-level classes into their respective .java files in the package directory.
     * Prevents the classic "The public type X must be defined in its own file" compiler error.
     */
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

        // Extract imports
        StringBuilder importsSb = new StringBuilder();
        Matcher importMatcher = Pattern.compile("(?m)^\\s*import\\s+[a-zA-Z0-9_.*]+;").matcher(javaSource);
        while (importMatcher.find()) {
            importsSb.append(importMatcher.group()).append("\n");
        }
        String importsStr = importsSb.toString();

        // Find all class/interface/enum blocks
        Pattern classDeclPattern = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:final\\s+|abstract\\s+|static\\s+)?(?:class|interface|enum|@interface)\\s+([a-zA-Z0-9_]+)");
        Matcher classMatcher = classDeclPattern.matcher(javaSource);

        List<Integer> startPositions = new ArrayList<>();
        List<String> classNames = new ArrayList<>();

        while (classMatcher.find()) {
            startPositions.add(classMatcher.start());
            classNames.add(classMatcher.group(1));
        }

        if (classNames.size() <= 1) {
            // Single class: write directly
            String primaryName = !classNames.isEmpty() ? classNames.get(0) : "Main";
            File singleFile = new File(pkgDir, primaryName + ".java");
            try (FileOutputStream fos = new FileOutputStream(singleFile)) {
                fos.write(javaSource.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            files.add(singleFile);
            return files;
        }

        // Multiple classes: split cleanly
        for (int i = 0; i < startPositions.size(); i++) {
            int start = startPositions.get(i);
            int end = (i + 1 < startPositions.size()) ? startPositions.get(i + 1) : javaSource.length();
            String classBody = javaSource.substring(start, end).trim();
            String name = classNames.get(i);

            StringBuilder fullClassSource = new StringBuilder();
            if (!pkg.isEmpty()) {
                fullClassSource.append("package ").append(pkg).append(";\n\n");
            }
            if (!importsStr.isEmpty()) {
                fullClassSource.append(importsStr).append("\n");
            }
            fullClassSource.append(classBody).append("\n");

            File classFile = new File(pkgDir, name + ".java");
            try (FileOutputStream fos = new FileOutputStream(classFile)) {
                fos.write(fullClassSource.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            files.add(classFile);
        }

        return files;
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

    private static String cleanEcjOutput(String rawOutput, String srcPath) {
        String clean = rawOutput.replace(srcPath, "");
        if (clean.startsWith(File.separator)) {
            clean = clean.substring(1);
        }
        return clean;
    }
}
