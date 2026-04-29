package com.example.idemilagrescript.utils;

import com.example.idemilagrescript.utils.TokenType;

public class Symbol {

    private final String name; // Nome da Variável
    private final TokenType type; // Qual o tipo declarado
    private final int line; // Posição a qual foi declarada no código, relacionado a linha
    private final int column; // Posição a qual foi declarada no código, relacionado a coluna
    private final int offset; // Posição absoluta do texto
    private int scopeDepth;

    private boolean initialized; // Flag para saber se a varáivel recebeu algum parâmetro "int a = 20;"
    private boolean used; // Flag para saber se a variável foi lida em alguma expressão

    public Symbol(String name, TokenType type, int line, int column, int offset) {
        this.name        = name;
        this.type        = type;
        this.line        = line;
        this.column      = column;
        this.offset      = offset;
        this.scopeDepth  = 0;
        this.initialized = false;
        this.used        = false;
    }

    public String getName()    { return name;    }
    public TokenType getType() { return type;    }
    public int getLine()       { return line;    }
    public int getColumn()     { return column;  }
    public int getOffset()     { return offset;  }

    public int getScopeDepth()             { return scopeDepth; }
    public void setScopeDepth(int depth)   { this.scopeDepth = depth; }

    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean initialized) { this.initialized = initialized; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    @Override
    public String toString() {
        return String.format("Symbol(%-12s | tipo=%-6s | linha=%-3d | col=%-3d | init=%-5b | usado=%b)",
                name, type, line, column, initialized, used);
    }
}