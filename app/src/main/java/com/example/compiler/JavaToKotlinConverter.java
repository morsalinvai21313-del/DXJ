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

    public static ConvertResult convert(String javaSource) {
        if (javaSource == null || javaSource.trim().isEmpty()) {
            return new ConvertResult(false, "", "Java source is empty.");
        }

        try {
            String[] lines = javaSource.split("\n");
            StringBuilder kt = new StringBuilder();
            List<String> companionMembers = new ArrayList<>();
            boolean insideClass = false;
            String className = "";
            int braceDepth = 0;

            for (String rawLine : lines) {
                String line = rawLine.trim();

                // Package
                if (line.startsWith("package ")) {
                    kt.append(line.replace(";", "")).append("\n\n");
                    continue;
                }

                // Import
                if (line.startsWith("import ")) {
                    kt.append(line.replace(";", "")).append("\n");
                    continue;
                }

                // Class declaration
                if (line.contains("class ")) {
                    insideClass = true;
                    braceDepth = 1;
                    String convertedClass = convertClassHeader(line);
                    Matcher m = Pattern.compile("class\\s+([a-zA-Z0-9_]+)").matcher(line);
                    if (m.find()) {
                        className = m.group(1);
                    }
                    kt.append(convertedClass).append("\n");
                    continue;
                }

                if (insideClass) {
                    if (line.contains("{")) {
                        braceDepth++;
                    }
                    if (line.contains("}")) {
                        braceDepth--;
                        if (braceDepth <= 0) {
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
                        } else {
                            kt.append("    }\n");
                            continue;
                        }
                    }
                }

                // Static field or method check
                if (insideClass && line.contains(" static ")) {
                    String convertedStatic = convertMember(line, className);
                    companionMembers.add(convertedStatic);
                    continue;
                }

                // Instance member or method
                if (insideClass) {
                    String converted = convertMember(line, className);
                    kt.append("    ").append(converted).append("\n");
                } else {
                    kt.append(line).append("\n");
                }
            }

            String output = postProcessKotlin(kt.toString());
            return new ConvertResult(true, output, "SUCCESS: Java successfully converted to Kotlin.");

        } catch (Exception e) {
            return new ConvertResult(false, "", "Conversion Error: " + e.getMessage());
        }
    }

    private static String convertClassHeader(String line) {
        String result = line.replace(";", "");
        // Remove public
        result = result.replaceAll("\\bpublic\\s+", "");
        // extends / implements
        if (result.contains(" extends ") || result.contains(" implements ")) {
            result = result.replaceAll("\\s+extends\\s+", " : ");
            result = result.replaceAll("\\s+implements\\s+", ", ");
            // Append constructor parens to first superclass if present
            int colonIdx = result.indexOf(":");
            if (colonIdx != -1) {
                String before = result.substring(0, colonIdx);
                String after = result.substring(colonIdx + 1).trim();
                String[] supers = after.split(",");
                if (supers.length > 0) {
                    String base = supers[0].trim();
                    if (!base.endsWith("()")) {
                        base = base + "()";
                    }
                    supers[0] = base;
                    StringBuilder sb = new StringBuilder(before).append(": ");
                    for (int i = 0; i < supers.length; i++) {
                        sb.append(supers[i].trim());
                        if (i < supers.length - 1) sb.append(", ");
                    }
                    result = sb.toString();
                }
            }
        }
        return result;
    }

    private static String convertMember(String line, String className) {
        String res = line.replace(";", "");

        // @Override
        res = res.replaceAll("@Override\\s*", "override ");

        // Visibility
        res = res.replaceAll("\\bpublic\\s+", "");
        res = res.replaceAll("\\bstatic\\s+", "");

        // System.out.println
        res = res.replaceAll("System\\.out\\.println\\(", "println(");
        res = res.replaceAll("System\\.out\\.print\\(", "print(");

        // new ClassName( -> ClassName(
        res = res.replaceAll("\\bnew\\s+([a-zA-Z0-9_]+)\\(", "$1(");

        // instanceof -> is
        res = res.replaceAll("\\binstanceof\\b", "is");

        // Method declaration: Type name(params) {
        Pattern methodPat = Pattern.compile("([a-zA-Z0-9_<>\\[\\]]+)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{?");
        Matcher m = methodPat.matcher(res);
        if (m.find()) {
            String retType = m.group(1);
            String name = m.group(2);
            String params = m.group(3);

            // Skip control structures
            if (!name.equals("if") && !name.equals("for") && !name.equals("while") && !name.equals("switch") && !name.equals("catch")) {
                if (name.equals(className)) {
                    // Constructor
                    return "constructor(" + convertParams(params) + ") {";
                }
                String ktReturn = toKtType(retType);
                String ktParams = convertParams(params);
                String returnSuffix = ktReturn.equals("Unit") ? "" : ": " + ktReturn;
                return "fun " + name + "(" + ktParams + ")" + returnSuffix + (res.endsWith("{") ? " {" : "");
            }
        }

        // Field declaration: [final] Type name = val
        Pattern fieldPat = Pattern.compile("(final)?\\s*([a-zA-Z0-9_<>\\[\\]]+)\\s+([a-zA-Z0-9_]+)(?:\\s*=\\s*(.*))?");
        Matcher fm = fieldPat.matcher(res);
        if (fm.find()) {
            boolean isFinal = fm.group(1) != null;
            String type = fm.group(2);
            String name = fm.group(3);
            String val = fm.group(4);

            if (!name.equals("return") && !name.equals("throw") && !name.equals("import") && !type.equals("return")) {
                String ktType = toKtType(type);
                String keyword = isFinal ? "val" : "var";
                if (val != null && !val.trim().isEmpty()) {
                    return keyword + " " + name + ": " + ktType + " = " + val.trim();
                } else {
                    return keyword + " " + name + ": " + ktType + "? = null";
                }
            }
        }

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
                sb.append(words[1]).append(": ").append(toKtType(words[0]));
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
        return code.replaceAll("(?m)^\\s+$", "");
    }
}
