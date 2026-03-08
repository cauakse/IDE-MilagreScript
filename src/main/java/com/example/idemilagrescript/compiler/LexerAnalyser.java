package com.example.idemilagrescript.compiler;

import com.example.idemilagrescript.tokens.Token;
import com.example.idemilagrescript.tokens.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LexerAnalyser {

    private String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<LexError> errors = new ArrayList<>();

    private int start;
    private int current;

    private int line;
    private int column;
    private int startColumn;

    private static final Map<String, TokenType> keywords;

    public List<Token> getTokens () {
        return tokens;
    }
    static {
        keywords = new HashMap<>();
        keywords.put("void", TokenType.VOID);
        keywords.put("char", TokenType.CHAR);
        keywords.put("int", TokenType.INT);
        keywords.put("double", TokenType.DOUBLE);
        keywords.put("short", TokenType.SHORT);
        keywords.put("long", TokenType.LONG);
        keywords.put("string", TokenType.STRING);
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("while", TokenType.WHILE);
        keywords.put("return", TokenType.RETURN);
    }

    public List<LexError> analyze(String source) {

        this.source = source;

        tokens.clear();
        errors.clear();

        start = 0;
        current = 0;

        line = 1;
        column = 1;

        while (!isAtEnd()) {
            start = current;
            startColumn = column;
            scanToken();
        }

        tokens.add(new Token(TokenType.EOF, "", line));

        return errors;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char advance() {
        char c = source.charAt(current++);
        column++;
        return c;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, line));
    }

    private void scanToken() {

        char c = advance();

        switch (c) {

            case '(' -> addToken(TokenType.ABRE_PAREN);
            case ')' -> addToken(TokenType.FECHA_PAREN);
            case '{' -> addToken(TokenType.ABRE_CHAVE);
            case '}' -> addToken(TokenType.FECHA_CHAVE);
            case ';' -> addToken(TokenType.PONTO_VIRGULA);

            case '+' -> addToken(TokenType.MAIS);
            case '-' -> addToken(TokenType.MENOS);
            case '*' -> addToken(TokenType.VEZES);
            case '/' -> addToken(TokenType.DIVISAO);

            case '=' -> {
                if (match('=')) addToken(TokenType.IGUAL);
                else addToken(TokenType.ATRIBUICAO);
            }

            case '!' -> {
                if (match('=')) addToken(TokenType.DIFERENTE);
                else addToken(TokenType.NOT);
            }

            case '>' -> {
                if (match('=')) addToken(TokenType.MAIOR_IGUAL);
                else addToken(TokenType.MAIOR);
            }

            case '<' -> {
                if (match('=')) addToken(TokenType.MENOR_IGUAL);
                else addToken(TokenType.MENOR);
            }

            case '&' -> {
                if (match('&')) addToken(TokenType.AND);
                else addError("Unexpected '&'");
            }

            case '|' -> {
                if (match('|')) addToken(TokenType.OR);
                else addError("Unexpected '|'");
            }

            case ' ', '\r', '\t' -> {}

            case '\n' -> {
                line++;
                column = 1;
            }

            case '"' -> string();

            default -> {

                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    addError("Unexpected character: " + c);
                }
            }
        }
    }

    private void addError(String message) {

        int length = Math.max(1, current - start);

        errors.add(
                new LexError(
                        line,
                        startColumn,
                        start,
                        length,
                        message
                )
        );
    }

    private boolean match(char expected) {

        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        column++;

        return true;
    }

    private void number() {

        while (isDigit(peek())) advance();

        addToken(TokenType.NUMERO);
    }

    private void identifier() {

        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);

        TokenType type = keywords.get(text);

        if (type == null)
            type = TokenType.IDENTIFICADOR;

        tokens.add(new Token(type, text, line));
    }

    private void string() {

        while (peek() != '"' && !isAtEnd()) {

            if (peek() == '\n') {
                line++;
                column = 1;
            }

            advance();
        }

        if (isAtEnd()) {
            addError("Unterminated string");
            return;
        }

        advance();

        String value = source.substring(start + 1, current - 1);

        tokens.add(new Token(TokenType.STRING_LITERAL, value, line));
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}