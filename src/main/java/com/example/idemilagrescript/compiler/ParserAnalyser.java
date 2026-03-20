package com.example.idemilagrescript.compiler;

import com.example.idemilagrescript.tokens.Token;
import com.example.idemilagrescript.tokens.TokenType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ParserAnalyser {

    private final List<Token> tokens;
    private final List<LexError> errors = new ArrayList<>();
    private int current;

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
            TokenType.RETURN
    );

    public ParserAnalyser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<LexError> parse() {
        errors.clear();
        current = 0;

        parsePrograma();
        consume(TokenType.EOF, "fim de arquivo");

        return errors;
    }

    private void parsePrograma() {
        while (!isAtEnd() && isCommandStart(peek().getType())) {
            parseComando();
        }
    }

    private void parseComando() {
        int errorsBefore = errors.size();

        if (isTypeStart(peek().getType())) {
            parseDeclaracao();
        } else if (check(TokenType.IDENTIFICADOR)) {
            parseAtribuicao();
        } else if (check(TokenType.IF)) {
            parseCondicional();
        } else if (check(TokenType.WHILE)) {
            parseRepeticao();
        } else if (check(TokenType.RETURN)) {
            parseReturn();
        } else {
            addError(peek(), "Esperado início de comando, mas encontrado " + foundTokenText(peek()) + ".");
            synchronize();
            return;
        }

        if (errors.size() > errorsBefore) {
            synchronize();
        }
    }

    private void parseBloco() {
        consume(TokenType.ABRE_CHAVE, "'{'");

        while (!isAtEnd() && !check(TokenType.FECHA_CHAVE) && isCommandStart(peek().getType())) {
            parseComando();
        }

        consume(TokenType.FECHA_CHAVE, "'}'");
    }

    private void parseDeclaracao() {
        parseTipo();
        consume(TokenType.IDENTIFICADOR, "identificador");

        if (match(TokenType.ATRIBUICAO)) {
            parseExpressao();
        }

        consume(TokenType.PONTO_VIRGULA, "';'");
    }

    private void parseAtribuicao() {
        consume(TokenType.IDENTIFICADOR, "identificador");
        consume(TokenType.ATRIBUICAO, "'='");
        parseExpressao();

        if (!check(TokenType.PONTO_VIRGULA)) {
            addError(peek(), "Esperado ';' após expressão de atribuição, mas encontrado " + foundTokenText(peek()) + ".");
            return;
        }

        advance();
    }

    private void parseReturn() {
        consume(TokenType.RETURN, "'return'");

        if (isExpressionStart(peek().getType())) {
            parseExpressao();
        }

        consume(TokenType.PONTO_VIRGULA, "';'");
    }

    private void parseCondicional() {
        consume(TokenType.IF, "'if'");
        consume(TokenType.ABRE_PAREN, "'('");
        parseExpressao();
        consume(TokenType.FECHA_PAREN, "')'");

        parseBlocoOuComando();

        if (match(TokenType.ELSE)) {
            parseBlocoOuComando();
        }
    }

    private void parseRepeticao() {
        consume(TokenType.WHILE, "'while'");
        consume(TokenType.ABRE_PAREN, "'('");
        parseExpressao();

        if (!check(TokenType.FECHA_PAREN)) {
            addError(peek(), "Estrutura 'while' sem parêntese de fechamento ')'.");
        } else {
            advance();
        }

        parseBlocoOuComando();
    }

    private void parseBlocoOuComando() {
        if (check(TokenType.ABRE_CHAVE)) {
            parseBloco();
            return;
        }

        parseComando();
    }

    private void parseTipo() {
        if (!match(
                TokenType.VOID,
                TokenType.CHAR,
                TokenType.INT,
                TokenType.DOUBLE,
                TokenType.SHORT,
                TokenType.LONG,
                TokenType.STRING
        )) {
            addError(peek(), "Esperado tipo, mas encontrado " + foundTokenText(peek()) + ".");
        }
    }

    private void parseExpressao() {
        parseExpLogicaOr();
    }

    private void parseExpLogicaOr() {
        parseExpLogicaAnd();

        while (match(TokenType.OR)) {
            parseExpLogicaAnd();
        }
    }

    private void parseExpLogicaAnd() {
        parseExpLogicaNot();

        while (match(TokenType.AND)) {
            parseExpLogicaNot();
        }
    }

    private void parseExpLogicaNot() {
        while (match(TokenType.NOT)) {
            // Not can repeat: !!!expr
        }

        parseExpRelacional();
    }

    private void parseExpRelacional() {
        parseExpAritmetica();

        if (match(TokenType.IGUAL, TokenType.DIFERENTE, TokenType.MAIOR, TokenType.MENOR,
                TokenType.MAIOR_IGUAL, TokenType.MENOR_IGUAL)) {
            parseExpAritmetica();
        }
    }

    private void parseExpAritmetica() {
        parseTermo();

        while (match(TokenType.MAIS, TokenType.MENOS)) {
            parseTermo();
        }
    }

    private void parseTermo() {
        parseFator();

        while (match(TokenType.VEZES, TokenType.DIVISAO)) {
            parseFator();
        }
    }

    private void parseFator() {
        if (match(TokenType.IDENTIFICADOR, TokenType.NUMERO)) {
            return;
        }

        if (match(TokenType.ABRE_PAREN)) {
            parseExpAritmetica();
            consume(TokenType.FECHA_PAREN, "')'");
            return;
        }

        addError(peek(), "Esperado fator (identificador, número ou expressão entre parênteses), mas encontrado " + foundTokenText(peek()) + ".");
    }

    private Token consume(TokenType expected, String expectedLabel) {
        if (check(expected)) {
            return advance();
        }

        addError(peek(), "Esperado " + expectedLabel + ", mas encontrado " + foundTokenText(peek()) + ".");

        if (!isAtEnd()) {
            return advance();
        }

        return peek();
    }

    private void synchronize() {
        while (!isAtEnd()) {
            if (previous().getType() == TokenType.PONTO_VIRGULA) {
                return;
            }

            TokenType nextType = peek().getType();

            if (nextType == TokenType.FECHA_CHAVE || nextType == TokenType.ELSE || isCommandStart(nextType)) {
                return;
            }

            advance();
        }
    }

    private void addError(Token token, String message) {
        int length = Math.max(1, token.getLength());

        errors.add(new LexError(token.getLine(), token.getColumn(), token.getOffset(), length, message));
    }

    private String foundTokenText(Token token) {
        if (token.getType() == TokenType.EOF) {
            return "fim de arquivo";
        }

        if (token.getLexeme() == null || token.getLexeme().isBlank()) {
            return "'" + token.getType().name() + "'";
        }

        return "'" + token.getLexeme() + "'";
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
        if (current == 0) {
            return tokens.get(0);
        }

        return tokens.get(current - 1);
    }
}
