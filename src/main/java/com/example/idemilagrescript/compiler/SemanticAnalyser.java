package com.example.idemilagrescript.compiler;

import com.example.idemilagrescript.utils.Token;
import com.example.idemilagrescript.utils.TokenType;
import com.example.idemilagrescript.utils.SymbolTable;
import com.example.idemilagrescript.utils.Symbol;

import java.util.*;


public class SemanticAnalyser {

    private static final Map<TokenType, Integer> TYPE_RANK = new EnumMap<>(TokenType.class);
    static {
        TYPE_RANK.put(TokenType.CHAR,   0);
        TYPE_RANK.put(TokenType.SHORT,  1);
        TYPE_RANK.put(TokenType.INT,    2);
        TYPE_RANK.put(TokenType.LONG,   3);
        TYPE_RANK.put(TokenType.DOUBLE, 4);
    }

    private static final Set<TokenType> TYPE_TOKENS = EnumSet.of(
            TokenType.VOID, TokenType.CHAR, TokenType.INT,
            TokenType.DOUBLE, TokenType.SHORT, TokenType.LONG, TokenType.STRING
    );

    private static final Set<TokenType> COMMAND_TOKENS = EnumSet.of(
            TokenType.VOID, TokenType.CHAR, TokenType.INT,
            TokenType.DOUBLE, TokenType.SHORT, TokenType.LONG, TokenType.STRING,
            TokenType.IDENTIFICADOR, TokenType.IF, TokenType.WHILE, TokenType.RETURN
    );

    private final List<Token>    tokens;
    private final List<LexError> errors      = new ArrayList<>();
    private final SymbolTable    symbolTable = new SymbolTable();
    private int current = 0;

    private static class SemanticException extends RuntimeException {
        SemanticException() { super(null, null, true, false); }
    }

    public SemanticAnalyser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<LexError> analyze() { // Estrutura começa aqui analisando o escopo do código
        errors.clear(); // limpa os erros antigos
        current = 0; // Ponteiro que volta ao inicio

        parsePrograma(); // Analisa o escopo do programa

        reportUnused(symbolTable.getCurrentScopeSymbols()); // Vai verificar aquelas variaveis que estão largadas no programa, não estao utilizadas

        return errors;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    private void parsePrograma() { // Vai processar todos os comandos de nivel global, percorrendo token por token
        while (!isAtEnd()) {
            if (isCommandStart(peek().getType()))
                parseComando();
            else
                advance();
        }
    }

    private void parseComando() { // Aqui ele vai verificar os tokens daquelas variaveis/funções, verificando sua logica, se está correta, e com isso avança para proxima linha
        try {
            TokenType t = peek().getType();
            if (isTypeStart(t))
                parseDeclaracao();
            else if (t == TokenType.IDENTIFICADOR)
                parseAtribuicao();
            else if (t == TokenType.IF)
                parseCondicional();
            else if (t == TokenType.WHILE)
                parseRepeticao();
            else if (t == TokenType.RETURN)
                parseReturn();
            else
                advance();
        } catch (SemanticException e) {
            synchronize();
        }
    }

    private void parseBloco() { // Verifica se a lógica do abrir e fechar chaves, ou seja, bloco, foi aplicada de forma correta, contendo o bloco inicial "{" e fechando com o final "}"
        symbolTable.enterScope();
        consumeSilent(TokenType.ABRE_CHAVE);

        while (!isAtEnd() && !check(TokenType.FECHA_CHAVE) && isCommandStart(peek().getType())) {
            parseComando();
        }

        List<Symbol> saindo = symbolTable.exitScope(); // Com isso aqui ele permite variaveis que foi usando somente naquele bloco, para ser usado em outro
        reportUnused(saindo);

        consumeSilent(TokenType.FECHA_CHAVE);
    }

    private void parseDeclaracao() { // Aqui vai registra os símbolos na tabela, onde checa a redeclaração, e verifica o tipo da inicializaçao
        Token typeToken = advance(); // Le o tipo de token daquela linha
        TokenType declaredType = typeToken.getType(); // Le o nome do que ta vindo juntamente com aquele token

        Token nameToken = consumeSilent(TokenType.IDENTIFICADOR);
        if (nameToken == null) {
            synchronize();
            return;
        }

        Symbol existing = symbolTable.lookupCurrentScope(nameToken.getLexeme()); // Vai verificar se não duplicação da mesma variavel no escopo do codigo
        if (existing != null) {
            addError(nameToken,
                    "Variável '" + nameToken.getLexeme() + "' já declarada neste escopo " +
                            "(linha " + existing.getLine() + ", coluna " + existing.getColumn() + ").");
        }

        Symbol symbol = new Symbol(  // A partir daquele token, ele vai criar o simbolo
                nameToken.getLexeme(), declaredType,
                nameToken.getLine(), nameToken.getColumn(), nameToken.getOffset()
        );

        if (match(TokenType.ATRIBUICAO)) { // Se caso ouver atribuição, o initialized é setado como true
            TokenType exprType = parseExpressao();
            symbol.setInitialized(true);
            if (exprType != null) { // Verifica se é compativel com aquele token a qual aquele tipo foi declarado
                checkAssignmentCompatibility(typeToken, declaredType, exprType);
            }
        }

        symbolTable.declare(symbol); // É declarado na tabela

        consumeSilent(TokenType.PONTO_VIRGULA);
    }

    private void parseAtribuicao() {
        Token nameToken = consumeSilent(TokenType.IDENTIFICADOR);
        if (nameToken == null) { synchronize(); return; }

        Symbol symbol = symbolTable.lookup(nameToken.getLexeme());
        if (symbol == null) {
            addError(nameToken, "Variável '" + nameToken.getLexeme() + "' não declarada.");
        }

        consumeSilent(TokenType.ATRIBUICAO);
        TokenType exprType = parseExpressao();

        if (symbol != null) {
            symbol.setInitialized(true);
            if (exprType != null) {
                checkAssignmentCompatibility(nameToken, symbol.getType(), exprType);
            }
        }

        consumeSilent(TokenType.PONTO_VIRGULA);
    }

    private void parseReturn() {
        consumeSilent(TokenType.RETURN);
        if (isExpressionStart(peek().getType())) {
            parseExpressao();
        }
        consumeSilent(TokenType.PONTO_VIRGULA);
    }

    private void parseCondicional() {
        consumeSilent(TokenType.IF);
        consumeSilent(TokenType.ABRE_PAREN);
        parseExpressao();
        consumeSilent(TokenType.FECHA_PAREN);
        parseBlocoOuComando();
        if (match(TokenType.ELSE)) {
            parseBlocoOuComando();
        }
    }

    private void parseRepeticao() {
        consumeSilent(TokenType.WHILE);
        consumeSilent(TokenType.ABRE_PAREN);
        parseExpressao();
        consumeSilent(TokenType.FECHA_PAREN);
        parseBlocoOuComando();
    }

    private void parseBlocoOuComando() {
        if (check(TokenType.ABRE_CHAVE)) parseBloco();
        else                            parseComando();
    }

    // Hierarquia de precedência para análise das expressões
    // parseExpressao()
    // parseExpLogicaOr()
    // parseExpLogicaAnd()
    // parseExpLogicaNot()
    // parseExpRelacional()
    // parseExpAritmetica()
    // parseTermo()
    // parseFator()

    private TokenType parseExpressao() { // Ele vai retornar qual tipo foi inferido da expressão
        return parseExpLogicaOr();
    }

    private TokenType parseExpLogicaOr() {
        TokenType type = parseExpLogicaAnd();
        while (match(TokenType.OR)) {
            TokenType right = parseExpLogicaAnd();
            checkLogicOperands(previous(), type, right);
            type = TokenType.INT;
        }
        return type;
    }

    private TokenType parseExpLogicaAnd() {
        TokenType type = parseExpLogicaNot();
        while (match(TokenType.AND)) {
            TokenType right = parseExpLogicaNot();
            checkLogicOperands(previous(), type, right);
            type = TokenType.INT;
        }
        return type;
    }

    private TokenType parseExpLogicaNot() {
        while (match(TokenType.NOT)) { }
        return parseExpRelacional();
    }

    private TokenType parseExpRelacional() { // A lógica de validação usando as expressões relacionais
        TokenType left = parseExpAritmetica();

        if (match(TokenType.IGUAL, TokenType.DIFERENTE,
                TokenType.MAIOR, TokenType.MENOR,
                TokenType.MAIOR_IGUAL, TokenType.MENOR_IGUAL)) { // Consome o token de expressão que esta naquela linha

            Token op = previous();
            TokenType right = parseExpAritmetica();

            if (left != null && right != null) { // Variaveis auxiliares para verificar aquilo que esta sendo comparado entre as expressoes esta correta
                boolean leftStr  = (left  == TokenType.STRING);
                boolean rightStr = (right == TokenType.STRING);
                if (leftStr != rightStr) {
                    addError(op, "Comparação inválida entre '" + typeName(left) +
                            "' e '" + typeName(right) + "'.");
                }
            }
            return TokenType.INT;
        }

        return left;
    }

    private TokenType parseExpAritmetica() {
        TokenType type = parseTermo();

        while (check(TokenType.MAIS) || check(TokenType.MENOS)) {
            Token op = advance();
            TokenType right = parseTermo();
            type = checkArithmeticTypes(op, type, right);
        }
        return type;
    }

    private TokenType parseTermo() {
        TokenType type = parseFator();

        while (check(TokenType.VEZES) || check(TokenType.DIVISAO)) {
            Token op = advance();
            TokenType right = parseFator();
            type = checkArithmeticTypes(op, type, right);
        }
        return type;
    }

    private TokenType parseFator() { // Toda vez que ele entra nesse metodo, vai verificar se o atributo de Symbol initialized esta como true, se não, vai indicar o erro de lógica naquela linha
        // Resolve os identificadores
        if (check(TokenType.IDENTIFICADOR)) { // Verifica se a inicialiação da variavel
            Token idToken = advance();
            Symbol sym = symbolTable.lookup(idToken.getLexeme());

            if (sym == null) {
                addError(idToken, "Variável '" + idToken.getLexeme() + "' não declarada.");
                return null;
            }

            if (!sym.isInitialized()) {
                addError(idToken,
                        "Variável '" + idToken.getLexeme() + "' pode não ter sido inicializada antes do uso.");
            }

            sym.setUsed(true);
            return sym.getType();
        }

        // Infere os tipos de literais
        if (check(TokenType.NUMERO)) { // Verifica se o retorno é um int ou double
            Token num = advance();
            return num.getLexeme() != null && num.getLexeme().contains(".")
                    ? TokenType.DOUBLE
                    : TokenType.INT;
        }

        if (check(TokenType.STRING_LITERAL)) { // Se o retorno é realmente uma string
            advance();
            return TokenType.STRING;
        }

        if (check(TokenType.ABRE_PAREN)) {

            if (isCastExpression()) {
                return parseCast(); // Processa os casts
            }

            advance(); // Ou verifica se após aquele parentese inserido, tem um fecha logo após a condicional digitada no processo
            TokenType inner = parseExpressao();
            consumeSilent(TokenType.FECHA_PAREN);
            return inner;
        }

        return null;
    }

    private boolean isCastExpression() { // metodo para validar se possui um cast naquela linha "(int) 2.4"
        if (current + 2 < tokens.size()) {
            TokenType next     = tokens.get(current + 1).getType();
            TokenType nextNext = tokens.get(current + 2).getType();
            return isTypeStart(next) && nextNext == TokenType.FECHA_PAREN; // Retorna como true, pois realmente faz sentido a lógica aplica para o cast
        }
        return false;
    }

    private TokenType parseCast() { // É a validação do cast, se realmente ta fazendo sentido utilizar
        consumeSilent(TokenType.ABRE_PAREN);
        Token castTypeToken = advance();
        TokenType targetType = castTypeToken.getType();
        consumeSilent(TokenType.FECHA_PAREN);

        TokenType sourceType = parseFator();

        if (sourceType != null) { // Se caso for inválido, ele vai tratar verificando como foi que aconteceu esse erro ao utilizar o cast
            if (!isValidCast(targetType, sourceType)) {
                addError(castTypeToken,
                        "Cast inválido: não é possível converter '" + typeName(sourceType) +
                                "' para '" + typeName(targetType) + "'.");
            }
            else if (isNumeric(targetType) && isNumeric(sourceType)) {
                int targetRank = TYPE_RANK.getOrDefault(targetType, -1);
                int sourceRank = TYPE_RANK.getOrDefault(sourceType, -1);
                if (targetRank < sourceRank) { // Aqui é em relação se faz sentido realizar aquele cast, como exemplo disso seria string para int
                    addWarning(castTypeToken,
                            "Cast de '" + typeName(sourceType) + "' para '" + typeName(targetType) +
                                    "' pode causar perda de dados.");
                }
            }
        }

        return targetType;
    }

    private void checkAssignmentCompatibility(Token context, TokenType target, TokenType source) { // Verificação das contabilidade dos tipos
        if (target == source) return;

        if (target == TokenType.VOID) { // Se caso ja vier um void para declarar, ja retorna o erro
            addError(context, "Não é possível atribuir valor a variável do tipo 'void'.");
            return;
        }

        // Um exemplo seria um "int x = 10.5", no qual ele notifica ao usuario avisando que pode houver a perca de dados ao fazer essa atribuição
        if (isNumeric(target) && isNumeric(source)) {
            int tRank = TYPE_RANK.getOrDefault(target, -1);
            int sRank = TYPE_RANK.getOrDefault(source, -1);
            if (sRank > tRank) {
                addError(context,
                        "Conversão implícita de '" + typeName(source) + "' para '" + typeName(target) +
                                "' pode perder dados. Use cast explícito: (" + typeName(target) + ").");
            }
            return;
        }
        // Um exemplo seria uma "string x = 10", ele vai validar verificando se naquela atriabuição tem a recorrencia do uso de aspas na atribuição
        if (target == TokenType.STRING || source == TokenType.STRING) {
            addError(context,
                    "Incompatibilidade de tipos: não é possível atribuir '" +
                            typeName(source) + "' a '" + typeName(target) + "'.");
        }
    }

    private TokenType checkArithmeticTypes(Token op, TokenType left, TokenType right) { // Validação das operações aritiméticas, ou seja, 1 + 1
        if (left == null || right == null)
            return null;

        if (left == TokenType.STRING && right == TokenType.STRING // Validação se é concatenação de duas strings, onde so pode ser concatenadas se houver um simbolo de mais entre elas
                && op.getType() == TokenType.MAIS) {
            return TokenType.STRING;
        }

        if (left == TokenType.STRING || right == TokenType.STRING) {
            addError(op,
                    "Operação aritmética inválida com tipo 'string'. " +
                            "Use '+' apenas para concatenar duas strings.");
            return null;
        }

        return promoteNumeric(left, right);
    }

    private void checkLogicOperands(Token op, TokenType left, TokenType right) { // Checagem se está feito correta o uso das condicionais, os operadores lógicos da linguagem
        if (left == TokenType.STRING || right == TokenType.STRING) { // Pega as duas posições, direita ou esquerda do operador
            addError(op, "Operador lógico '" + op.getLexeme() +
                    "' não pode ser aplicado ao tipo 'string'.");
        }
    }

    private void reportUnused(List<Symbol> symbols) { // Verifica durante o escopo inteiro, andando nele e validando se seu uso ta recorrente no codigo, ou se esta ali jogada
        for (Symbol s : symbols) {
            if (!s.isUsed()) {
                addWarning(s.getLine(), s.getColumn(), s.getOffset(), s.getName().length(),
                        "Variável '" + s.getName() + "' declarada mas nunca utilizada.");
            }
        }
    }

    private boolean isNumeric(TokenType t) {
        return TYPE_RANK.containsKey(t);
    }

    private boolean isTypeStart(TokenType t) {
        return TYPE_TOKENS.contains(t);
    }

    private boolean isCommandStart(TokenType t) {
        return COMMAND_TOKENS.contains(t);
    }

    private boolean isExpressionStart(TokenType t) {
        return t == TokenType.IDENTIFICADOR || t == TokenType.NUMERO
                || t == TokenType.STRING_LITERAL || t == TokenType.ABRE_PAREN
                || t == TokenType.NOT;
    }

    private boolean isValidCast(TokenType target, TokenType source) {
        if (target == source)
            return true;
        if (isNumeric(target) && isNumeric(source))
            return true;
        return false;
    }

    private TokenType promoteNumeric(TokenType a, TokenType b) {
        int ra = TYPE_RANK.getOrDefault(a, 2);
        int rb = TYPE_RANK.getOrDefault(b, 2);
        return (ra >= rb) ? a : b;
    }

    private String typeName(TokenType t) {
        if (t == null) return "desconhecido";
        return t.name().toLowerCase();
    }

    private void addError(Token token, String message) {
        int length = Math.max(1, token.getLength());
        errors.add(new LexError(token.getLine(), token.getColumn(),
                token.getOffset(), length, message));
    }

    private void addWarning(Token token, String message) {
        int length = Math.max(1, token.getLength());
        errors.add(new LexError(token.getLine(), token.getColumn(),
                token.getOffset(), length, "[AVISO] " + message));
    }

    private void addWarning(int line, int col, int offset, int length, String message) {
        errors.add(new LexError(line, col, offset, Math.max(1, length),
                "[AVISO] " + message));
    }

    private Token consumeSilent(TokenType expected) {
        if (check(expected)) return advance();
        return null;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) { advance(); return true; }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return type == TokenType.EOF;
        return peek().getType() == type;
    }

    private Token advance() { // Ele avança para a proxima linha, diante aquela List de Map que guardou os tokens
        if (!isAtEnd()) current++;
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

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().getType() == TokenType.PONTO_VIRGULA)
                return;
            TokenType t = peek().getType();
            if (t == TokenType.FECHA_CHAVE || isCommandStart(t))
                return;
            advance();
        }
    }
}