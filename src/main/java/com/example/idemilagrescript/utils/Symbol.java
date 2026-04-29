package com.example.idemilagrescript.utils;

import com.example.idemilagrescript.utils.TokenType;

public class Symbol {

    private final String name;
    private final TokenType type;
    private final int line;
    private final int column;
    private final int offset;
    private int scopeDepth;

    private boolean initialized;
    private boolean used;

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