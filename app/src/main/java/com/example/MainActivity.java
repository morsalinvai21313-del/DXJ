package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.activity.DexToSmaliActivity;
import com.example.activity.JavaToDexActivity;
import com.example.activity.JavaToKotlinActivity;
import com.example.activity.JavaToSmaliActivity;
import com.example.activity.KotlinToJavaActivity;
import com.example.activity.SmaliToDexActivity;
import com.example.activity.XmlDrawableActivity;
import com.example.activity.XmlLayoutActivity;
import com.example.compiler.AndroidPlatformManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pre-extract and index android.jar in the background on app start
        AndroidPlatformManager.initializeAsync(this, null);

        setupModuleButtons();
    }

    private void setupModuleButtons() {
        // 1. Java to Smali
        View btnJavaToSmali = findViewById(R.id.btn_menu_java_to_smali);
        if (btnJavaToSmali != null) {
            btnJavaToSmali.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, JavaToSmaliActivity.class)));
        }

        // 2. Java to Dex
        View btnJavaToDex = findViewById(R.id.btn_menu_java_to_dex);
        if (btnJavaToDex != null) {
            btnJavaToDex.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, JavaToDexActivity.class)));
        }

        // 3. Smali to Dex
        View btnSmaliToDex = findViewById(R.id.btn_menu_smali_to_dex);
        if (btnSmaliToDex != null) {
            btnSmaliToDex.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, SmaliToDexActivity.class)));
        }

        // 4. Dex to Smali
        View btnDexToSmali = findViewById(R.id.btn_menu_dex_to_smali);
        if (btnDexToSmali != null) {
            btnDexToSmali.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, DexToSmaliActivity.class)));
        }

        // 5. Java to Kotlin
        View btnJavaToKotlin = findViewById(R.id.btn_menu_java_to_kotlin);
        if (btnJavaToKotlin != null) {
            btnJavaToKotlin.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, JavaToKotlinActivity.class)));
        }

        // 6. Kotlin to Java
        View btnKotlinToJava = findViewById(R.id.btn_menu_kotlin_to_java);
        if (btnKotlinToJava != null) {
            btnKotlinToJava.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, KotlinToJavaActivity.class)));
        }

        // 7. XML Layout Editor
        View btnXmlLayout = findViewById(R.id.btn_menu_xml_layout);
        if (btnXmlLayout != null) {
            btnXmlLayout.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, XmlLayoutActivity.class)));
        }

        // 8. XML Drawable Editor
        View btnXmlDrawable = findViewById(R.id.btn_menu_xml_drawable);
        if (btnXmlDrawable != null) {
            btnXmlDrawable.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, XmlDrawableActivity.class)));
        }
    }
}
