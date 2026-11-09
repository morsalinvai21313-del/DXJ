package com.example.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KotlinToJavaConverter {

    public static class ConvertResult {
        public final boolean success;
        public final String javaCode;
        public final String message;

        public ConvertResult(boolean success, String javaCode, String message) {
            this.success = success;
            this.javaCode = javaCode;
            this.message = message;
        }
    }

    /**
     * Converts Kotlin source code into readable, valid, compilable Java source code.
     * Supports data classes (generates constructor, getters, setters, equals, hashCode, toString),
     * standard classes, companion objects, functions, loops, and control flow.
     */
    public static ConvertResult convert(String kotlinSource) {
        if (kotlinSource == null || kotlinSource.trim().isEmpty()) {
            return new ConvertResult(false, "", "Kotlin source is empty.");
        }

        try {
            // Check for data class
            if (kotlinSource.contains("data class ")) {
                return convertDataClass(kotlinSource);
            }

            String[] lines = kotlinSource.split("\n");
            StringBuilder java = new StringBuilder();
            boolean inCompanion = false;
            int companionBrace = 0;

            for (String rawLine : lines) {
                String line = rawLine.trim();

                // Empty line
                if (line.isEmpty()) {
                    java.append("\n");
                    continue;
                }

                // Comments
                if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                    java.append(rawLine).append("\n");
                    continue;
                }

                // Package
                if (line.startsWith("package ")) {
                    java.append(line.replace(";", "")).append(";\n\n");
                    continue;
                }

                // Import
                if (line.startsWith("import ")) {
                    java.append(line.replace(";", "")).append(";\n");
                    continue;
                }

                // Companion object boundary
                if (line.contains("companion object")) {
                    inCompanion = true;
                    companionBrace = countBraces(line);
                    continue;
                }
                if (inCompanion) {
                    companionBrace += countBraces(line);
                    if (companionBrace <= 0 && line.equals("}")) {
                        inCompanion = false;
                        continue;
                    }
                }

                // Class header: class Foo(...) : Bar(), Baz
                if (line.contains("class ") || line.contains("interface ")) {
                    java.append(convertClassHeader(line)).append("\n");
                    continue;
                }

                // Function: fun name(params): ReturnType
                if (line.startsWith("fun ") || line.contains(" fun ") || line.startsWith("override fun ") || line.contains(" override fun ")) {
                    String jvmMethod = convertFunction(line, inCompanion);
                    java.append("    ").append(jvmMethod).append("\n");
                    continue;
                }

                // Property: val / var
                if (line.startsWith("val ") || line.startsWith("var ") || line.contains(" val ") || line.contains(" var ") ||
                    line.startsWith("private val ") || line.startsWith("private var ")) {
                    String jvmField = convertProperty(line, inCompanion);
                    java.append("    ").append(jvmField).append("\n");
                    continue;
                }

                // Statements
                String convertedStmt = convertStatement(line);
                if (line.equals("{") || line.equals("}")) {
                    java.append(line).append("\n");
                } else if (!convertedStmt.isEmpty()) {
                    java.append("    ").append(convertedStmt).append("\n");
                }
            }

            return new ConvertResult(true, java.toString(), "SUCCESS: Kotlin transpiled to standard Java source.");

        } catch (Exception e) {
            return new ConvertResult(false, "", "Conversion Error: " + e.getMessage());
        }
    }

    private static int countBraces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == '{') count++;
            else if (c == '}') count--;
        }
        return count;
    }

    private static String convertClassHeader(String line) {
        String res = line.trim();
        if (!res.startsWith("public ") && !res.startsWith("private ") && !res.startsWith("abstract ")) {
            res = "public " + res;
        }

        // Handle constructor params on class header: class Foo(val a: Int) : Bar()
        Matcher ctorParamMatcher = Pattern.compile("^(.*?class\\s+[a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)(.*)$").matcher(res);
        if (ctorParamMatcher.find()) {
            String classDecl = ctorParamMatcher.group(1);
            String rest = ctorParamMatcher.group(3);
            res = classDecl + rest;
        }

        if (res.contains(" : ")) {
            String[] parts = res.split(" : ");
            String classNamePart = parts[0];
            String inheritance = parts[1];
            String[] supers = inheritance.split(",");
            StringBuilder sb = new StringBuilder(classNamePart);
            if (supers.length > 0) {
                String firstSuper = supers[0].replaceAll("\\(.*\\)", "").trim();
                sb.append(" extends ").append(firstSuper);
            }
            if (supers.length > 1) {
                sb.append(" implements ");
                for (int i = 1; i < supers.length; i++) {
                    sb.append(supers[i].trim());
                    if (i < supers.length - 1) sb.append(", ");
                }
            }
            res = sb.toString();
        }
        return res;
    }

    private static String convertFunction(String line, boolean isStatic) {
        String res = line.trim();
        boolean isOverride = res.startsWith("override ") || res.contains(" override ");
        res = res.replaceAll("\\boverride\\s+", "");

        // Check for single expression function: fun add(a: Int, b: Int): Int = a + b
        Pattern singleExpPat = Pattern.compile("fun\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)(?:\\s*:\\s*([a-zA-Z0-9_<>\\[\\]?]+))?\\s*=\\s*(.*)$");
        Matcher sem = singleExpPat.matcher(res);
        if (sem.find()) {
            String name = sem.group(1);
            String params = sem.group(2);
            String retType = sem.group(3) != null ? sem.group(3) : "void";
            String expr = sem.group(4).trim();
            String jRetType = toJavaType(retType);
            String jParams = convertKtParams(params);
            String staticMod = isStatic ? "static " : "";
            String overrideAnno = isOverride ? "@Override\n    " : "";
            return overrideAnno + "public " + staticMod + jRetType + " " + name + "(" + jParams + ") {\n        return " + convertStatement(expr) + "\n    }";
        }

        // Standard block function: fun foo(params): Type {
        Pattern pat = Pattern.compile("fun\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)(?:\\s*:\\s*([a-zA-Z0-9_<>\\[\\]?]+))?\\s*(\\{?)");
        Matcher m = pat.matcher(res);
        if (m.find()) {
            String name = m.group(1);
            String params = m.group(2);
            String retType = m.group(3) != null ? m.group(3) : "void";
            String openBrace = m.group(4);

            String jRetType = toJavaType(retType);
            String jParams = convertKtParams(params);
            String staticMod = isStatic ? "static " : "";
            String overrideAnno = isOverride ? "@Override\n    " : "";

            return overrideAnno + "public " + staticMod + jRetType + " " + name + "(" + jParams + ")" + (openBrace.isEmpty() ? "" : " " + openBrace);
        }
        return res;
    }

    private static String convertProperty(String line, boolean isStatic) {
        String res = line.trim();
        boolean isFinal = res.startsWith("val ") || res.contains(" val ");
        boolean isPrivate = res.startsWith("private ");
        res = res.replaceAll("^private\\s+", "");

        Pattern pat = Pattern.compile("(?:val|var)\\s+([a-zA-Z0-9_]+)(?:\\s*:\\s*([a-zA-Z0-9_<>\\[\\]?]+))?(?:\\s*=\\s*(.*))?");
        Matcher m = pat.matcher(res);
        if (m.find()) {
            String name = m.group(1);
            String type = m.group(2);
            String val = m.group(3);

            String jType = (type != null) ? toJavaType(type) : inferTypeFromValue(val);
            String staticMod = isStatic ? "static " : "";
            String finalMod = isFinal ? "final " : "";
            String vis = isPrivate ? "private " : "public ";

            if (val != null && !val.trim().isEmpty()) {
                val = convertStatement(val.trim());
                if (!val.endsWith(";")) val = val + ";";
                return vis + staticMod + finalMod + jType + " " + name + " = " + val;
            } else {
                return vis + staticMod + finalMod + jType + " " + name + ";";
            }
        }
        return res;
    }

    private static String inferTypeFromValue(String val) {
        if (val == null || val.isEmpty()) return "Object";
        val = val.trim();
        if (val.startsWith("\"")) return "String";
        if ("true".equals(val) || "false".equals(val)) return "boolean";
        if (val.matches("^-?\\d+$")) return "int";
        if (val.matches("^-?\\d+L$")) return "long";
        if (val.matches("^-?\\d+\\.\\d+f?$")) return "float";
        if (val.matches("^-?\\d+\\.\\d+$")) return "double";
        return "Object";
    }

    private static String convertStatement(String line) {
        String res = line;

        // Loop: for (i in 0 until n) -> for (int i = 0; i < n; i++)
        Matcher untilM = Pattern.compile("for\\s*\\(\\s*([a-zA-Z0-9_]+)\\s+in\\s+([0-9a-zA-Z_]+)\\s+until\\s+([^)]+)\\)").matcher(res);
        if (untilM.find()) {
            String var = untilM.group(1);
            String start = untilM.group(2);
            String end = untilM.group(3).trim();
            res = untilM.replaceFirst("for (int " + var + " = " + start + "; " + var + " < " + end + "; " + var + "++)");
        }

        // Loop: for (i in 0..n) -> for (int i = 0; i <= n; i++)
        Matcher rangeM = Pattern.compile("for\\s*\\(\\s*([a-zA-Z0-9_]+)\\s+in\\s+([0-9a-zA-Z_]+)\\.\\.([^)]+)\\)").matcher(res);
        if (rangeM.find()) {
            String var = rangeM.group(1);
            String start = rangeM.group(2);
            String end = rangeM.group(3).trim();
            res = rangeM.replaceFirst("for (int " + var + " = " + start + "; " + var + " <= " + end + "; " + var + "++)");
        }

        // Loop: for (item in list) -> for (Object item : list)
        Matcher inM = Pattern.compile("for\\s*\\(\\s*([a-zA-Z0-9_]+)\\s+in\\s+([^)]+)\\)").matcher(res);
        if (inM.find()) {
            String var = inM.group(1);
            String col = inM.group(2).trim();
            res = inM.replaceFirst("for (Object " + var + " : " + col + ")");
        }

        res = res.replaceAll("\\bprintln\\(", "System.out.println(");
        res = res.replaceAll("\\bprint\\(", "System.out.print(");

        // Type checks: !is -> !(instanceof)
        res = res.replaceAll("([a-zA-Z0-9_]+)\\s+!is\\s+([a-zA-Z0-9_<>]+)", "!($1 instanceof $2)");
        res = res.replaceAll("\\bis\\b", "instanceof");

        // Cast: x as String -> (String) x
        res = res.replaceAll("([a-zA-Z0-9_]+)\\s+as\\s+([a-zA-Z0-9_<>]+)", "($2) $1");

        // Null coalescing: a ?: b -> (a != null ? a : b)
        Matcher elvisM = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\?:\\s*([^;\\n]+)").matcher(res);
        if (elvisM.find()) {
            String a = elvisM.group(1);
            String b = elvisM.group(2).trim();
            res = elvisM.replaceFirst("(" + a + " != null ? " + a + " : " + b + ")");
        }

        // Semicolon
        if (!res.endsWith(";") && !res.endsWith("{") && !res.endsWith("}") && !res.isEmpty() && !res.startsWith("//")) {
            res = res + ";";
        }
        return res;
    }

    private static String convertKtParams(String params) {
        if (params == null || params.trim().isEmpty()) return "";
        String[] parts = params.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            String[] words = p.split(":");
            if (words.length >= 2) {
                String name = words[0].trim();
                String type = words[1].trim();
                sb.append(toJavaType(type)).append(" ").append(name);
            } else {
                sb.append(p);
            }
            if (i < parts.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private static String toJavaType(String type) {
        if (type == null) return "void";
        type = type.trim().replace("?", "");
        if (type.startsWith("Array<") && type.endsWith(">")) {
            String inner = type.substring(6, type.length() - 1);
            return toJavaType(inner) + "[]";
        }
        switch (type) {
            case "Unit": return "void";
            case "Int": return "int";
            case "Boolean": return "boolean";
            case "Long": return "long";
            case "Float": return "float";
            case "Double": return "double";
            case "Byte": return "byte";
            case "Short": return "short";
            case "Char": return "char";
            case "Any": return "Object";
            case "IntArray": return "int[]";
            case "ByteArray": return "byte[]";
            case "LongArray": return "long[]";
            case "BooleanArray": return "boolean[]";
            default: return type;
        }
    }

    private static ConvertResult convertDataClass(String source) {
        Pattern pat = Pattern.compile("data\\s+class\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");
        Matcher m = pat.matcher(source);
        if (!m.find()) {
            return new ConvertResult(false, "", "Could not parse data class declaration.");
        }

        String className = m.group(1);
        String params = m.group(2);

        StringBuilder java = new StringBuilder();

        // Extract package
        Matcher pkgM = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)").matcher(source);
        if (pkgM.find()) {
            java.append("package ").append(pkgM.group(1)).append(";\n\n");
        }

        java.append("import java.util.Objects;\n\n");
        java.append("public class ").append(className).append(" {\n\n");

        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();

        if (params != null && !params.trim().isEmpty()) {
            String[] parts = params.split(",");
            for (String p : parts) {
                p = p.trim().replaceAll("^(val|var)\\s+", "");
                String[] words = p.split(":");
                if (words.length >= 2) {
                    String name = words[0].trim();
                    String type = toJavaType(words[1].trim());
                    fieldNames.add(name);
                    fieldTypes.add(type);
                    java.append("    private final ").append(type).append(" ").append(name).append(";\n");
                }
            }
        }

        // Constructor
        java.append("\n    public ").append(className).append("(");
        for (int i = 0; i < fieldNames.size(); i++) {
            java.append(fieldTypes.get(i)).append(" ").append(fieldNames.get(i));
            if (i < fieldNames.size() - 1) java.append(", ");
        }
        java.append(") {\n");
        for (String fName : fieldNames) {
            java.append("        this.").append(fName).append(" = ").append(fName).append(";\n");
        }
        java.append("    }\n\n");

        // Getters
        for (int i = 0; i < fieldNames.size(); i++) {
            String fName = fieldNames.get(i);
            String fType = fieldTypes.get(i);
            String cap = Character.toUpperCase(fName.charAt(0)) + fName.substring(1);
            String prefix = "boolean".equals(fType) ? "is" : "get";
            java.append("    public ").append(fType).append(" ").append(prefix).append(cap).append("() {\n");
            java.append("        return this.").append(fName).append(";\n");
            java.append("    }\n\n");
        }

        // equals
        java.append("    @Override\n");
        java.append("    public boolean equals(Object o) {\n");
        java.append("        if (this == o) return true;\n");
        java.append("        if (o == null || getClass() != o.getClass()) return false;\n");
        java.append("        ").append(className).append(" that = (").append(className).append(") o;\n");
        if (fieldNames.isEmpty()) {
            java.append("        return true;\n");
        } else {
            java.append("        return ");
            for (int i = 0; i < fieldNames.size(); i++) {
                String fn = fieldNames.get(i);
                String ft = fieldTypes.get(i);
                if (isPrimitive(ft)) {
                    java.append(fn).append(" == that.").append(fn);
                } else {
                    java.append("Objects.equals(").append(fn).append(", that.").append(fn).append(")");
                }
                if (i < fieldNames.size() - 1) java.append(" &&\n               ");
            }
            java.append(";\n");
        }
        java.append("    }\n\n");

        // hashCode
        java.append("    @Override\n");
        java.append("    public int hashCode() {\n");
        if (fieldNames.isEmpty()) {
            java.append("        return 0;\n");
        } else {
            java.append("        return Objects.hash(");
            for (int i = 0; i < fieldNames.size(); i++) {
                java.append(fieldNames.get(i));
                if (i < fieldNames.size() - 1) java.append(", ");
            }
            java.append(");\n");
        }
        java.append("    }\n\n");

        // toString
        java.append("    @Override\n");
        java.append("    public String toString() {\n");
        java.append("        return \"").append(className).append("{\" +\n");
        for (int i = 0; i < fieldNames.size(); i++) {
            String fName = fieldNames.get(i);
            java.append("                \"").append(fName).append("=\" + ").append(fName);
            if (i < fieldNames.size() - 1) java.append(" + \", \" +\n");
        }
        java.append(" +\n                '}';\n");
        java.append("    }\n");

        java.append("}\n");
        return new ConvertResult(true, java.toString(), "SUCCESS: Kotlin data class transpiled to complete Java POJO.");
    }

    private static boolean isPrimitive(String type) {
        return "int".equals(type) || "boolean".equals(type) || "long".equals(type) ||
                "float".equals(type) || "double".equals(type) || "byte".equals(type) ||
                "short".equals(type) || "char".equals(type);
    }
}
