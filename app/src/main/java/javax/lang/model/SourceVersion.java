package javax.lang.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Source versions of the Java programming language.
 * Provided on Android to support ECJ and bytecode compilers where the java.compiler
 * module is missing from Android's core libcore/ART runtime.
 */
public enum SourceVersion {
    RELEASE_0,
    RELEASE_1,
    RELEASE_2,
    RELEASE_3,
    RELEASE_4,
    RELEASE_5,
    RELEASE_6,
    RELEASE_7,
    RELEASE_8,
    RELEASE_9,
    RELEASE_10,
    RELEASE_11,
    RELEASE_12,
    RELEASE_13,
    RELEASE_14,
    RELEASE_15,
    RELEASE_16,
    RELEASE_17,
    RELEASE_18,
    RELEASE_19,
    RELEASE_20,
    RELEASE_21,
    RELEASE_22,
    RELEASE_23,
    RELEASE_24;

    private static final SourceVersion latestSupported = RELEASE_21;

    private static final Set<String> KEYWORDS;

    static {
        Set<String> s = new HashSet<>();
        String[] kw = {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "record", "sealed", "non-sealed",
            "permits", "yield", "var"
        };
        Collections.addAll(s, kw);
        KEYWORDS = Collections.unmodifiableSet(s);
    }

    public static SourceVersion latest() {
        return RELEASE_21;
    }

    public static SourceVersion latestSupported() {
        return latestSupported;
    }

    public static boolean isIdentifier(CharSequence name) {
        if (name == null) return false;
        String id = name.toString();
        if (id.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(id.charAt(0))) return false;
        for (int i = 1; i < id.length(); i++) {
            if (!Character.isJavaIdentifierPart(id.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isName(CharSequence name) {
        return isName(name, latest());
    }

    public static boolean isName(CharSequence name, SourceVersion version) {
        if (name == null) return false;
        String id = name.toString();
        for (String s : id.split("\\.", -1)) {
            if (!isIdentifier(s) || isKeyword(s, version)) return false;
        }
        return true;
    }

    public static boolean isKeyword(CharSequence s) {
        return isKeyword(s, latest());
    }

    public static boolean isKeyword(CharSequence s, SourceVersion version) {
        if (s == null) return false;
        return KEYWORDS.contains(s.toString());
    }
}
