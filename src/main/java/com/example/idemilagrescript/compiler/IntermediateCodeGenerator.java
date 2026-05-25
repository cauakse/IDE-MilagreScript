package com.example.idemilagrescript.compiler;

import com.example.idemilagrescript.utils.Token;
import com.example.idemilagrescript.utils.TokenType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class IntermediateCodeGenerator {

    private final List<Token> tokens;
    private final List<String> code = new ArrayList<>();

    private int current = 0;
    private int tempCount = 0;
    private int labelCount = 0;

    private static final EnumSet<TokenType> TYPE_START = EnumSet.of(
            TokenType.VOID, TokenType.CHAR, TokenType.INT,
            TokenType.DOUBLE, TokenType.SHORT, TokenType.LONG,
            TokenType.STRING
    );

    private static final EnumSet<TokenType> COMMAND_START = EnumSet.of(
            TokenType.VOID, TokenType.CHAR, TokenType.INT,
            TokenType.DOUBLE, TokenType.SHORT, TokenType.LONG,
            TokenType.STRING, TokenType.IDENTIFICADOR, TokenType.IF,
            TokenType.WHILE, TokenType.RETURN, TokenType.ABRE_CHAVE
    );

    public IntermediateCodeGenerator(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<String> generate() {
        code.clear();
        current = 0;
        tempCount = 0;
        labelCount = 0;
        parsePrograma();
        return code;
    }

    private String newTemp() { return "t" + (++tempCount); }
    private String newLabel() { return "L" + (++labelCount); }
    private void emit(String instruction) { code.add(instruction); }
    private void emitLabel(String label) { code.add(label + ":"); }

    private void parsePrograma() {
        while (!isAtEnd()) {
            if (isCommandStart(peek().getType())) parseComando();
            else advance();
        }
    }

    private void parseComando() {
        TokenType type = peek().getType();

        if (isTypeStart(type)) parseDeclaracao();
        else if (type == TokenType.IDENTIFICADOR) parseAtribuicao();
        else if (type == TokenType.IF) parseCondicional();
        else if (type == TokenType.WHILE) parseRepeticao();
        else if (type == TokenType.RETURN) parseReturn();
        else if (type == TokenType.ABRE_CHAVE) parseBloco();
        else advance();
    }

    private void parseBloco() {
        consume(TokenType.ABRE_CHAVE);
        while (!isAtEnd() && !check(TokenType.FECHA_CHAVE)) {
            if (isCommandStart(peek().getType())) parseComando();
            else advance();
        }
        consume(TokenType.FECHA_CHAVE);
    }

    private void parseDeclaracao() {
        advance(); // tipo
        Token id = consume(TokenType.IDENTIFICADOR);

        if (match(TokenType.ATRIBUICAO)) {
            String value = parseExpressao();
            emit(id.getLexeme() + " = " + value);
        }
        consume(TokenType.PONTO_VIRGULA);
    }

    private void parseAtribuicao() {
        Token id = consume(TokenType.IDENTIFICADOR);
        consume(TokenType.ATRIBUICAO);
        String value = parseExpressao();
        emit(id.getLexeme() + " = " + value);
        consume(TokenType.PONTO_VIRGULA);
    }

    private void parseReturn() {
        consume(TokenType.RETURN);
        if (isExpressionStart(peek().getType())) {
            String value = parseExpressao();
            emit("return " + value);
        } else emit("return");
        consume(TokenType.PONTO_VIRGULA);
    }

    //PARTE BOOLEAN DO CURTO-CIRCUITO (SHORT-CIRCUIT)
    private BoolExpr parseBooleanExpression() {
        return parseBoolOr();
    }

    private BoolExpr parseBoolOr() {
        BoolExpr left = parseBoolAnd();
        while (match(TokenType.OR)) {
            BoolExpr right = parseBoolAnd();
            left = new OrExpr(left, right);
        }
        return left;
    }

    private BoolExpr parseBoolAnd() {
        BoolExpr left = parseBoolNot();
        while (match(TokenType.AND)) {
            BoolExpr right = parseBoolNot();
            left = new AndExpr(left, right);
        }
        return left;
    }

    private BoolExpr parseBoolNot() {
        if (match(TokenType.NOT)) {
            return new NotExpr(parseBoolNot());
        }
        return parseBoolPrimary();
    }

    private BoolExpr parseBoolPrimary() {
        String left = parseExpAritmetica();
        if (match(TokenType.IGUAL, TokenType.DIFERENTE,
                TokenType.MAIOR, TokenType.MENOR,
                TokenType.MAIOR_IGUAL, TokenType.MENOR_IGUAL)) {

            Token op = previous();
            String right = parseExpAritmetica();
            return new RelExpr(left, op.getLexeme(), right);
        }
        return new RelExpr(left, "!=", "0");
    }

    private void emitBoolean(BoolExpr expr, String trueLabel, String falseLabel) {
        if (expr instanceof RelExpr r) {
            emit("ifFalse " + r.left + " " + r.op + " " + r.right + " goto " + falseLabel);
        }

        else if (expr instanceof OrExpr o) {
            emitTrueJump(o.left, trueLabel);
            emitBoolean(o.right, trueLabel, falseLabel);
        }

        else if (expr instanceof AndExpr a) {
            emitFalseJump(a.left, falseLabel);
            emitBoolean(a.right, trueLabel, falseLabel);
        }

        else if (expr instanceof NotExpr n) {
            emitBoolean(n.expr, falseLabel, trueLabel);
        }
    }

    private void emitTrueJump(BoolExpr expr, String trueLabel) {

        if (expr instanceof RelExpr r) {
            emit("if " + r.left + " " + r.op + " " + r.right + " goto " + trueLabel);
        }

        else if (expr instanceof OrExpr o) {
            emitTrueJump(o.left, trueLabel);
            emitTrueJump(o.right, trueLabel);
        }

        else if (expr instanceof AndExpr a) {
            String skipLabel = newLabel();

            emitFalseJump(a.left, skipLabel);
            emitTrueJump(a.right, trueLabel);

            emitLabel(skipLabel);
        }

        else if (expr instanceof NotExpr n) {
            String skipLabel = newLabel();

            emitTrueJump(n.expr, skipLabel);
            emit("goto " + trueLabel);

            emitLabel(skipLabel);
        }
    }

    private void emitFalseJump(BoolExpr expr, String falseLabel) {
        if (expr instanceof RelExpr r) {
            emit("ifFalse " + r.left + " " + r.op + " " + r.right + " goto " + falseLabel);
        }

        else if (expr instanceof AndExpr a) {
            emitFalseJump(a.left, falseLabel);
            emitFalseJump(a.right, falseLabel);
        }

        else if (expr instanceof OrExpr o) {
            String skipLabel = newLabel();

            emitTrueJump(o.left, skipLabel);
            emitFalseJump(o.right, falseLabel);

            emitLabel(skipLabel);
        }

        else if (expr instanceof NotExpr n) {
            emitTrueJump(n.expr, falseLabel);
        }
    }

    //CONDICIONAL COM CURTO-CIRCUITO
    private void parseCondicional() {
        consume(TokenType.IF);
        consume(TokenType.ABRE_PAREN);
        BoolExpr condition = parseBooleanExpression();
        consume(TokenType.FECHA_PAREN);

        String trueLabel = newLabel();
        String falseLabel = newLabel();

        if (condition instanceof RelExpr r) {
            emit("if " + r.left + " " + r.op + " " + r.right + " goto " + trueLabel);
            emit("goto " + falseLabel);
        } else {
            emitBoolean(condition, trueLabel, falseLabel);
        }
        emitLabel(trueLabel);
        parseBlocoOuComando();

        if (match(TokenType.ELSE)) {
            String endLabel = newLabel();

            emit("goto " + endLabel);
            emitLabel(falseLabel);

            parseBlocoOuComando();

            emitLabel(endLabel);
        } else {
            emitLabel(falseLabel);
        }
    }

    //WHILE COM CURTO-CIRCUITO
    private void parseRepeticao() {
        consume(TokenType.WHILE);

        String beginLabel = newLabel();
        String bodyLabel = newLabel();
        String endLabel = newLabel();

        emitLabel(beginLabel);

        consume(TokenType.ABRE_PAREN);

        BoolExpr condition = parseBooleanExpression();

        consume(TokenType.FECHA_PAREN);

        if (condition instanceof RelExpr r) {

            emit("if " + r.left + " "
                    + r.op + " "
                    + r.right + " goto " + bodyLabel);

            emit("goto " + endLabel);

        } else {

            emitBoolean(condition, bodyLabel, endLabel);
        }

        emitLabel(bodyLabel);

        parseBlocoOuComando();

        emit("goto " + beginLabel);

        emitLabel(endLabel);
    }

    private void parseBlocoOuComando() {
        if (check(TokenType.ABRE_CHAVE)) {
            parseBloco();
        } else {
            parseComando();
        }
    }

    //EXPRESSÕES
    private String parseExpressao() { return parseExpLogicaOr(); }

    private String parseExpLogicaOr() {
        String left = parseExpLogicaAnd();
        while (match(TokenType.OR)) {
            Token op = previous();
            String right = parseExpLogicaAnd();
            String temp = newTemp();
            emit(temp + " = " + left + " " + op.getLexeme() + " " + right);
            left = temp;
        }
        return left;
    }

    private String parseExpLogicaAnd() {
        String left = parseExpLogicaNot();
        while (match(TokenType.AND)) {
            Token op = previous();
            String right = parseExpLogicaNot();
            String temp = newTemp();
            emit(temp + " = " + left + " " + op.getLexeme() + " " + right);
            left = temp;
        }
        return left;
    }

    private String parseExpLogicaNot() {
        if (match(TokenType.NOT)) {
            String value = parseExpLogicaNot();
            String temp = newTemp();
            emit(temp + " = !" + value);
            return temp;
        }
        return parseExpRelacional();
    }

    private String parseExpRelacional() {
        String left = parseExpAritmetica();
        if (match(TokenType.IGUAL, TokenType.DIFERENTE, TokenType.MAIOR, TokenType.MENOR, TokenType.MAIOR_IGUAL, TokenType.MENOR_IGUAL)) {
            Token op = previous();
            String right = parseExpAritmetica();
            String temp = newTemp();
            emit(temp + " = " + left + " " + op.getLexeme() + " " + right);
            return temp;
        }
        return left;
    }

    private String parseExpAritmetica() {
        String left = parseTermo();
        while (match(TokenType.MAIS, TokenType.MENOS)) {
            Token op = previous();
            String right = parseTermo();
            String temp = newTemp();
            emit(temp + " = " + left + " " + op.getLexeme() + " " + right);
            left = temp;
        }
        return left;
    }

    private String parseTermo() {
        String left = parseFator();
        while (match(TokenType.VEZES, TokenType.DIVISAO)) {
            Token op = previous();
            String right = parseFator();
            String temp = newTemp();
            emit(temp + " = " + left + " " + op.getLexeme() + " " + right);
            left = temp;
        }
        return left;
    }

    private String parseFator() {
        if (match(TokenType.IDENTIFICADOR, TokenType.NUMERO)) return previous().getLexeme();

        // STRING_LITERAL recebe aspas para não ser confundida com número ou variável
        if (match(TokenType.STRING_LITERAL)) return "\"" + previous().getLexeme() + "\"";

        if (check(TokenType.ABRE_PAREN)) {
            if (isCastExpression()) {
                consume(TokenType.ABRE_PAREN);
                Token castType = advance();
                consume(TokenType.FECHA_PAREN);

                String value = parseFator();
                String temp = newTemp();
                emit(temp + " = (" + castType.getLexeme() + ") " + value);
                return temp;
            }

            consume(TokenType.ABRE_PAREN);
            String value = parseExpressao();
            consume(TokenType.FECHA_PAREN);
            return value;
        }
        return "";
    }

    private boolean isCastExpression() {
        if (current + 2 < tokens.size()) {
            TokenType next = tokens.get(current + 1).getType();
            TokenType nextNext = tokens.get(current + 2).getType();
            return isTypeStart(next) && nextNext == TokenType.FECHA_PAREN;
        }
        return false;
    }

    private boolean isTypeStart(TokenType type) { return TYPE_START.contains(type); }
    private boolean isCommandStart(TokenType type) { return COMMAND_START.contains(type); }
    private boolean isExpressionStart(TokenType type) {
        return type == TokenType.IDENTIFICADOR || type == TokenType.NUMERO
                || type == TokenType.STRING_LITERAL || type == TokenType.ABRE_PAREN
                || type == TokenType.NOT;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) { advance(); return true; }
        }
        return false;
    }

    private Token consume(TokenType expected) { if (check(expected)) return advance(); return peek(); }
    private boolean check(TokenType type) { if (isAtEnd()) return type == TokenType.EOF; return peek().getType() == type; }
    private Token advance() { if (!isAtEnd()) current++; return previous(); }
    private boolean isAtEnd() { return peek().getType() == TokenType.EOF; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current == 0 ? 0 : current - 1); }
    private interface BoolExpr {}

    private static class RelExpr implements BoolExpr {
        String left;
        String op;
        String right;

        RelExpr(String left, String op, String right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    private static class OrExpr implements BoolExpr {
        BoolExpr left;
        BoolExpr right;

        OrExpr(BoolExpr left, BoolExpr right) {
            this.left = left;
            this.right = right;
        }
    }

    private static class AndExpr implements BoolExpr {
        BoolExpr left;
        BoolExpr right;

        AndExpr(BoolExpr left, BoolExpr right) {
            this.left = left;
            this.right = right;
        }
    }

    private static class NotExpr implements BoolExpr {
        BoolExpr expr;

        NotExpr(BoolExpr expr) {
            this.expr = expr;
        }
    }
}