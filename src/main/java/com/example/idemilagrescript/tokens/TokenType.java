package com.example.idemilagrescript.tokens;

public enum TokenType {
    VOID, CHAR, INT, DOUBLE, SHORT, LONG, STRING, // Identificadores

    IF, ELSE, WHILE, RETURN, // Funções + return

    // Identificadores e Literais
    IDENTIFICADOR, NUMERO, STRING_LITERAL,

    // Operadores Aritméticos + Atribuição
    MAIS, MENOS, VEZES, DIVISAO, ATRIBUICAO,

    // Operadores Relacionais e Lógicos
    IGUAL, DIFERENTE, MAIOR, MENOR, MAIOR_IGUAL, MENOR_IGUAL,
    NOT, AND, OR,

    // Delimitadores
    ABRE_PAREN, FECHA_PAREN, ABRE_CHAVE, FECHA_CHAVE, PONTO_VIRGULA,

    // Fim de Arquivo e Erro
    EOF, ERRO
}
