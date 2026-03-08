package com.example.idemilagrescript.compiler;

public class LexError {

    private final int line;
    private final int column;
    private final int offset;
    private final int length;
    private final String message;

    public LexError(int line, int column, int offset, int length, String message) {
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.length = length;
        this.message = message;
    }

    public int getLine(){ return line; }
    public int getColumn(){ return column; }
    public int getOffset(){ return offset; }
    public int getLength(){ return length; }
    public String getMessage(){ return message; }
}
