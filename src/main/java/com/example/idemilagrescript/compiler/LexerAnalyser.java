package com.example.idemilagrescript.compiler;

import com.example.idemilagrescript.tokens.Token;
import com.example.idemilagrescript.tokens.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LexerAnalyser {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, TokenType> keywords;

    static { //Reconher os tokens que foram digitados indo la no Token Type para identificação
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

    public LexerAnalyser(String source) {
        this.source = source;
    }

}
