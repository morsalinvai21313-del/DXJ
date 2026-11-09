package com.example

import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import com.example.compiler.DexDisassembler
import com.example.compiler.JavaToDexCompiler
import com.example.compiler.JavaToKotlinConverter
import com.example.compiler.JavaToSmaliCompiler
import com.example.compiler.KotlinToJavaConverter
import com.example.compiler.SmaliAssembler
import com.example.util.CyberTemplates
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {

  @Test
  fun testSmaliAndBaksmali() {
    val tempDir = File.createTempFile("smali_test", "").apply {
      delete()
      mkdirs()
    }
    val smaliFile = File(tempDir, "CyberNode.smali")
    smaliFile.writeText(CyberTemplates.SMALI_DEX_SAMPLE)

    val dexOutput = File(tempDir, "classes.dex")
    val smaliOptions = SmaliOptions().apply {
      apiLevel = 26
      outputDexFile = dexOutput.absolutePath
      jobs = 1
    }

    val assembleSuccess = Smali.assemble(smaliOptions, smaliFile.absolutePath)
    assertTrue("Smali assemble should succeed", assembleSuccess)
    assertTrue("classes.dex should exist", dexOutput.exists() && dexOutput.length() > 0)

    val disassembleDir = File(tempDir, "disassembled")
    disassembleDir.mkdirs()
    val dexFile = DexFileFactory.loadDexFile(dexOutput, Opcodes.getDefault())
    val baksmaliOptions = BaksmaliOptions().apply {
      apiLevel = 26
    }
    val baksmaliSuccess = Baksmali.disassembleDexFile(dexFile, disassembleDir, 1, baksmaliOptions)
    assertTrue("Baksmali disassemble should succeed", baksmaliSuccess)

    val disassembledSmali = File(disassembleDir, "com/morsalin/djx/cybernode/CyberNode.smali")
    assertTrue("Disassembled file should exist at " + disassembledSmali.absolutePath, disassembledSmali.exists())
    val content = disassembledSmali.readText()
    assertTrue("Should contain getStatus", content.contains("getStatus"))
  }

  @Test
  fun testRealJavaToDexCompiler() {
    val javaCode = """
      package com.test.unit;

      public class Calculator {
          public static int add(int a, int b) {
              return a + b;
          }
          public String greet(String name) {
              return "Hello, " + name;
          }
      }
    """.trimIndent()

    val result = JavaToDexCompiler.compile(null, javaCode)
    assertTrue("JavaToDex compile should succeed: " + result.message, result.success)
    assertNotNull("DEX file must exist", result.dexFile)
    assertTrue("DEX bytes length must be > 0", result.dexBytes != null && result.dexBytes.isNotEmpty())
    assertTrue("Class count should be >= 1", result.classCount >= 1)
  }

  @Test
  fun testJavaToSmaliCompiler() {
    val result = JavaToSmaliCompiler.compile(CyberTemplates.JAVA_SMALI_SAMPLE)
    assertTrue("JavaToSmali compile should succeed: " + result.message, result.success)
    assertTrue("Should contain class directive", result.smaliCode.contains(".class"))
    assertTrue("Should contain method directive", result.smaliCode.contains("getStatus"))
    assertTrue("Should contain compute method", result.smaliCode.contains("compute"))
  }

  @Test
  fun testRoundTripSmaliToDexToSmali() {
    val asmResult = SmaliAssembler.assemble(null, CyberTemplates.SMALI_DEX_SAMPLE)
    assertTrue("Smali assemble should succeed: " + asmResult.message, asmResult.success)
    assertNotNull("DEX file must exist", asmResult.dexFile)

    val disResult = DexDisassembler.disassemble(null, asmResult.dexFile)
    assertTrue("Dex disassemble should succeed: " + disResult.message, disResult.success)
    assertTrue("Should contain getStatus in disassembled smali", disResult.smaliCode.contains("getStatus"))
    assertTrue("Class count should match", disResult.classCount == asmResult.classCount)
  }

  @Test
  fun testJavaToKotlinConverter() {
    val result = JavaToKotlinConverter.convert(CyberTemplates.JAVA_KOTLIN_SAMPLE)
    assertTrue("JavaToKotlin should succeed: " + result.message, result.success)
    assertTrue("Should contain class SecurityManager", result.kotlinCode.contains("class SecurityManager"))
    assertTrue("Should contain fun isAuthorized", result.kotlinCode.contains("fun isAuthorized"))
  }

  @Test
  fun testKotlinToJavaConverter() {
    val result = KotlinToJavaConverter.convert(CyberTemplates.KOTLIN_JAVA_SAMPLE)
    assertTrue("KotlinToJava should succeed: " + result.message, result.success)
    assertTrue("Should contain public class ModuleConfig", result.javaCode.contains("public class ModuleConfig"))
    assertTrue("Should contain constructor", result.javaCode.contains("public ModuleConfig("))
    assertTrue("Should generate equals", result.javaCode.contains("boolean equals"))
    assertTrue("Should generate hashCode", result.javaCode.contains("int hashCode"))
    assertTrue("Should generate toString", result.javaCode.contains("String toString"))
  }

  @Test
  fun testXmlLayoutValidation() {
    val result = com.example.compiler.XmlLayoutRenderer.render(null, CyberTemplates.XML_LAYOUT_SAMPLE)
    // When context is null in JVM unit test without Robolectric, renderer catches and reports cleanly or succeeds
    assertNotNull("XML render result must not be null", result)

    // Test malformed XML detection
    val malformedXml = "<LinearLayout><TextView android:text='broken'</LinearLayout>"
    val errorResult = com.example.compiler.XmlLayoutRenderer.render(null, malformedXml)
    assertTrue("Malformed XML should fail validation", !errorResult.success)
    assertTrue("Error message should mention XML Error", errorResult.message.contains("XML Error"))
  }

  @Test
  fun testXmlDrawableValidation() {
    val result = com.example.compiler.XmlDrawableRenderer.render(null, CyberTemplates.XML_DRAWABLE_SAMPLE)
    assertNotNull("Drawable result must not be null", result)

    // Test unsupported tag
    val invalidTagXml = "<vector xmlns:android='http://schemas.android.com/apk/res/android'></vector>"
    val invalidResult = com.example.compiler.XmlDrawableRenderer.render(null, invalidTagXml)
    assertTrue("Unsupported root tag should report failure", !invalidResult.success)
  }

  @Test
  fun testComplexMultiClassJavaCompilation() {
    val complexJava = """
      package com.complex.enterprise;

      import java.util.*;
      import java.io.Serializable;

      public class OrderManager<T extends Serializable> {
          private final List<T> orders = new ArrayList<>();
          private int maxCapacity = 500;

          public OrderManager() {}

          public OrderManager(int capacity) {
              this.maxCapacity = capacity;
          }

          public synchronized void addOrder(T order) throws IllegalStateException {
              if (orders.size() >= maxCapacity) {
                  throw new IllegalStateException("Capacity reached: " + maxCapacity);
              }
              orders.add(order);
          }

          public List<T> getOrders() {
              return Collections.unmodifiableList(orders);
          }

          public int count() {
              return orders.size();
          }

          public static <E extends Serializable> OrderManager<E> createDefault() {
              return new OrderManager<>();
          }
      }

      enum OrderStatus {
          PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

          public boolean isFinalState() {
              return this == DELIVERED || this == CANCELLED;
          }
      }

      interface OrderValidator<V> {
          boolean validate(V item);
      }
    """.trimIndent()

    val result = JavaToDexCompiler.compile(null, complexJava)
    assertTrue("Complex multi-class Java compile should succeed: " + result.message, result.success)
    assertTrue("Should compile multiple classes (OrderManager, OrderStatus, OrderValidator)", result.classCount >= 3)
    assertTrue("Method count should be > 5", result.methodCount > 5)

    // Test disassembly of this complex multi-class DEX
    val disResult = DexDisassembler.disassemble(null, result.dexFile)
    assertTrue("Disassemble of complex DEX must succeed: " + disResult.message, disResult.success)
    assertTrue("Disassembled smali must contain OrderManager", disResult.smaliCode.contains("OrderManager"))
    assertTrue("Disassembled smali must contain OrderStatus", disResult.smaliCode.contains("OrderStatus"))
  }

  @Test
  fun testAndroidFrameworkResolutionAndJarClasspath() {
    val androidJava = """
      package com.test.android;

      import android.app.Activity;
      import android.os.Bundle;
      import android.util.Log;
      import android.widget.TextView;

      public class SampleAndroidActivity extends Activity {
          private static final String TAG = "SampleActivity";
          private TextView titleView;

          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              Log.i(TAG, "Initialized with android.jar bootclasspath");
          }

          public void updateTitle(String newTitle) {
              if (titleView != null) {
                  titleView.setText(newTitle);
              }
          }
      }
    """.trimIndent()

    val result = JavaToDexCompiler.compile(null, androidJava)
    assertTrue("Java with Android SDK imports must compile cleanly: " + result.message, result.success)
    assertNotNull("DEX file must exist", result.dexFile)
    assertTrue("DEX bytes must exist", result.dexBytes != null && result.dexBytes.isNotEmpty())

    // Disassemble and verify Android framework descriptors in Dalvik instructions
    val disResult = DexDisassembler.disassemble(null, result.dexFile)
    assertTrue("Disassembly of Android activity must succeed: " + disResult.message, disResult.success)
    assertTrue("Should contain super class Activity", disResult.smaliCode.contains("Landroid/app/Activity;"))
    assertTrue("Should contain Bundle descriptor", disResult.smaliCode.contains("Landroid/os/Bundle;"))
    assertTrue("Should contain Log descriptor", disResult.smaliCode.contains("Landroid/util/Log;"))
  }

  @Test
  fun testAndroidPlatformManagerIndex() {
    assertTrue("android.app.Activity must be recognized as framework class",
            com.example.compiler.AndroidPlatformManager.isFrameworkClass(null, "android.app.Activity"))
    assertTrue("android.os.Bundle must be recognized as framework class",
            com.example.compiler.AndroidPlatformManager.isFrameworkClass(null, "android.os.Bundle"))
    assertTrue("Landroid/view/View; must be recognized as framework class",
            com.example.compiler.AndroidPlatformManager.isFrameworkClass(null, "Landroid/view/View;"))
    assertTrue("java.util.List must be recognized as framework class",
            com.example.compiler.AndroidPlatformManager.isFrameworkClass(null, "java.util.List"))

    val smaliDesc = com.example.compiler.AndroidPlatformManager.toSmaliDescriptor("android.widget.TextView")
    org.junit.Assert.assertEquals("Landroid/widget/TextView;", smaliDesc)

    val javaClass = com.example.compiler.AndroidPlatformManager.fromSmaliDescriptor("Landroid/widget/TextView;")
    org.junit.Assert.assertEquals("android.widget.TextView", javaClass)
  }

  @Test
  fun testJavaxLangModelSourceVersion() {
    val sv = javax.lang.model.SourceVersion.latest()
    assertNotNull(sv)
    assertTrue(javax.lang.model.SourceVersion.isIdentifier("CyberDemo"))
    assertTrue(javax.lang.model.SourceVersion.isName("com.example.CyberDemo"))
    assertTrue(javax.lang.model.SourceVersion.isKeyword("public"))
  }

  @Test
  fun testJavaWithInnerClassesCompilation() {
    val javaCodeWithInnerClasses = """
      package com.test.inner;

      public class OuterClass {
          private String outerField = "outer";

          public static class StaticNestedClass {
              public String getNestedValue() {
                  return "nested_static";
              }
          }

          public class InnerClass {
              public String getCombined() {
                  return outerField + "_inner";
              }
          }

          public interface Callback {
              void onEvent(int code);
          }
      }
    """.trimIndent()

    val result = JavaToDexCompiler.compile(null, javaCodeWithInnerClasses)
    assertTrue("Java with nested/inner classes must compile cleanly: " + result.message, result.success)
    assertTrue("Class count should be >= 3 (Outer, StaticNested, Inner)", result.classCount >= 3)
    val smaliResult = DexDisassembler.disassemble(null, result.dexFile)
    assertTrue("Disassembly must succeed: " + smaliResult.message, smaliResult.success)
    assertTrue("Disassembly should contain OuterClass", smaliResult.smaliCode.contains("OuterClass"))
    assertTrue("Disassembly should contain StaticNestedClass", smaliResult.smaliCode.contains("OuterClass\$StaticNestedClass"))
    assertTrue("Disassembly should contain InnerClass", smaliResult.smaliCode.contains("OuterClass\$InnerClass"))
  }
}

