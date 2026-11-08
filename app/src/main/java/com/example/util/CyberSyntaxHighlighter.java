package com.example.util;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CyberSyntaxHighlighter {

    private static final int COLOR_KEYWORD = Color.parseColor("#00F0FF");    // Cyan
    private static final int COLOR_STRING = Color.parseColor("#00FF88");     // Neon Green
    private static final int COLOR_COMMENT = Color.parseColor("#64748B");    // Dim Slate
    private static final int COLOR_NUMBER = Color.parseColor("#FBBF24");     // Amber
    private static final int COLOR_DIRECTIVE = Color.parseColor("#F43F5E");  // Rose / Magenta
    private static final int COLOR_ATTR = Color.parseColor("#38BDF8");       // Sky Blue

    // Java / Kotlin keywords
    private static final Pattern JAVA_KEYWORDS = Pattern.compile(
            "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|" +
            "default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|" +
            "import|instanceof|int|interface|long|native|new|package|private|protected|public|" +
            "return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|" +
            "try|void|volatile|while|true|false|null|var|val|fun|when|override|companion|object|data)\\b"
    );

    // Smali directives
    private static final Pattern SMALI_DIRECTIVES = Pattern.compile(
            "\\.(class|super|source|implements|field|method|end|registers|locals|parameter|" +
            "line|prologue|epilogue|restart|annotation|enum)\\b"
    );

    // Smali opcodes
    private static final Pattern SMALI_OPCODES = Pattern.compile(
            "\\b(invoke-virtual|invoke-super|invoke-direct|invoke-static|invoke-interface|" +
            "const-string|const/4|const/16|const|const-wide|return-void|return|return-object|" +
            "sget|sget-object|sput|sput-object|iget|iget-object|iput|iput-object|" +
            "new-instance|check-cast|instance-of|array-length|new-array|move-result|" +
            "move-result-object|move-exception|throw|goto|if-eqz|if-nez|if-eq|if-ne|" +
            "nop|move)\\b"
    );

    // XML tags and attributes
    private static final Pattern XML_TAGS = Pattern.compile("</?[a-zA-Z0-9_:-]+");
    private static final Pattern XML_ATTRS = Pattern.compile("\\s+([a-zA-Z0-9_:-]+)=");

    // Common literals
    private static final Pattern STRINGS = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"");
    private static final Pattern SINGLE_COMMENTS = Pattern.compile("//.*|#.*");
    private static final Pattern NUMBERS = Pattern.compile("\\b0x[0-9a-fA-F]+\\b|\\b\\d+(\\.\\d+)?f?\\b");

    private CyberSyntaxHighlighter() {}

    public static CharSequence highlight(String code, String language) {
        if (code == null) return "";
        SpannableStringBuilder builder = new SpannableStringBuilder(code);

        // Highlight strings first
        applyPattern(builder, STRINGS, COLOR_STRING);
        // Highlight numbers
        applyPattern(builder, NUMBERS, COLOR_NUMBER);

        if ("smali".equalsIgnoreCase(language)) {
            applyPattern(builder, SMALI_DIRECTIVES, COLOR_DIRECTIVE);
            applyPattern(builder, SMALI_OPCODES, COLOR_KEYWORD);
        } else if ("xml".equalsIgnoreCase(language)) {
            applyPattern(builder, XML_TAGS, COLOR_KEYWORD);
            applyPattern(builder, XML_ATTRS, COLOR_ATTR);
        } else {
            // Java / Kotlin
            applyPattern(builder, JAVA_KEYWORDS, COLOR_KEYWORD);
        }

        // Comments last to override
        applyPattern(builder, SINGLE_COMMENTS, COLOR_COMMENT);

        return builder;
    }

    private static void applyPattern(SpannableStringBuilder builder, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(builder);
        while (matcher.find()) {
            builder.setSpan(
                    new ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
}
