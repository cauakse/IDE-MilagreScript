package com.example.idemilagrescript.tokens;

public class Token {
    public final TokenType type;
    public final String lexeme;
    public final int line;
    public final int column;
    public final int offset;
    public final int length;

    public Token(TokenType type, String lexeme, int line) {
        this(type, lexeme, line, 1, 0, Math.max(0, lexeme != null ? lexeme.length() : 0));
    }

    public Token(TokenType type, String lexeme, int line, int column, int offset, int length) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.length = length;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }


    @Override
    public String toString() {
        return String.format("Token(%-15s, '%s', linha %d, coluna %d)", type, lexeme, line, column);
    }
}
