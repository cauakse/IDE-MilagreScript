package com.example.idemilagrescript.compiler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntermediateCodeOptimizer {

    private static final Pattern LABEL_P = Pattern.compile("^(\\S+):$");
    private static final Pattern ASSIGN_BINOP_P = Pattern.compile(
            "^(\\S+) = (\\S+) (\\+|-|\\*|/|==|!=|>=|<=|>|<|&&|\\|\\|) (\\S+)$");
    private static final Pattern ASSIGN_CAST_P = Pattern.compile(
            "^(\\S+) = \\((\\w+)\\) (\\S+)$");
    private static final Pattern ASSIGN_NOT_P = Pattern.compile(
            "^(\\S+) = !(\\S+)$");
    private static final Pattern ASSIGN_COPY_P = Pattern.compile(
            "^(\\S+) = (\\S+)$");
    private static final Pattern GOTO_P = Pattern.compile(
            "^goto (\\S+)$");
    private static final Pattern IF_P = Pattern.compile(
            "^if (\\S+) (==|!=|>=|<=|>|<) (\\S+) goto (\\S+)$");
    private static final Pattern IFFALSE_P = Pattern.compile(
            "^ifFalse (\\S+) (==|!=|>=|<=|>|<) (\\S+) goto (\\S+)$");
    private static final Pattern RETURN_VAL_P = Pattern.compile(
            "^return (\\S+)$");

    public List<String> optimize(List<String> code) {
        List<String> result = new ArrayList<>(code);
        boolean changed = true;
        int iterations = 0;
        while (changed && iterations < 10) {
            List<String> before = new ArrayList<>(result);
            result = constantFolding(result);
            result = constantPropagation(result);
            result = copyPropagation(result);
            result = algebraicSimplification(result);
            result = commonSubexpressionElimination(result);
            result = deadCodeElimination(result);
            result = redundantGotoElimination(result);
            result = unreachableCodeElimination(result);
            changed = !result.equals(before);
            iterations++;
        }
        return result;
    }

    private List<String> constantFolding(List<String> code) {
        List<String> result = new ArrayList<>();
        for (String line : code) {
            Matcher m = ASSIGN_BINOP_P.matcher(line);
            if (m.matches() && isNumeric(m.group(2)) && isNumeric(m.group(4))) {
                Double val = evaluate(parseNum(m.group(2)), m.group(3), parseNum(m.group(4)));
                if (val != null) {
                    result.add(m.group(1) + " = " + formatNum(val));
                    continue;
                }
            }

            Matcher notM = ASSIGN_NOT_P.matcher(line);
            if (notM.matches() && isNumeric(notM.group(2))) {
                result.add(notM.group(1) + " = " + (parseNum(notM.group(2)) == 0.0 ? "1" : "0"));
                continue;
            }

            Matcher ifM = IF_P.matcher(line);
            if (ifM.matches() && isNumeric(ifM.group(1)) && isNumeric(ifM.group(3))) {
                Double val = evaluate(parseNum(ifM.group(1)), ifM.group(2), parseNum(ifM.group(3)));
                if (val != null) {
                    if (val != 0.0) result.add("goto " + ifM.group(4));
                    continue;
                }
            }

            Matcher ifFM = IFFALSE_P.matcher(line);
            if (ifFM.matches() && isNumeric(ifFM.group(1)) && isNumeric(ifFM.group(3))) {
                Double val = evaluate(parseNum(ifFM.group(1)), ifFM.group(2), parseNum(ifFM.group(3)));
                if (val != null) {
                    if (val == 0.0) result.add("goto " + ifFM.group(4));
                    continue;
                }
            }

            result.add(line);
        }
        return result;
    }

    private List<String> constantPropagation(List<String> code) {
        Map<String, String> constants = new LinkedHashMap<>();
        List<String> result = new ArrayList<>();
        for (String line : code) {
            if (LABEL_P.matcher(line).matches()) {
                constants.clear();
                result.add(line);
                continue;
            }

            if (GOTO_P.matcher(line).matches()) {
                result.add(line);
                continue;
            }

            Matcher binM = ASSIGN_BINOP_P.matcher(line);
            if (binM.matches()) {
                String dest = binM.group(1);
                String left = prop(binM.group(2), constants);
                String op = binM.group(3);
                String right = prop(binM.group(4), constants);
                constants.remove(dest);
                result.add(dest + " = " + left + " " + op + " " + right);
                continue;
            }

            Matcher castM = ASSIGN_CAST_P.matcher(line);
            if (castM.matches()) {
                String dest = castM.group(1);
                String src = prop(castM.group(3), constants);
                constants.remove(dest);
                result.add(dest + " = (" + castM.group(2) + ") " + src);
                continue;
            }

            Matcher notM = ASSIGN_NOT_P.matcher(line);
            if (notM.matches()) {
                String dest = notM.group(1);
                String src = prop(notM.group(2), constants);
                constants.remove(dest);
                result.add(dest + " = !" + src);
                continue;
            }

            Matcher copyM = ASSIGN_COPY_P.matcher(line);
            if (copyM.matches()) {
                String dest = copyM.group(1);
                String src = prop(copyM.group(2), constants);
                if (isNumeric(src)) constants.put(dest, src);
                else constants.remove(dest);
                result.add(dest + " = " + src);
                continue;
            }

            Matcher ifM = IF_P.matcher(line);
            if (ifM.matches()) {
                result.add("if " + prop(ifM.group(1), constants) + " " + ifM.group(2)
                        + " " + prop(ifM.group(3), constants) + " goto " + ifM.group(4));
                continue;
            }

            Matcher ifFM = IFFALSE_P.matcher(line);
            if (ifFM.matches()) {
                result.add("ifFalse " + prop(ifFM.group(1), constants) + " " + ifFM.group(2)
                        + " " + prop(ifFM.group(3), constants) + " goto " + ifFM.group(4));
                continue;
            }

            Matcher retM = RETURN_VAL_P.matcher(line);
            if (retM.matches()) {
                result.add("return " + prop(retM.group(1), constants));
                continue;
            }

            result.add(line);
        }
        return result;
    }

    private List<String> copyPropagation(List<String> code) {
        Map<String, String> copies = new LinkedHashMap<>();
        List<String> result = new ArrayList<>();
        for (String line : code) {
            if (LABEL_P.matcher(line).matches()) {
                copies.clear();
                result.add(line);
                continue;
            }

            if (GOTO_P.matcher(line).matches()) {
                result.add(line);
                continue;
            }

            Matcher binM = ASSIGN_BINOP_P.matcher(line);
            if (binM.matches()) {
                String dest = binM.group(1);
                String left = prop(binM.group(2), copies);
                String op = binM.group(3);
                String right = prop(binM.group(4), copies);
                invalidateCopies(copies, dest);
                result.add(dest + " = " + left + " " + op + " " + right);
                continue;
            }

            Matcher castM = ASSIGN_CAST_P.matcher(line);
            if (castM.matches()) {
                String dest = castM.group(1);
                String src = prop(castM.group(3), copies);
                invalidateCopies(copies, dest);
                result.add(dest + " = (" + castM.group(2) + ") " + src);
                continue;
            }

            Matcher notM = ASSIGN_NOT_P.matcher(line);
            if (notM.matches()) {
                String dest = notM.group(1);
                String src = prop(notM.group(2), copies);
                invalidateCopies(copies, dest);
                result.add(dest + " = !" + src);
                continue;
            }

            Matcher copyM = ASSIGN_COPY_P.matcher(line);
            if (copyM.matches()) {
                String dest = copyM.group(1);
                String src = prop(copyM.group(2), copies);
                invalidateCopies(copies, dest);
                if (!isNumeric(src) && isTemp(dest)) copies.put(dest, src);
                result.add(dest + " = " + src);
                continue;
            }

            Matcher ifM = IF_P.matcher(line);
            if (ifM.matches()) {
                result.add("if " + prop(ifM.group(1), copies) + " " + ifM.group(2)
                        + " " + prop(ifM.group(3), copies) + " goto " + ifM.group(4));
                continue;
            }

            Matcher ifFM = IFFALSE_P.matcher(line);
            if (ifFM.matches()) {
                result.add("ifFalse " + prop(ifFM.group(1), copies) + " " + ifFM.group(2)
                        + " " + prop(ifFM.group(3), copies) + " goto " + ifFM.group(4));
                continue;
            }

            Matcher retM = RETURN_VAL_P.matcher(line);
            if (retM.matches()) {
                result.add("return " + prop(retM.group(1), copies));
                continue;
            }

            result.add(line);
        }
        return result;
    }

    private List<String> algebraicSimplification(List<String> code) {
        List<String> result = new ArrayList<>();
        for (String line : code) {
            Matcher m = ASSIGN_BINOP_P.matcher(line);
            if (m.matches()) {
                String s = simplify(m.group(1), m.group(2), m.group(3), m.group(4));
                result.add(s != null ? s : line);
                continue;
            }
            result.add(line);
        }
        return result;
    }

    private List<String> commonSubexpressionElimination(List<String> code) {
        Map<String, String> exprToTemp = new LinkedHashMap<>();
        List<String> result = new ArrayList<>();
        for (String line : code) {
            if (LABEL_P.matcher(line).matches()) {
                exprToTemp.clear();
                result.add(line);
                continue;
            }
            Matcher binM = ASSIGN_BINOP_P.matcher(line);
            if (binM.matches()) {
                String dest = binM.group(1);
                String left = binM.group(2);
                String op = binM.group(3);
                String right = binM.group(4);
                String expr = left + " " + op + " " + right;
                invalidateExprs(exprToTemp, dest);
                if (exprToTemp.containsKey(expr)) {
                    result.add(dest + " = " + exprToTemp.get(expr));
                } else {
                    if (!dest.equals(left) && !dest.equals(right)) {
                        exprToTemp.put(expr, dest);
                    }
                    result.add(line);
                }
                continue;
            }
            String assignDest = getAssignDest(line);
            if (assignDest != null) invalidateExprs(exprToTemp, assignDest);
            result.add(line);
        }
        return result;
    }

    private List<String> deadCodeElimination(List<String> code) {
        Set<String> used = new HashSet<>();
        for (String line : code) {
            Matcher m;
            m = ASSIGN_BINOP_P.matcher(line);
            if (m.matches()) { used.add(m.group(2)); used.add(m.group(4)); continue; }
            m = ASSIGN_CAST_P.matcher(line);
            if (m.matches()) { used.add(m.group(3)); continue; }
            m = ASSIGN_NOT_P.matcher(line);
            if (m.matches()) { used.add(m.group(2)); continue; }
            m = ASSIGN_COPY_P.matcher(line);
            if (m.matches()) { used.add(m.group(2)); continue; }
            m = IF_P.matcher(line);
            if (m.matches()) { used.add(m.group(1)); used.add(m.group(3)); continue; }
            m = IFFALSE_P.matcher(line);
            if (m.matches()) { used.add(m.group(1)); used.add(m.group(3)); continue; }
            m = RETURN_VAL_P.matcher(line);
            if (m.matches()) { used.add(m.group(1)); }
        }
        List<String> result = new ArrayList<>();
        for (String line : code) {
            String dest = getAssignDest(line);
            if (dest != null && isTemp(dest) && !used.contains(dest)) continue;
            Matcher copyM = ASSIGN_COPY_P.matcher(line);
            if (copyM.matches() && copyM.group(1).equals(copyM.group(2))) continue;
            result.add(line);
        }
        return result;
    }

    private List<String> redundantGotoElimination(List<String> code) {
        List<String> pass1 = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            Matcher gotoM = GOTO_P.matcher(code.get(i));
            if (gotoM.matches() && i + 1 < code.size()) {
                Matcher nextLabel = LABEL_P.matcher(code.get(i + 1));
                if (nextLabel.matches() && nextLabel.group(1).equals(gotoM.group(1))) continue;
            }
            pass1.add(code.get(i));
        }
        Set<String> referenced = new HashSet<>();
        for (String line : pass1) {
            Matcher m;
            m = GOTO_P.matcher(line);
            if (m.matches()) { referenced.add(m.group(1)); continue; }
            m = IF_P.matcher(line);
            if (m.matches()) { referenced.add(m.group(4)); continue; }
            m = IFFALSE_P.matcher(line);
            if (m.matches()) { referenced.add(m.group(4)); }
        }
        List<String> pass2 = new ArrayList<>();
        for (String line : pass1) {
            Matcher labelM = LABEL_P.matcher(line);
            if (labelM.matches() && !referenced.contains(labelM.group(1))) continue;
            pass2.add(line);
        }
        return pass2;
    }

    private List<String> unreachableCodeElimination(List<String> code) {
        List<String> result = new ArrayList<>();
        boolean unreachable = false;
        for (String line : code) {
            if (LABEL_P.matcher(line).matches()) {
                unreachable = false;
                result.add(line);
                continue;
            }
            if (unreachable) continue;
            result.add(line);
            if (GOTO_P.matcher(line).matches() || line.equals("return")
                    || RETURN_VAL_P.matcher(line).matches()) {
                unreachable = true;
            }
        }
        return result;
    }

    private String prop(String name, Map<String, String> map) {
        return map.getOrDefault(name, name);
    }

    private void invalidateCopies(Map<String, String> copies, String name) {
        copies.remove(name);
        copies.values().removeIf(v -> v.equals(name));
    }

    private void invalidateExprs(Map<String, String> exprToTemp, String var) {
        exprToTemp.entrySet().removeIf(e -> {
            if (e.getValue().equals(var)) return true;
            String[] parts = e.getKey().split(" ");
            return parts.length >= 3 && (parts[0].equals(var) || parts[2].equals(var));
        });
    }

    private String getAssignDest(String line) {
        Matcher m;
        m = ASSIGN_BINOP_P.matcher(line); if (m.matches()) return m.group(1);
        m = ASSIGN_CAST_P.matcher(line); if (m.matches()) return m.group(1);
        m = ASSIGN_NOT_P.matcher(line); if (m.matches()) return m.group(1);
        m = ASSIGN_COPY_P.matcher(line); if (m.matches()) return m.group(1);
        return null;
    }

    private String simplify(String dest, String left, String op, String right) {
        switch (op) {
            case "+":
                if (isZero(right)) return dest + " = " + left;
                if (isZero(left)) return dest + " = " + right;
                break;
            case "-":
                if (isZero(right)) return dest + " = " + left;
                if (left.equals(right)) return dest + " = 0";
                break;
            case "*":
                if (isOne(right)) return dest + " = " + left;
                if (isOne(left)) return dest + " = " + right;
                if (isZero(left) || isZero(right)) return dest + " = 0";
                break;
            case "/":
                if (isOne(right)) return dest + " = " + left;
                if (left.equals(right) && !isZero(left)) return dest + " = 1";
                break;
        }
        return null;
    }

    private boolean isTemp(String name) { return name.matches("t\\d+"); }

    private boolean isNumeric(String s) {
        try { Double.parseDouble(s); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private double parseNum(String s) { return Double.parseDouble(s); }

    private String formatNum(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) return String.valueOf((long) val);
        return String.valueOf(val);
    }

    private boolean isZero(String s) { return isNumeric(s) && parseNum(s) == 0.0; }
    private boolean isOne(String s) { return isNumeric(s) && parseNum(s) == 1.0; }

    private Double evaluate(double left, String op, double right) {
        return switch (op) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> right != 0 ? left / right : null;
            case "==" -> left == right ? 1.0 : 0.0;
            case "!=" -> left != right ? 1.0 : 0.0;
            case ">" -> left > right ? 1.0 : 0.0;
            case "<" -> left < right ? 1.0 : 0.0;
            case ">=" -> left >= right ? 1.0 : 0.0;
            case "<=" -> left <= right ? 1.0 : 0.0;
            case "&&" -> (left != 0 && right != 0) ? 1.0 : 0.0;
            case "||" -> (left != 0 || right != 0) ? 1.0 : 0.0;
            default -> null;
        };
    }
}
