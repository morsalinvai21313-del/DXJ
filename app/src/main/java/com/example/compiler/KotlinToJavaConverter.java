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

            for (String rawLine : lines) {
                String line = rawLine.trim();

                // Package
                if (line.startsWith("package ")) {
                    String pkgLine = line;
                    if (pkgLine.contains("example")) {
                        pkgLine = pkgLine.replace("com.example", "com.morsalin.djx").replace("example", "morsalin.djx");
                    }
                    java.append(pkgLine).append(";\n\n");
                    continue;
                }

                // Import
                if (line.startsWith("import ")) {
                    java.append(line).append(";\n");
                    continue;
                }

                // Companion object start
                if (line.contains("companion object")) {
                    inCompanion = true;
                    continue;
                }

                if (inCompanion && line.equals("}")) {
                    inCompanion = false;
                    continue;
                }

                // Class header
                if (line.contains("class ")) {
                    java.append(convertClassHeader(line)).append("\n");
                    continue;
                }

                // Function: fun name(params): ReturnType {
                if (line.startsWith("fun ") || line.contains(" fun ")) {
                    String jvmMethod = convertFunction(line, inCompanion);
                    java.append("    ").append(jvmMethod).append("\n");
                    continue;
                }

                // Property: val name: Type = ... or var name: Type = ...
                if (line.startsWith("val ") || line.startsWith("var ") || line.contains(" val ") || line.contains(" var ")) {
                    String jvmField = convertProperty(line, inCompanion);
                    java.append("    ").append(jvmField).append("\n");
                    continue;
                }

                // System calls / statements
                String convertedStmt = convertStatement(line);
                java.append("    ").append(convertedStmt).append("\n");
            }

            return new ConvertResult(true, java.toString(), "SUCCESS: Kotlin converted to Java source.");

        } catch (Exception e) {
            return new ConvertResult(false, "", "Conversion Error: " + e.getMessage());
        }
    }

    private static String convertClassHeader(String line) {
        String res = line.trim();
        if (!res.startsWith("public ")) {
            res = "public " + res;
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
        // Extract fun name(params): ReturnType {
        Pattern pat = Pattern.compile("fun\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)(?:\\s*:\\s*([a-zA-Z0-9_<>\\[\\]?]+))?\\s*\\{?");
        Matcher m = pat.matcher(res);
        if (m.find()) {
            String name = m.group(1);
            String params = m.group(2);
            String retType = m.group(3) != null ? m.group(3) : "void";

            String jRetType = toJavaType(retType);
            String jParams = convertKtParams(params);
            String staticMod = isStatic ? "static " : "";

            return "public " + staticMod + jRetType + " " + name + "(" + jParams + ") {";
        }
        return res;
    }

    private static String convertProperty(String line, boolean isStatic) {
        String res = line.trim();
        boolean isFinal = res.startsWith("val ") || res.contains(" val ");
        Pattern pat = Pattern.compile("(?:val|var)\\s+([a-zA-Z0-9_]+)(?:\\s*:\\s*([a-zA-Z0-9_<>\\[\\]?]+))?(?:\\s*=\\s*(.*))?");
        Matcher m = pat.matcher(res);
        if (m.find()) {
            String name = m.group(1);
            String type = m.group(2);
            String val = m.group(3);

            String jType = (type != null) ? toJavaType(type) : "Object";
            String staticMod = isStatic ? "static " : "";
            String finalMod = isFinal ? "final " : "";

            if (val != null && !val.trim().isEmpty()) {
                val = convertStatement(val.trim());
                if (!val.endsWith(";")) val = val + ";";
                return "public " + staticMod + finalMod + jType + " " + name + " = " + val;
            } else {
                return "public " + staticMod + finalMod + jType + " " + name + ";";
            }
        }
        return res;
    }

    private static String convertStatement(String line) {
        String res = line;
        res = res.replaceAll("\\bprintln\\(", "System.out.println(");
        res = res.replaceAll("\\bprint\\(", "System.out.print(");
        res = res.replaceAll("\\bis\\b", "instanceof");
        if (!res.endsWith(";") && !res.endsWith("{") && !res.endsWith("}") && !res.isEmpty()) {
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
            default: return type;
        }
    }

    private static ConvertResult convertDataClass(String source) {
        // e.g. data class User(val id: Int, val name: String)
        Pattern pat = Pattern.compile("data\\s+class\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");
        Matcher m = pat.matcher(source);
        if (!m.find()) {
            return new ConvertResult(false, "", "Could not parse data class.");
        }

        String className = m.group(1);
        String params = m.group(2);

        StringBuilder java = new StringBuilder();

        // Extract package if present
        Matcher pkgM = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)").matcher(source);
        if (pkgM.find()) {
            String pkg = pkgM.group(1);
            if (pkg.contains("example")) {
                pkg = pkg.replace("com.example", "com.morsalin.djx").replace("example", "morsalin.djx");
            }
            java.append("package ").append(pkg).append(";\n\n");
        } else {
            java.append("package com.morsalin.djx.").append(className.toLowerCase()).append(";\n\n");
        }

        java.append("public class ").append(className).append(" {\n\n");

        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();

        if (params != null && !params.trim().isEmpty()) {
            String[] parts = params.split(",");
            for (String p : parts) {
                p = p.trim();
                p = p.replaceAll("^(val|var)\\s+", "");
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
            java.append("    public ").append(fType).append(" get").append(cap).append("() {\n");
            java.append("        return this.").append(fName).append(";\n");
            java.append("    }\n\n");
        }

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
        return new ConvertResult(true, java.toString(), "SUCCESS: Kotlin data class converted to Java class.");
    }
}
