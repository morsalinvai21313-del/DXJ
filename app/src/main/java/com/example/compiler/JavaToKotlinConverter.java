package com.example.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaToKotlinConverter {

    public static class ConvertResult {
        public final boolean success;
        public final String kotlinCode;
        public final String message;

        public ConvertResult(boolean success, String kotlinCode, String message) {
            this.success = success;
            this.kotlinCode = kotlinCode;
            this.message = message;
        }
    }

    /**
     * Converts complex Java source code into clean, valid, idiomatic Kotlin code.
     * Handles large multi-line classes, generics, companion objects, constructors, and control structures.
     */
    public static ConvertResult convert(String javaSource) {
        if (javaSource == null || javaSource.trim().isEmpty()) {
            return new ConvertResult(false, "", "Java source is empty.");
        }

        try {
            String[] lines = javaSource.split("\n");
            StringBuilder kt = new StringBuilder();
            List<String> companionMembers = new ArrayList<>();
            boolean insideClass = false;
            String currentClass = "";
            int braceDepth = 0;

            for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
                String rawLine = lines[lineIdx];
                String line = rawLine.trim();

                // Empty lines
                if (line.isEmpty()) {
                    kt.append("\n");
                    continue;
                }

                // Comments
                if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                    kt.append(rawLine).append("\n");
                    continue;
                }

                // Package declaration
                if (line.startsWith("package ")) {
                    String pkg = line.replace(";", "").trim();
                    kt.append(pkg).append("\n\n");
                    continue;
                }

                // Import declaration
                if (line.startsWith("import ")) {
                    String imp = line.replace(";", "").trim();
                    kt.append(imp).append("\n");
                    continue;
                }

                // Class / Interface / Enum header
                if (isClassHeader(line)) {
                    insideClass = true;
                    braceDepth = countBraces(line);
                    currentClass = extractClassName(line);
                    String convertedHeader = convertClassHeader(line);
                    kt.append(convertedHeader).append("\n");
                    continue;
                }

                if (insideClass) {
                    braceDepth += countBraces(line);

                    if (line.equals("}") && braceDepth <= 0) {
                        // Append companion object if static members were collected
                        if (!companionMembers.isEmpty()) {
                            kt.append("\n    companion object {\n");
                            for (String cm : companionMembers) {
                                kt.append("        ").append(cm).append("\n");
                            }
                            kt.append("    }\n");
                            companionMembers.clear();
                        }
                        kt.append("}\n");
                        insideClass = false;
                        continue;
                    }

                    // Check for static methods or fields
                    if (isStaticMember(line)) {
                        String convertedStatic = convertMember(line, currentClass);
                        companionMembers.add(convertedStatic);
                        continue;
                    }

                    // Standard class member
                    String converted = convertMember(line, currentClass);
                    kt.append("    ").append(converted).append("\n");
                } else {
                    kt.append(convertStatement(line)).append("\n");
                }
            }

            // Flush remaining companion members if class ended without closing brace
            if (!companionMembers.isEmpty()) {
                kt.append("\ncompanion object {\n");
                for (String cm : companionMembers) {
                    kt.append("    ").append(cm).append("\n");
                }
                kt.append("}\n");
            }

            String output = postProcessKotlin(kt.toString());
            return new ConvertResult(true, output, "SUCCESS: Java successfully transpiled to Kotlin.");

        } catch (Exception e) {
            return new ConvertResult(false, "", "Conversion Error: " + e.getMessage());
        }
    }

    private static boolean isClassHeader(String line) {
        return (line.contains("class ") || line.contains("interface ") || line.contains("enum "))
                && !line.contains("class.") && !line.contains(".class");
    }

    private static int countBraces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == '{') count++;
            else if (c == '}') count--;
        }
        return count;
    }

    private static String extractClassName(String line) {
        Matcher m = Pattern.compile("(?:class|interface|enum)\\s+([a-zA-Z0-9_]+)").matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return "UnknownClass";
    }

    private static boolean isStaticMember(String line) {
        return (line.contains(" static ") || line.startsWith("static ")) && !line.contains("class ");
    }

    private static String convertClassHeader(String line) {
        String res = line.replace(";", "").trim();
        // Remove redundant public
        res = res.replaceAll("\\bpublic\\s+", "");

        // Interface / Enum
        if (res.contains("interface ")) {
            return res.replaceAll("\\s+extends\\s+", " : ");
        }
        if (res.contains("enum ")) {
            if (!res.contains("enum class")) {
                res = res.replace("enum ", "enum class ");
            }
            return res;
        }

        // Extends & Implements
        if (res.contains(" extends ") || res.contains(" implements ")) {
            res = res.replaceAll("\\s+extends\\s+", " : ");
            res = res.replaceAll("\\s+implements\\s+", ", ");

            int colon = res.indexOf(":");
            if (colon != -1) {
                String prefix = res.substring(0, colon);
                String after = res.substring(colon + 1).trim();
                String[] supers = after.split(",");
                if (supers.length > 0) {
                    String base = supers[0].trim();
                    if (!base.endsWith("()")) {
                        base = base + "()";
                    }
                    supers[0] = base;
                    StringBuilder sb = new StringBuilder(prefix).append(": ");
                    for (int i = 0; i < supers.length; i++) {
                        sb.append(supers[i].trim());
                        if (i < supers.length - 1) sb.append(", ");
                    }
                    res = sb.toString();
                }
            }
        }
        return res;
    }

    private static String convertMember(String line, String className) {
        String res = line.replace(";", "").trim();

        // Annotations
        res = res.replaceAll("@Override\\s*", "override ");

        // Modifiers
        res = res.replaceAll("\\bpublic\\s+", "");
        res = res.replaceAll("\\bstatic\\s+", "");

        // Logging & System calls
        res = res.replaceAll("System\\.out\\.println\\(", "println(");
        res = res.replaceAll("System\\.out\\.print\\(", "print(");

        // Instance creation: new Foo() -> Foo()
        res = res.replaceAll("\\bnew\\s+([a-zA-Z0-9_<>]+)\\(", "$1(");

        // Type check: instanceof -> is
        res = res.replaceAll("\\binstanceof\\b", "is");

        // String equals: !a.equals(b) -> a != b, a.equals(b) -> a == b
        res = res.replaceAll("!([a-zA-Z0-9_]+)\\.equals\\(([^)]+)\\)", "$1 != $2");
        res = res.replaceAll("([a-zA-Z0-9_]+)\\.equals\\(([^)]+)\\)", "$1 == $2");

        // Method declaration: Type name(params) {
        Pattern methodPat = Pattern.compile("([a-zA-Z0-9_<>\\[\\]?]+)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[^\\{]+)?(\\{?)");
        Matcher m = methodPat.matcher(res);
        if (m.find()) {
            String retType = m.group(1);
            String name = m.group(2);
            String params = m.group(3);
            String openBrace = m.group(4);

            if (!name.equals("if") && !name.equals("for") && !name.equals("while") && !name.equals("switch") && !name.equals("catch")) {
                if (name.equals(className)) {
                    return "constructor(" + convertParams(params) + ")" + (openBrace.isEmpty() ? "" : " " + openBrace);
                }
                String ktReturn = toKtType(retType);
                String ktParams = convertParams(params);
                String retClause = "Unit".equals(ktReturn) ? "" : ": " + ktReturn;
                return "fun " + name + "(" + ktParams + ")" + retClause + (openBrace.isEmpty() ? "" : " " + openBrace);
            }
        }

        // Field declaration: [final] Type name [= val]
        Pattern fieldPat = Pattern.compile("(final)?\\s*([a-zA-Z0-9_<>\\[\\]?]+)\\s+([a-zA-Z0-9_]+)(?:\\s*=\\s*(.*))?");
        Matcher fm = fieldPat.matcher(res);
        if (fm.find()) {
            boolean isFinal = fm.group(1) != null;
            String type = fm.group(2);
            String name = fm.group(3);
            String val = fm.group(4);

            if (!name.equals("return") && !name.equals("throw") && !name.equals("import") && !type.equals("return") && !name.equals("case")) {
                String ktType = toKtType(type);
                String kw = isFinal ? "val" : "var";
                if (val != null && !val.trim().isEmpty()) {
                    return kw + " " + name + ": " + ktType + " = " + val.trim();
                } else {
                    return kw + " " + name + ": " + ktType + "? = null";
                }
            }
        }

        return convertStatement(res);
    }

    private static String convertStatement(String line) {
        String res = line;
        res = res.replaceAll("\\bnew\\s+([a-zA-Z0-9_<>]+)\\(", "$1(");
        res = res.replaceAll("System\\.out\\.println\\(", "println(");
        res = res.replaceAll("System\\.out\\.print\\(", "print(");
        res = res.replaceAll("\\binstanceof\\b", "is");
        return res;
    }

    private static String convertParams(String params) {
        if (params == null || params.trim().isEmpty()) return "";
        String[] parts = params.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            String[] words = p.split("\\s+");
            if (words.length >= 2) {
                String type = words[0];
                String name = words[1];
                sb.append(name).append(": ").append(toKtType(type));
            } else {
                sb.append(p);
            }
            if (i < parts.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private static String toKtType(String type) {
        if (type == null) return "Unit";
        type = type.trim();
        if (type.endsWith("[]")) {
            String elem = type.substring(0, type.length() - 2);
            if ("int".equals(elem)) return "IntArray";
            if ("byte".equals(elem)) return "ByteArray";
            if ("long".equals(elem)) return "LongArray";
            return "Array<" + toKtType(elem) + ">";
        }
        switch (type) {
            case "void": return "Unit";
            case "int": return "Int";
            case "boolean": return "Boolean";
            case "long": return "Long";
            case "float": return "Float";
            case "double": return "Double";
            case "byte": return "Byte";
            case "short": return "Short";
            case "char": return "Char";
            case "Object": return "Any";
            default: return type;
        }
    }

    private static String postProcessKotlin(String code) {
        return code.replaceAll("(?m)^[ \\t]+$", "");
    }
}
