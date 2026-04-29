package com.example.idemilagrescript.utils;

import java.util.*;

public class SymbolTable {

    private final List<Map<String, Symbol>> scopes = new ArrayList<>();
    private final List<Symbol> allDeclared = new ArrayList<>();

    public SymbolTable() {
        enterScope();
    }

    public void enterScope() {
        scopes.add(new LinkedHashMap<>());
    }


    public List<Symbol> exitScope() {
        if (scopes.size() <= 1) return Collections.emptyList(); // nunca remove o global
        Map<String, Symbol> removed = scopes.remove(scopes.size() - 1);
        return new ArrayList<>(removed.values());
    }



    public boolean declare(Symbol symbol) {
        Map<String, Symbol> current = currentScope();
        if (current.containsKey(symbol.getName())) {
            return false;
        }
        symbol.setScopeDepth(scopes.size() - 1);
        current.put(symbol.getName(), symbol);
        allDeclared.add(symbol);
        return true;
    }

    public Symbol lookup(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Symbol s = scopes.get(i).get(name);
            if (s != null) return s;
        }
        return null;
    }

    public Symbol lookupCurrentScope(String name) {
        return currentScope().get(name);
    }

    public Map<String, Symbol> currentScope() {
        return scopes.get(scopes.size() - 1);
    }

    public List<Symbol> getCurrentScopeSymbols() {
        return new ArrayList<>(currentScope().values());
    }

    public List<Symbol> getAllSymbols() {
        return new ArrayList<>(allDeclared);
    }

    public int depth() {
        return scopes.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Tabela de Símbolos ===\n");
        for (int i = 0; i < scopes.size(); i++) {
            sb.append("  Escopo ").append(i).append(":\n");
            for (Symbol s : scopes.get(i).values()) {
                sb.append("    ").append(s).append("\n");
            }
        }
        return sb.toString();
    }
}