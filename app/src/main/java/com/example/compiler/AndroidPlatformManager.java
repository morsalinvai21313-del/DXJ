package com.example.compiler;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Unified Android Platform Manager & Framework API Resolver.
 * Centralizes android.jar resolution, API classpath provisioning, framework symbol identification,
 * and API level compatibility across all compilers, assemblers, disassemblers, transpilers, and XML engines.
 */
public final class AndroidPlatformManager {

    private static final String TAG = "AndroidPlatformManager";
    public static final int DEFAULT_MIN_API = 24;
    public static final int TARGET_API = 36;

    private static volatile File resolvedAndroidJar = null;
    private static volatile Set<String> frameworkClassSet = null;
    private static final Object lock = new Object();
    private static final ExecutorService initExecutor = Executors.newSingleThreadExecutor();

    private AndroidPlatformManager() {}

    /**
     * Pre-extracts and initializes android.jar asynchronously on app launch.
     */
    public static void initializeAsync(Context context, Runnable onComplete) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();
        initExecutor.execute(() -> {
            try {
                File jar = getAndroidJar(appCtx);
                if (jar != null && jar.exists()) {
                    Log.i(TAG, "AndroidPlatformManager ready with jar: " + jar.getAbsolutePath() + " (" + jar.length() + " bytes)");
                    getFrameworkClassIndex(appCtx);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Background android.jar initialization note: " + t.getMessage());
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    /**
     * Resolves and provides the canonical android.jar file.
     * Searches:
     * 1. Extracted android.jar in app internal filesDir (offline phone device execution)
     * 2. Assets extraction (android.jar bundled in APK)
     * 3. Local Android SDK platforms paths (development / test execution)
     * 4. System framework jar fallback
     */
    public static File getAndroidJar(Context context) {
        if (resolvedAndroidJar != null && resolvedAndroidJar.exists() && resolvedAndroidJar.length() > 5000000) {
            return resolvedAndroidJar;
        }

        synchronized (lock) {
            if (resolvedAndroidJar != null && resolvedAndroidJar.exists() && resolvedAndroidJar.length() > 5000000) {
                return resolvedAndroidJar;
            }

            // 1. Check if already extracted to app internal filesDir
            if (context != null) {
                File filesDir = context.getFilesDir();
                if (filesDir != null) {
                    File localJar = new File(filesDir, "android.jar");
                    if (localJar.exists() && localJar.length() > 5000000) {
                        resolvedAndroidJar = localJar;
                        return resolvedAndroidJar;
                    }

                    // 2. Extract bundled android.jar from APK assets safely (atomic write via tmp)
                    File tmpJar = new File(filesDir, "android.jar.tmp");
                    try (InputStream in = context.getAssets().open("android.jar");
                         FileOutputStream out = new FileOutputStream(tmpJar)) {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.flush();
                        if (tmpJar.exists() && tmpJar.length() > 5000000) {
                            if (localJar.exists()) {
                                localJar.delete();
                            }
                            if (tmpJar.renameTo(localJar)) {
                                resolvedAndroidJar = localJar;
                                Log.i(TAG, "Successfully extracted bundled android.jar (" + localJar.length() + " bytes)");
                                return resolvedAndroidJar;
                            } else if (tmpJar.exists() && tmpJar.length() > 5000000) {
                                resolvedAndroidJar = tmpJar;
                                return resolvedAndroidJar;
                            }
                        }
                    } catch (Throwable t) {
                        Log.d(TAG, "Assets android.jar extraction skipped: " + t.getMessage());
                        if (tmpJar.exists()) {
                            tmpJar.delete();
                        }
                    }
                }
            }

            // 3. Check Android SDK paths in local development/test container
            String[] sdkLocations = {
                    "/opt/android/sdk/platforms/android-36/android.jar",
                    "/opt/android/sdk/platforms/android-36.1/android.jar",
                    "/opt/android-sdk-linux/platforms/android-34/android.jar",
                    System.getenv("ANDROID_HOME") != null ? System.getenv("ANDROID_HOME") + "/platforms/android-36/android.jar" : null,
                    System.getenv("ANDROID_HOME") != null ? System.getenv("ANDROID_HOME") + "/platforms/android-34/android.jar" : null,
                    System.getenv("ANDROID_SDK_ROOT") != null ? System.getenv("ANDROID_SDK_ROOT") + "/platforms/android-36/android.jar" : null
            };

            for (String loc : sdkLocations) {
                if (loc != null) {
                    File f = new File(loc);
                    if (f.exists() && f.isFile() && f.length() > 5000000) {
                        resolvedAndroidJar = f;
                        return resolvedAndroidJar;
                    }
                }
            }

            // 4. System framework jar fallback on Android OS devices
            File sysFramework = new File("/system/framework/framework.jar");
            if (sysFramework.exists() && sysFramework.canRead() && sysFramework.length() > 1000000) {
                resolvedAndroidJar = sysFramework;
                return resolvedAndroidJar;
            }

            return null;
        }
    }

    /**
     * Returns the bootclasspath / classpath string for compilers.
     */
    public static String getBootClasspath(Context context) {
        File jar = getAndroidJar(context);
        if (jar != null) {
            return jar.getAbsolutePath();
        }

        // Fallback multi-jar bootclasspath on physical devices
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
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Checks if a class name or type descriptor belongs to the Android Framework or Java Core API.
     * Examples: "android.app.Activity", "android/os/Bundle", "Landroid/view/View;", "java.util.List"
     */
    public static boolean isFrameworkClass(Context context, String className) {
        if (className == null || className.trim().isEmpty()) return false;
        String normalized = normalizeClassName(className);

        // Fast-path prefix check
        if (normalized.startsWith("android.") ||
            normalized.startsWith("java.") ||
            normalized.startsWith("javax.") ||
            normalized.startsWith("dalvik.") ||
            normalized.startsWith("org.xml.") ||
            normalized.startsWith("org.w3c.") ||
            normalized.startsWith("org.json.")) {
            return true;
        }

        // Verify against framework index if available
        Set<String> index = getFrameworkClassIndex(context);
        return index.contains(normalized);
    }

    /**
     * Converts any class reference (e.g. "android.os.Bundle" or "android/os/Bundle")
     * into a standard Dalvik Smali Type Descriptor ("Landroid/os/Bundle;").
     */
    public static String toSmaliDescriptor(String className) {
        if (className == null || className.isEmpty()) return "V";
        String clean = className.trim();
        if (clean.startsWith("L") && clean.endsWith(";")) {
            return clean;
        }
        if (clean.endsWith("[]")) {
            return "[" + toSmaliDescriptor(clean.substring(0, clean.length() - 2));
        }
        switch (clean) {
            case "void": return "V";
            case "boolean": return "Z";
            case "byte": return "B";
            case "short": return "S";
            case "char": return "C";
            case "int": return "I";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            default:
                return "L" + clean.replace('.', '/') + ";";
        }
    }

    /**
     * Converts a Smali descriptor ("Landroid/view/View;") into a standard Java class name ("android.view.View").
     */
    public static String fromSmaliDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) return "";
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }
        if (descriptor.startsWith("[")) {
            return fromSmaliDescriptor(descriptor.substring(1)) + "[]";
        }
        switch (descriptor) {
            case "V": return "void";
            case "Z": return "boolean";
            case "B": return "byte";
            case "S": return "short";
            case "C": return "char";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            default: return descriptor;
        }
    }

    /**
     * Validates API Level Compatibility for a target API level.
     */
    public static boolean isApiCompatible(int apiLevel) {
        return apiLevel >= DEFAULT_MIN_API && apiLevel <= TARGET_API;
    }

    private static Set<String> getFrameworkClassIndex(Context context) {
        if (frameworkClassSet != null) return frameworkClassSet;

        synchronized (lock) {
            if (frameworkClassSet != null) return frameworkClassSet;

            Set<String> set = new HashSet<>();
            File jar = getAndroidJar(context);
            if (jar != null && jar.exists() && jar.isFile()) {
                try (JarFile jf = new JarFile(jar)) {
                    Enumeration<JarEntry> entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".class")) {
                            String fqcn = name.substring(0, name.length() - 6).replace('/', '.');
                            set.add(fqcn);
                        }
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "Jar indexing error: " + t.getMessage());
                }
            }
            frameworkClassSet = Collections.unmodifiableSet(set);
            return frameworkClassSet;
        }
    }

    private static String normalizeClassName(String name) {
        String clean = name.trim();
        if (clean.startsWith("L") && clean.endsWith(";")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.replace('/', '.');
    }
}
