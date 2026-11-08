package com.example.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaToSmaliCompiler {

    public static class CompileResult {
        public final boolean success;
        public final String smaliCode;
        public final String className;
        public final String packageName;
        public final String message;

        public CompileResult(boolean success, String smaliCode, String className, String packageName, String message) {
            this.success = success;
            this.smaliCode = smaliCode;
            this.className = className;
            this.packageName = packageName;
            this.message = message;
        }
    }

    public static CompileResult compile(String javaSource) {
        if (javaSource == null || javaSource.trim().isEmpty()) {
            return new CompileResult(false, "", "", "", "Java source code cannot be empty.");
        }

        try {
            // Normalize source: strip block comments and single-line comments
            String source = javaSource.replaceAll("/\\*[\\s\\S]*?\\*/", "");
            source = source.replaceAll("//.*", "");

            // Extract package
            String pkg = "";
            Matcher pkgMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)\\s*;").matcher(source);
            if (pkgMatcher.find()) {
                pkg = pkgMatcher.group(1).trim();
            }

            // Extract class declaration
            Pattern classPattern = Pattern.compile(
                    "(public|private|protected)?\\s*(final|abstract)?\\s*class\\s+([a-zA-Z0-9_]+)(?:\\s+extends\\s+([a-zA-Z0-9_.]+))?(?:\\s+implements\\s+([a-zA-Z0-9_,\\s.]+))?\\s*\\{");
            Matcher classMatcher = classPattern.matcher(source);

            if (!classMatcher.find()) {
                return new CompileResult(false, "", "", pkg, "No valid Java class declaration found.");
            }

            String accessMod = classMatcher.group(1) != null ? classMatcher.group(1) : "public";
            String finalMod = classMatcher.group(2) != null ? " " + classMatcher.group(2) : "";
            String className = classMatcher.group(3);
            String superClassName = classMatcher.group(4);
            String interfaces = classMatcher.group(5);

            String classSmaliType = "L" + (pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/") + className + ";";
            String superSmaliType = "Ljava/lang/Object;";
            if (superClassName != null && !superClassName.isEmpty()) {
                superSmaliType = toSmaliType(superClassName, pkg);
            }

            StringBuilder smali = new StringBuilder();
            smali.append(".class ").append(accessMod).append(finalMod).append(" ").append(classSmaliType).append("\n");
            smali.append(".super ").append(superSmaliType).append("\n");
            smali.append(".source \"").append(className).append(".java\"\n\n");

            if (interfaces != null && !interfaces.trim().isEmpty()) {
                String[] ifaces = interfaces.split(",");
                for (String iface : ifaces) {
                    iface = iface.trim();
                    if (!iface.isEmpty()) {
                        smali.append(".implements ").append(toSmaliType(iface, pkg)).append("\n");
                    }
                }
                smali.append("\n");
            }

            // Extract fields
            Pattern fieldPattern = Pattern.compile(
                    "(public|private|protected)?\\s*(static)?\\s*(final)?\\s*([a-zA-Z0-9_<>\\[\\]]+)\\s+([a-zA-Z0-9_]+)(?:\\s*=\\s*([^;]+))?;");
            Matcher fieldMatcher = fieldPattern.matcher(source);

            while (fieldMatcher.find()) {
                String fAccess = fieldMatcher.group(1) != null ? fieldMatcher.group(1) + " " : "";
                String fStatic = fieldMatcher.group(2) != null ? "static " : "";
                String fFinal = fieldMatcher.group(3) != null ? "final " : "";
                String fType = fieldMatcher.group(4);
                String fName = fieldMatcher.group(5);
                String fVal = fieldMatcher.group(6);

                if (fType.equals("return") || fType.equals("if") || fType.equals("for") || fType.equals("while")) {
                    continue;
                }

                String smaliFType = toSmaliType(fType, pkg);
                smali.append(".field ").append(fAccess).append(fStatic).append(fFinal).append(fName).append(":").append(smaliFType);
                if (fVal != null && !fVal.trim().isEmpty() && fStatic.contains("static") && fFinal.contains("final")) {
                    smali.append(" = ").append(fVal.trim());
                }
                smali.append("\n");
            }
            smali.append("\n");

            // Extract constructors & methods
            Pattern methodPattern = Pattern.compile(
                    "(public|private|protected)?\\s*(static)?\\s*(final|synchronized|abstract)?\\s*([a-zA-Z0-9_<>\\[\\]]+)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{([^}]*)\\}");
            Matcher methodMatcher = methodPattern.matcher(source);
            boolean hasConstructor = false;

            while (methodMatcher.find()) {
                String mAccess = methodMatcher.group(1) != null ? methodMatcher.group(1) + " " : "public ";
                String mStatic = methodMatcher.group(2) != null ? "static " : "";
                String mFinal = methodMatcher.group(3) != null ? "final " : "";
                String mReturnType = methodMatcher.group(4);
                String mName = methodMatcher.group(5);
                String mParams = methodMatcher.group(6);
                String mBody = methodMatcher.group(7);

                if (mName.equals("if") || mName.equals("while") || mName.equals("for") || mName.equals("switch")) {
                    continue;
                }

                boolean isConstructor = mName.equals(className);
                if (isConstructor) hasConstructor = true;

                String methodName = isConstructor ? "<init>" : mName;
                String smaliReturn = isConstructor ? "V" : toSmaliType(mReturnType, pkg);

                StringBuilder paramSig = new StringBuilder();
                List<String> paramTypes = new ArrayList<>();
                List<String> paramNames = new ArrayList<>();

                if (mParams != null && !mParams.trim().isEmpty()) {
                    String[] pList = mParams.split(",");
                    for (String p : pList) {
                        String[] parts = p.trim().split("\\s+");
                        if (parts.length >= 2) {
                            String pSmali = toSmaliType(parts[0], pkg);
                            paramSig.append(pSmali);
                            paramTypes.add(pSmali);
                            paramNames.add(parts[1]);
                        }
                    }
                }

                smali.append(".method ").append(mAccess).append(mStatic).append(mFinal);
                if (isConstructor) smali.append("constructor ");
                smali.append(methodName).append("(").append(paramSig).append(")").append(smaliReturn).append("\n");

                generateMethodBody(smali, isConstructor, superSmaliType, smaliReturn, mBody, mStatic.contains("static"), paramTypes, paramNames, classSmaliType);

                smali.append(".end method\n\n");
            }

            // If no constructor exists, inject default no-arg constructor
            if (!hasConstructor) {
                smali.append("# Default constructor\n");
                smali.append(".method public constructor <init>()V\n");
                smali.append("    .registers 1\n");
                smali.append("    invoke-direct {p0}, ").append(superSmaliType).append("-><init>()V\n");
                smali.append("    return-void\n");
                smali.append(".end method\n\n");
            }

            return new CompileResult(true, smali.toString(), className, pkg, "SUCCESS: Java compiled to Smali bytecode.");

        } catch (Exception e) {
            return new CompileResult(false, "", "", "", "Compilation Error: " + e.getMessage());
        }
    }

    private static void generateMethodBody(StringBuilder smali, boolean isConstructor, String superSmaliType,
                                           String returnType, String body, boolean isStatic,
                                           List<String> paramTypes, List<String> paramNames, String classSmaliType) {
        smali.append("    .registers 16\n");

        if (isConstructor) {
            smali.append("    invoke-direct {p0}, ").append(superSmaliType).append("-><init>()V\n");
        }

        String trimmed = body != null ? body.trim() : "";

        // Check for System.out.println
        Matcher printMatcher = Pattern.compile("System\\.out\\.println\\(([^)]+)\\);").matcher(trimmed);
        while (printMatcher.find()) {
            String arg = printMatcher.group(1).trim();
            smali.append("    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;\n");
            if (arg.startsWith("\"") && arg.endsWith("\"")) {
                smali.append("    const-string v1, ").append(arg).append("\n");
                smali.append("    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V\n");
            } else if (arg.matches("-?\\d+")) {
                smali.append("    const/4 v1, ").append(arg).append("\n");
                smali.append("    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(I)V\n");
            } else {
                smali.append("    invoke-virtual {v0, ").append(arg).append("}, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V\n");
            }
        }

        // Check for arithmetic return: return a + b; or return a * b; or return a - b;
        Matcher arithMatcher = Pattern.compile("return\\s+([a-zA-Z0-9_]+)\\s*([+\\-*/])\\s*([a-zA-Z0-9_]+);").matcher(trimmed);
        if (arithMatcher.find()) {
            String left = arithMatcher.group(1);
            String op = arithMatcher.group(2);
            String right = arithMatcher.group(3);

            int pLeftIdx = paramNames.indexOf(left);
            int pRightIdx = paramNames.indexOf(right);

            String regLeft = (pLeftIdx != -1) ? (isStatic ? "p" + pLeftIdx : "p" + (pLeftIdx + 1)) : "v0";
            String regRight = (pRightIdx != -1) ? (isStatic ? "p" + pRightIdx : "p" + (pRightIdx + 1)) : "v1";

            String opInst = "add-int";
            if ("-".equals(op)) opInst = "sub-int";
            else if ("*".equals(op)) opInst = "mul-int";
            else if ("/".equals(op)) opInst = "div-int";

            smali.append("    ").append(opInst).append(" v0, ").append(regLeft).append(", ").append(regRight).append("\n");
            smali.append("    return v0\n");
            return;
        }

        // Check for return statement
        Matcher returnMatcher = Pattern.compile("return(?:\\s+([^;]+))?;").matcher(trimmed);
        if (returnMatcher.find()) {
            String retVal = returnMatcher.group(1);
            if (retVal == null || retVal.trim().isEmpty() || returnType.equals("V")) {
                smali.append("    return-void\n");
            } else {
                retVal = retVal.trim();
                int paramIdx = paramNames.indexOf(retVal);

                if (paramIdx != -1) {
                    String paramReg = isStatic ? "p" + paramIdx : "p" + (paramIdx + 1);
                    if (returnType.startsWith("L") || returnType.startsWith("[")) {
                        smali.append("    return-object ").append(paramReg).append("\n");
                    } else {
                        smali.append("    return ").append(paramReg).append("\n");
                    }
                } else if (retVal.startsWith("\"") && retVal.endsWith("\"")) {
                    smali.append("    const-string v0, ").append(retVal).append("\n");
                    smali.append("    return-object v0\n");
                } else if (retVal.equals("true")) {
                    smali.append("    const/4 v0, 0x1\n");
                    smali.append("    return v0\n");
                } else if (retVal.equals("false")) {
                    smali.append("    const/4 v0, 0x0\n");
                    smali.append("    return v0\n");
                } else if (retVal.matches("-?\\d+")) {
                    smali.append("    const/4 v0, ").append(retVal).append("\n");
                    smali.append("    return v0\n");
                } else if (returnType.startsWith("L") || returnType.startsWith("[")) {
                    if (retVal.equals("null")) {
                        smali.append("    const/4 v0, 0x0\n");
                    } else {
                        smali.append("    const/4 v0, 0x0\n");
                    }
                    smali.append("    return-object v0\n");
                } else {
                    smali.append("    const/4 v0, 0x0\n");
                    smali.append("    return v0\n");
                }
            }
        } else {
            if (returnType.equals("V")) {
                smali.append("    return-void\n");
            } else if (returnType.equals("I") || returnType.equals("Z")) {
                smali.append("    const/4 v0, 0x0\n");
                smali.append("    return v0\n");
            } else {
                smali.append("    const/4 v0, 0x0\n");
                smali.append("    return-object v0\n");
            }
        }
    }

    private static String toSmaliType(String type, String currentPkg) {
        if (type == null) return "V";
        type = type.trim();

        if (type.endsWith("[]")) {
            return "[" + toSmaliType(type.substring(0, type.length() - 2), currentPkg);
        }

        switch (type) {
            case "void": return "V";
            case "boolean": return "Z";
            case "byte": return "B";
            case "short": return "S";
            case "char": return "C";
            case "int": return "I";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            case "String": return "Ljava/lang/String;";
            case "Object": return "Ljava/lang/Object;";
            case "Integer": return "Ljava/lang/Integer;";
            case "Boolean": return "Ljava/lang/Boolean;";
            case "List": return "Ljava/util/List;";
            case "ArrayList": return "Ljava/util/ArrayList;";
            case "Map": return "Ljava/util/Map;";
            case "HashMap": return "Ljava/util/HashMap;";
            default:
                if (type.contains(".")) {
                    return "L" + type.replace('.', '/') + ";";
                }
                if (!currentPkg.isEmpty()) {
                    return "L" + currentPkg.replace('.', '/') + "/" + type + ";";
                }
                return "L" + type + ";";
        }
    }
}
