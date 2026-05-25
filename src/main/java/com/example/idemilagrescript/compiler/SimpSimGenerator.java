package com.example.idemilagrescript.compiler;

import com.example.idemilagrescript.utils.Symbol;
import com.example.idemilagrescript.utils.SymbolTable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpSimGenerator {

    private int internalLabelCount = 0;

    private final List<String> tacCode;
    private final SymbolTable symbolTable;
    private final List<String> machineCode = new ArrayList<>();

    // Captura as variáveis temporárias geradas (t1, t2...) para alocar na memória depois
    private final Set<String> temporaries = new LinkedHashSet<>();

    // Padrões Regex adaptados do seu Optimizer para ler o TAC
    private static final Pattern LABEL_P = Pattern.compile("^(\\S+):$");
    private static final Pattern ASSIGN_BINOP_P = Pattern.compile("^(\\S+) = (\\S+) (\\+|-|\\*|/|==|!=|>=|<=|>|<|&&|\\|\\|) (\\S+)$");
    private static final Pattern ASSIGN_COPY_P = Pattern.compile("^(\\S+) = (\\S+)$");
    private static final Pattern GOTO_P = Pattern.compile("^goto (\\S+)$");
    private static final Pattern IF_P = Pattern.compile("^if (\\S+) (==|!=|>=|<=|>|<) (\\S+) goto (\\S+)$");
    private static final Pattern IFFALSE_P = Pattern.compile("^ifFalse (\\S+) (==|!=|>=|<=|>|<) (\\S+) goto (\\S+)$");
    private static final Pattern RETURN_VAL_P = Pattern.compile("^return (\\S+)$");
    private static final Pattern RETURN_VOID_P = Pattern.compile("^return$");
    private static final Pattern ASSIGN_NOT_P = Pattern.compile("^(\\S+) = !(\\S+)$");
    private static final Pattern ASSIGN_CAST_P = Pattern.compile("^(\\S+) = \\((\\w+)\\) (\\S+)$");
    private final Map<String, Integer> stringLiterals = new LinkedHashMap<>();
    private int stringLiteralCount = 0;
    public SimpSimGenerator(List<String> tacCode, SymbolTable symbolTable) {
        this.tacCode = tacCode;
        this.symbolTable = symbolTable;
    }

    public List<String> generate() {
        machineCode.clear();
        temporaries.clear();

        machineCode.add("jmp MainCode");
        machineCode.add("");

        // Tradução das instruções TAC
        machineCode.add("MainCode:");
        for (String tacLine : tacCode) {
            translateLine(tacLine.trim());
        }

        // Fim da execução
        machineCode.add("halt");
        machineCode.add("");

        // Seção de Dados, alocando a memória usando 'db 0'
        generateDataSegment();

        return machineCode;
    }

    private void translateLine(String line) {
        machineCode.add("; TAC: " + line); // Deixa o TAC como comentário no Assembly para facilitar o debug, mostrando

        Matcher m;

        // Se caso for um labl
        m = LABEL_P.matcher(line);
        if (m.matches()) {
            machineCode.add(line);
            return;
        }

        // Se caso for um Goto incodicional
        m = GOTO_P.matcher(line);
        if (m.matches()) {
            machineCode.add("jmp " + m.group(1));
            return;
        }

        // Se for um return com valor (ex: return t1)
        m = RETURN_VAL_P.matcher(line);
        if (m.matches()) {
            loadOperand("RF", m.group(1)); // Joga o resultado no RF para destaque visual!
            machineCode.add("halt");
            return;
        }

        // Se for um return vazio
        m = RETURN_VOID_P.matcher(line);
        if (m.matches()) {
            machineCode.add("halt");
            return;
        }

        // Seria se tem uma Cópia/Atribuição Direta? (ex: x = 5 ou t1 = y)
        m = ASSIGN_COPY_P.matcher(line);
        if (m.matches()) {
            handleAssignCopy(m.group(1), m.group(2));
            return;
        }

        // Se for uma operação Binária (ex: t1 = a + b)
        m = ASSIGN_BINOP_P.matcher(line);
        if (m.matches()) {
            handleBinOp(m.group(1), m.group(2), m.group(3), m.group(4));
            return;
        }

        // Se caso for um if condicional
        m = IF_P.matcher(line);
        if (m.matches()) {
            handleIfCond(m.group(1), m.group(2), m.group(3), m.group(4), true);
            return;
        }

        m = IFFALSE_P.matcher(line);
        if (m.matches()) {
            handleIfCond(m.group(1), m.group(2), m.group(3), m.group(4), false);
            return;
        }

        // É uma negação lógica
        m = ASSIGN_NOT_P.matcher(line);
        if (m.matches()) {
            trackIfTemp(m.group(1));
            loadOperand("R1", m.group(2));
            machineCode.add("load R2, 1");
            machineCode.add("xor R3, R1, R2"); // Inverte o bit (1 vira 0, 0 vira 1)
            storeOperand("R3", m.group(1));
            return;
        }

        // Se for um Cast
        m = ASSIGN_CAST_P.matcher(line);
        if (m.matches()) {
            // O cast é apenas mover o valor
            handleAssignCopy(m.group(1), m.group(3));
            return;
        }
    }

    private void handleAssignCopy(String dest, String src) {
        trackIfTemp(dest);
        loadOperand("R1", src);
        storeOperand("R1", dest);
    }

    private void handleBinOp(String dest, String left, String op, String right) {
        trackIfTemp(dest);
        loadOperand("R1", left);
        loadOperand("R2", right);

        switch (op) {
            case "+":
                machineCode.add("addi R3, R1, R2");
                break;
            case "-":
                // Subtração em Complemento de 2: R1 - R2 = R1 + (-R2)
                // -R2 = (R2 XOR -1) + 1
                machineCode.add("load R4, -1");
                machineCode.add("xor R2, R2, R4");  // Inverte os bits
                machineCode.add("load R4, 1");
                machineCode.add("addi R2, R2, R4"); // Soma 1
                machineCode.add("addi R3, R1, R2"); // Realiza a subtração somando o negativo
                break;
            case "*":
                // Multiplicação por Somas Sucessivas
                String mulLoop = "MulLoop_" + (++internalLabelCount);
                String mulEnd = "MulEnd_" + internalLabelCount;

                machineCode.add("load R3, 0");      // R3 será o Acumulador (Resultado)
                machineCode.add("load R4, -1");     // R4 será o Decrementador (-1)
                machineCode.add("load R0, 0");      // R0 para comparar quando R2 chega a 0

                machineCode.add(mulLoop + ":");
                machineCode.add("jmpEQ R2=R0, " + mulEnd); // Se multiplicador chegou a 0, termina

                machineCode.add("addi R3, R3, R1"); // Resultado += R1
                machineCode.add("addi R2, R2, R4"); // Multiplicador -= 1
                machineCode.add("jmp " + mulLoop);

                machineCode.add(mulEnd + ":");
                break;

            case "/":
                // Divisão por Subtrações Sucessivas
                String divLoop = "DivLoop_" + (++internalLabelCount);
                String divEnd = "DivEnd_" + internalLabelCount;

                machineCode.add("load R3, 0");      // R3 será o Quociente (Resultado)
                machineCode.add("load R4, 1");      // R4 será o Incrementador (+1)

                // R5 é o subtraidor (Complemento de 2 do divisor: R5 = -R2)
                machineCode.add("load R6, -1");
                machineCode.add("xor R5, R2, R6");
                machineCode.add("addi R5, R5, R4");

                machineCode.add(divLoop + ":");
                // Condição de parada: Se R1 < R2 (ou seja, R1 <= R2 - 1), termina
                machineCode.add("addi R0, R2, R6"); // R0 = R2 - 1
                machineCode.add("jmpLE R1<=R0, " + divEnd);

                machineCode.add("addi R1, R1, R5"); // R1 = R1 - R2 (Faz a subtração)
                machineCode.add("addi R3, R3, R4"); // Quociente += 1
                machineCode.add("jmp " + divLoop);

                machineCode.add(divEnd + ":");
                break;
            case "&&":
                // Utiliza a instrução 'and' nativa do SimpSIM
                machineCode.add("and R3, R1, R2");
                break;
            case "||":
                // Utiliza a instrução 'or' nativa do SimpSIM
                machineCode.add("or R3, R1, R2");
                break;
            case "==": {
                String trueLabel = "RelTrue_" + (++internalLabelCount);
                String endLabel = "RelEnd_" + internalLabelCount;

                machineCode.add("move R0, R2");
                machineCode.add("jmpEQ R1=R0, " + trueLabel); // Se R1 == R2, pula pro verdadeiro

                machineCode.add("load R3, 0"); // Bloco Falso
                machineCode.add("jmp " + endLabel);

                machineCode.add(trueLabel + ":"); // Bloco Verdadeiro
                machineCode.add("load R3, 1");

                machineCode.add(endLabel + ":");
                break;
            }
            case "!=": {
                String skipLabel = "Skip_" + (++internalLabelCount);
                String endLabel = "RelEnd_" + internalLabelCount;

                machineCode.add("move R0, R2");
                machineCode.add("jmpEQ R1=R0, " + skipLabel); // Se for igual, pula pro bloco falso

                // Se NÃO pulou, é porque é diferente (Verdadeiro)
                machineCode.add("load R3, 1");
                machineCode.add("jmp " + endLabel);

                machineCode.add(skipLabel + ":"); // Bloco Falso
                machineCode.add("load R3, 0");

                machineCode.add(endLabel + ":");
                break;
            }
            case "<=": {
                String trueLabel = "RelTrue_" + (++internalLabelCount);
                String endLabel = "RelEnd_" + internalLabelCount;

                machineCode.add("move R0, R2");
                machineCode.add("jmpLE R1<=R0, " + trueLabel);

                machineCode.add("load R3, 0");
                machineCode.add("jmp " + endLabel);

                machineCode.add(trueLabel + ":");
                machineCode.add("load R3, 1");

                machineCode.add(endLabel + ":");
                break;
            }
            case ">=": {
                String trueLabel = "RelTrue_" + (++internalLabelCount);
                String endLabel = "RelEnd_" + internalLabelCount;

                // R1 >= R2 é a mesma coisa que R2 <= R1
                machineCode.add("move R0, R1");
                machineCode.add("jmpLE R2<=R0, " + trueLabel);

                machineCode.add("load R3, 0");
                machineCode.add("jmp " + endLabel);

                machineCode.add(trueLabel + ":");
                machineCode.add("load R3, 1");

                machineCode.add(endLabel + ":");
                break;
            }
            case "<": {
                String trueLabel = "RelTrue_" + (++internalLabelCount);
                String endLabel = "RelEnd_" + internalLabelCount;

                // R1 < R2 é a mesma coisa que R1 <= (R2 - 1)
                machineCode.add("load R4, -1");
                machineCode.add("addi R0, R2, R4");
                machineCode.add("jmpLE R1<=R0, " + trueLabel);

                machineCode.add("load R3, 0");
                machineCode.add("jmp " + endLabel);

                machineCode.add(trueLabel + ":");
                machineCode.add("load R3, 1");

                machineCode.add(endLabel + ":");
                break;
            }
            case ">": {
                String trueLabel = "RelTrue_" + (++internalLabelCount);
                String endLabel = "RelEnd_" + internalLabelCount;

                // R1 > R2 é a mesma coisa que R2 <= (R1 - 1)
                machineCode.add("load R4, -1");
                machineCode.add("addi R0, R1, R4");
                machineCode.add("jmpLE R2<=R0, " + trueLabel);

                machineCode.add("load R3, 0");
                machineCode.add("jmp " + endLabel);

                machineCode.add(trueLabel + ":");
                machineCode.add("load R3, 1");

                machineCode.add(endLabel + ":");
                break;
            }
        }

        storeOperand("R3", dest);
    }

    private void handleIfCond(String left, String op, String right, String label, boolean isTrue) {
        // Se for um ifFalse (gerado pelo curto-circuito), apenas invertemos a lógica matemática
        if (!isTrue) {
            op = invertOperator(op);
        }

        loadOperand("R1", left);
        loadOperand("R2", right);

        switch (op) {
            case "==":
                machineCode.add("move R0, R2");
                machineCode.add("jmpEQ R1=R0, " + label);
                break;
            case "<=":
                machineCode.add("move R0, R2");
                machineCode.add("jmpLE R1<=R0, " + label);
                break;
            case ">=":
                // R1 >= R2 é a mesma coisa que R2 <= R1
                machineCode.add("move R0, R1");
                machineCode.add("jmpLE R2<=R0, " + label);
                break;
            case "<":
                // R1 < R2 é a mesma coisa que R1 <= (R2 - 1)
                machineCode.add("load R3, -1");
                machineCode.add("addi R0, R2, R3"); // R0 = R2 - 1
                machineCode.add("jmpLE R1<=R0, " + label);
                break;
            case ">":
                // R1 > R2 é a mesma coisa que R2 <= (R1 - 1)
                machineCode.add("load R3, -1");
                machineCode.add("addi R0, R1, R3"); // R0 = R1 - 1
                machineCode.add("jmpLE R2<=R0, " + label);
                break;
            case "!=":
                // SimpSIM não tem jmpNEQ. Pulamos para uma label fantasma se for igual, senão fazemos o jump real.
                String skipLabel = "Skip_" + (++internalLabelCount);
                machineCode.add("move R0, R2");
                machineCode.add("jmpEQ R1=R0, " + skipLabel);
                machineCode.add("jmp " + label);
                machineCode.add(skipLabel + ":");
                break;
        }
    }

    private void trackIfTemp(String varName) {
        if (varName.matches("t\\d+")) {
            temporaries.add(varName);
        }
    }

    private void generateDataSegment() {
        machineCode.add("; --- SECAO DE DADOS ---");

        Set<String> allocated = new LinkedHashSet<>();

        // Variáveis da tabela de símbolos
        for (Symbol sym : symbolTable.getAllSymbols()) {
            if (!allocated.contains(sym.getName())) {
                machineCode.add(sym.getName() + ": db 0");
                allocated.add(sym.getName());
            }
        }

        // Temporários do TAC (t1, t2...)
        for (String temp : temporaries) {
            if (!allocated.contains(temp)) {
                machineCode.add(temp + ": db 0");
                allocated.add(temp);
            }
        }

        // Literais de string — alocados com seu ID como valor inicial
        if (!stringLiterals.isEmpty()) {
            machineCode.add("; --- LITERAIS DE STRING ---");
            for (Map.Entry<String, Integer> entry : stringLiterals.entrySet()) {
                if (!allocated.contains(entry.getKey())) {
                    machineCode.add("; \"" + entry.getKey() + "\" => ID " + entry.getValue());
                    machineCode.add(entry.getKey() + ": db " + entry.getValue());
                    allocated.add(entry.getKey());
                }
            }
        }
    }

    private String resolveIfStringLiteral(String operand) {
        if (operand.startsWith("\"") && operand.endsWith("\"")) {
            String key = operand.substring(1, operand.length() - 1);
            // str_ garante label válido mesmo para "1231" → str_1231
            String label = "str_" + key.replaceAll("[^a-zA-Z0-9_]", "_");
            stringLiterals.putIfAbsent(label, ++stringLiteralCount);
            return label;
        }
        return operand; // não é string literal, devolve como veio
    }

    private void loadOperand(String reg, String operand) {
        // 1. String literal com aspas tem prioridade
        String resolved = resolveIfStringLiteral(operand);
        if (!resolved.equals(operand)) {
            machineCode.add("load " + reg + ", [" + resolved + "]");
            return;
        }

        // 2. Char literal → valor ASCII  ('a' → 97)
        if (operand.startsWith("'") && operand.endsWith("'") && operand.length() == 3) {
            machineCode.add("load " + reg + ", " + (int) operand.charAt(1));
            return;
        }

        // 3. Numérico → trunca para inteiro (3.14 → 3, -2.9 → -2)
        if (operand.matches("-?\\d+(\\.\\d+)?")) {
            long intVal = (long) Double.parseDouble(operand);
            machineCode.add("load " + reg + ", " + intVal);
            return;
        }

        // 4. Variável/temporário → acesso por endereço
        machineCode.add("load " + reg + ", [" + operand + "]");
    }

    private void storeOperand(String reg, String operand) {
        machineCode.add("store " + reg + ", [" + operand + "]");
    }

    private String invertOperator(String op) {
        return switch (op) {
            case "==" -> "!=";
            case "!=" -> "==";
            case "<"  -> ">=";
            case ">"  -> "<=";
            case "<=" -> ">";
            case ">=" -> "<";
            default   -> op;
        };
    }



}