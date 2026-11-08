package com.example.util;

public final class CyberTemplates {

    private CyberTemplates() {}

    public static final String JAVA_SMALI_SAMPLE =
            "package com.morsalin.djx.cyberdemo;\n\n" +
            "public class CyberDemo {\n" +
            "    public static final String TAG = \"CYBER_DEX\";\n" +
            "    private int counter = 100;\n\n" +
            "    public static String getStatus() {\n" +
            "        return \"SYSTEM OPERATIONAL\";\n" +
            "    }\n\n" +
            "    public static int compute(int a, int b) {\n" +
            "        return a + b;\n" +
            "    }\n\n" +
            "    public void runDiagnostics() {\n" +
            "        System.out.println(\"Cyber core diagnostic check passed.\");\n" +
            "    }\n" +
            "}\n";

    public static final String JAVA_DEX_SAMPLE =
            "package com.morsalin.djx.bytecodeunit;\n\n" +
            "public class BytecodeUnit {\n" +
            "    public static final String ENGINE = \"CYBER_DEX_v1\";\n\n" +
            "    public static int execute(int seed) {\n" +
            "        return seed * 2;\n" +
            "    }\n\n" +
            "    public static String verify() {\n" +
            "        return \"DEX BYTECODE VERIFIED\";\n" +
            "    }\n" +
            "}\n";

    public static final String SMALI_DEX_SAMPLE =
            ".class public Lcom/morsalin/djx/cybernode/CyberNode;\n" +
            ".super Ljava/lang/Object;\n" +
            ".source \"CyberNode.java\"\n\n" +
            ".field public static final NODE_ID:Ljava/lang/String; = \"NODE_77\"\n\n" +
            ".method public constructor <init>()V\n" +
            "    .registers 1\n" +
            "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
            "    return-void\n" +
            ".end method\n\n" +
            ".method public static getStatus()Ljava/lang/String;\n" +
            "    .registers 1\n" +
            "    const-string v0, \"NODE_ONLINE_CYBER_DEX\"\n" +
            "    return-object v0\n" +
            ".end method\n\n" +
            ".method public static getVersion()I\n" +
            "    .registers 1\n" +
            "    const/4 v0, 0x4\n" +
            "    return v0\n" +
            ".end method\n";

    public static final String JAVA_KOTLIN_SAMPLE =
            "package com.morsalin.djx.securitymanager;\n\n" +
            "import java.util.List;\n\n" +
            "public class SecurityManager {\n" +
            "    private String token = \"auth_key_99\";\n" +
            "    private int level = 5;\n\n" +
            "    public String getToken() {\n" +
            "        return this.token;\n" +
            "    }\n\n" +
            "    public void setToken(String token) {\n" +
            "        this.token = token;\n" +
            "    }\n\n" +
            "    public boolean isAuthorized(int requiredLevel) {\n" +
            "        return this.level >= requiredLevel;\n" +
            "    }\n" +
            "}\n";

    public static final String KOTLIN_JAVA_SAMPLE =
            "package com.morsalin.djx.moduleconfig\n\n" +
            "data class ModuleConfig(\n" +
            "    val id: Int,\n" +
            "    val moduleName: String,\n" +
            "    val active: Boolean\n" +
            ")\n";

    public static final String XML_LAYOUT_SAMPLE =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"wrap_content\"\n" +
            "    android:orientation=\"vertical\"\n" +
            "    android:padding=\"20dp\"\n" +
            "    android:background=\"#111827\">\n\n" +
            "    <TextView\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"CYBER COMPILER ACTIVE\"\n" +
            "        android:textColor=\"#00F0FF\"\n" +
            "        android:textSize=\"20sp\"\n" +
            "        android:textStyle=\"bold\" />\n\n" +
            "    <TextView\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Hardware Acceleration: Enabled\"\n" +
            "        android:textColor=\"#00FF88\"\n" +
            "        android:textSize=\"14sp\"\n" +
            "        android:padding=\"8dp\" />\n\n" +
            "    <Button\n" +
            "        android:layout_width=\"match_parent\"\n" +
            "        android:layout_height=\"48dp\"\n" +
            "        android:text=\"EXECUTE WORKSPACE\"\n" +
            "        android:textColor=\"#070A10\"\n" +
            "        android:background=\"#00F0FF\" />\n" +
            "</LinearLayout>\n";

    public static final String XML_DRAWABLE_SAMPLE =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:shape=\"rectangle\">\n" +
            "    <gradient\n" +
            "        android:angle=\"45\"\n" +
            "        android:startColor=\"#0B101D\"\n" +
            "        android:centerColor=\"#111827\"\n" +
            "        android:endColor=\"#1E293B\" />\n" +
            "    <corners android:radius=\"16dp\" />\n" +
            "    <stroke\n" +
            "        android:width=\"2dp\"\n" +
            "        android:color=\"#00F0FF\" />\n" +
            "</shape>\n";
}
