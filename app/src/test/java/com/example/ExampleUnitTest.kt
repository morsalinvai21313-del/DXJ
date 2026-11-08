package com.example

import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import com.example.compiler.JavaToKotlinConverter
import com.example.compiler.JavaToSmaliCompiler
import com.example.compiler.KotlinToJavaConverter
import com.example.compiler.XmlLayoutRenderer
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
    val smaliFile = File(tempDir, "HelloWorld.smali")
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

    val disassembledSmali = File(disassembleDir, "com/example/cyber/CyberNode.smali")
    assertTrue("Disassembled file should exist", disassembledSmali.exists())
    val content = disassembledSmali.readText()
    assertTrue("Should contain getStatus", content.contains("getStatus"))
  }

  @Test
  fun testJavaToSmaliCompiler() {
    val result = JavaToSmaliCompiler.compile(CyberTemplates.JAVA_SMALI_SAMPLE)
    assertTrue("JavaToSmali compile should succeed: " + result.message, result.success)
    assertTrue("Should contain class directive", result.smaliCode.contains(".class public"))
    assertTrue("Should contain method directive", result.smaliCode.contains(".method public static getStatus()"))
    assertTrue("Should contain compute method", result.smaliCode.contains("compute"))
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
  }
}
