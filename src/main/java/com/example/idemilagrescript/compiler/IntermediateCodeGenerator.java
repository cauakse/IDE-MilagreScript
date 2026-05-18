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
            TokenType.VOID, 
            TokenType.CHAR, 
            TokenType.INT,
            TokenType.DOUBLE, 
            TokenType.SHORT, 
            TokenType.LONG, 
            TokenType.STRING
    );

    private static final EnumSet<TokenType> COMMAND_START = EnumSet.of(
            TokenType.VOID, 
            TokenType.CHAR, 
            TokenType.INT,
            TokenType.DOUBLE, 
            TokenType.SHORT, 
            TokenType.LONG, 
            TokenType.STRING,
            TokenType.IDENTIFICADOR, 
            TokenType.IF, 
            TokenType.WHILE, 
            TokenType.RETURN,
            TokenType.ABRE_CHAVE
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

    private String newTemp() {
        return "t" + (++tempCount);
    }

    private String newLabel() {
        return "L" + (++labelCount);
    }

    private void emit(String instruction) {
        code.add(instruction);
    }

    private void emitLabel(String label) {
        code.add(label + ":");
    }

    private void parsePrograma() {
        while (!isAtEnd()) {
            if (isCommandStart(peek().getType())) {
                parseComando();
            } else {
                advance();
            }
        }
    }

    private void parseComando() {
        TokenType type = peek().getType();

        if (isTypeStart(type)) {
            parseDeclaracao();
        } else if (type == TokenType.IDENTIFICADOR) {
            parseAtribuicao();
        } else if (type == TokenType.IF) {
            parseCondicional();
        } else if (type == TokenType.WHILE) {
            parseRepeticao();
        } else if (type == TokenType.RETURN) {
            parseReturn();
        } else if (type == TokenType.ABRE_CHAVE) {
            parseBloco();
        } else {
            advance();
        }
    }

    private void parseBloco() {
        consume(TokenType.ABRE_CHAVE);

        while (!isAtEnd() && !check(TokenType.FECHA_CHAVE)) {
            if (isCommandStart(peek().getType())) {
                parseComando();
            } else {
                advance();
            }
        }

        consume(TokenType.FECHA_CHAVE);
    }

    private void parseDeclaracao() {
        advance(); //tipo

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
        } else {
            emit("return");
        }

        consume(TokenType.PONTO_VIRGULA);
    }

    private void parseCondicional() {
        consume(TokenType.IF);
        consume(TokenType.ABRE_PAREN);

        Condition condition = parseCondicao();

        consume(TokenType.FECHA_PAREN);

        String trueLabel = newLabel();
        String falseLabel = newLabel();
        String endLabel = newLabel();

        emit("if " + condition.left + " " + condition.operator + " " + condition.right + " goto " + trueLabel);
        emit("goto " + falseLabel);

        emitLabel(trueLabel);
        parseBlocoOuComando();
        emit("goto " + endLabel);

        emitLabel(falseLabel);

        if (match(TokenType.ELSE)) {
            parseBlocoOuComando();
        }

        emitLabel(endLabel);
    }

    private void parseRepeticao() {
        consume(TokenType.WHILE);

        String beginLabel = newLabel();
        String bodyLabel = newLabel();
        String endLabel = newLabel();

        emitLabel(beginLabel);

        consume(TokenType.ABRE_PAREN);
        Condition condition = parseCondicao();
        consume(TokenType.FECHA_PAREN);

        emit("if " + condition.left + " " + condition.operator + " " + condition.right + " goto " + bodyLabel);
        emit("goto " + endLabel);

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

    private Condition parseCondicao() {
        String left = parseExpAritmetica();

        if (match(TokenType.IGUAL, TokenType.DIFERENTE,
                TokenType.MAIOR, TokenType.MENOR,
                TokenType.MAIOR_IGUAL, TokenType.MENOR_IGUAL)) {

            Token op = previous();
            String right = parseExpAritmetica();

            return new Condition(left, op.getLexeme(), right);
        }

        return new Condition(left, "!=", "0");
    }

    private String parseExpressao() {
        return parseExpLogicaOr();
    }

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

        if (match(TokenType.IGUAL, TokenType.DIFERENTE,
                TokenType.MAIOR, TokenType.MENOR,
                TokenType.MAIOR_IGUAL, TokenType.MENOR_IGUAL)) {

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
        if (match(TokenType.IDENTIFICADOR, TokenType.NUMERO, TokenType.STRING_LITERAL)) {
            return previous().getLexeme();
        }

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

    private boolean isTypeStart(TokenType type) {
        return TYPE_START.contains(type);
    }

    private boolean isCommandStart(TokenType type) {
        return COMMAND_START.contains(type);
    }

    private boolean isExpressionStart(TokenType type) {
        return type == TokenType.IDENTIFICADOR
                || type == TokenType.NUMERO
                || type == TokenType.STRING_LITERAL
                || type == TokenType.ABRE_PAREN
                || type == TokenType.NOT;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType expected) {
        if (check(expected)) {
            return advance();
        }

        return peek();
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return type == TokenType.EOF;
        }

        return peek().getType() == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }

        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current == 0 ? 0 : current - 1);
    }

    private static class Condition {
        String left;
        String operator;
        String right;

        Condition(String left, String operator, String right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
    }
}