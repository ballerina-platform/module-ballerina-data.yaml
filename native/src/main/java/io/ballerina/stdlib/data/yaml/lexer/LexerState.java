package io.ballerina.stdlib.data.yaml.lexer;

import java.util.ArrayList;
import java.util.List;

public class LexerState {
    // Properties to represent current position
    public int index = 0;
    public int lineNumber = 0;

    // Line to be lexically analyzed
    public String line = "";

    // Value of the generated token
    String lexeme = "";

    // Current state of the Lexer
    public LexerContext context = LexerContext.LEXER_START;

    // Minimum indentation imposed by the parent nodes
    List<Indent> indents = new ArrayList<>();

    // Minimum indentation required to the current line
    int indent = -1;

    // Additional indent set by the indentation indicator
    int addIndent = 1;

    // Represent the number of opened flow collections
    int numOpenedFlowCollections = 0;

    // Store the lexeme if it will be scanned again by the next token
    String lexemeBuffer = "";

    // Flag is enabled after a JSON key is detected.
    // Used to generate mapping value even when it is possible to generate a planar scalar.
    public boolean isJsonKey = false;

    // The lexer is currently processing trailing comments when the flag is set.
    public boolean trailingComment = false;

    // Start index for the mapping value
    int indentStartIndex = -1;

    Token.TokenType[] tokensForMappingValue = new Token.TokenType[0];

    public int lastEscapedChar = -1;

    public boolean allowTokensAsPlanar = false;

    // When flag is set, updates the current indent to the indent of the first line
    boolean captureIndent = false;

    boolean enforceMapping = false;

    int tabInWhitespace = -1;

    boolean indentationBreak = false;

    boolean keyDefinedForLine = false;

    public boolean firstLine = true;

    public boolean isNewLine = false;

    int mappingKeyColumn = -1;

    // Output YAML token
    Token.TokenType token = Token.TokenType.DUMMY;

    Indentation indentation = null;

    private CharacterReader characterReader;

    public LexerState(CharacterReader characterReader) {
        this.characterReader = characterReader;
    }

    // Peeks the character succeeding after k indexes.
    // Returns the character after k spots.
    //
    // + k - Number of characters to peek. Default = 0
    // + return - Character at the peek if not null
    public String peek(int k) {
        int codePoint = characterReader.peek(k);
        if (codePoint == -1) {
            return null;
        }
        return Character.toString(codePoint);
    }

    // Increment the index of the column by k indexes
    //
    // + k - Number of indexes to forward. Default = 1
    public void forward(int k) {
        characterReader.forward(k);
    }

    public void updateStartIndex(Token.TokenType token) { // TODO:
        if (token != null) {
            Token.TokenType[] temp = new Token.TokenType[this.tokensForMappingValue.length + 1];
            System.arraycopy(this.tokensForMappingValue, 0, temp, 0, this.tokensForMappingValue.length);
            temp[temp.length - 1] = token;
            this.tokensForMappingValue = temp;
        }
        if (this.index < this.indentStartIndex || this.indentStartIndex < 0) {
            this.indentStartIndex = this.index;
        }
    }

    public void updateFirstTabIndex() { // TODO:
        if (this.index < this.tabInWhitespace || this.tabInWhitespace < 0) {
            this.tabInWhitespace = this.index;
        }
    }

    // Add the output YAML token to the current state
    //
    // + token - YAML token
    // + return - Generated lexical token
    public LexerState tokenize(Token.TokenType token) {
        this.forward(1);
        this.token = token;
        return this;
    }

    // Obtain the lexer token
    //
    // + return - Lexer token
    public Token getToken() {
        Token.TokenType tokenBuffer = this.token;
        this.token = Token.TokenType.DUMMY;
        String lexemeBuffer = this.lexeme;
        this.lexeme = "";
        Indentation indentationBuffer = this.indentation;
        this.indentation = null;
        return new Token(tokenBuffer, lexemeBuffer, indentationBuffer);
    }

    public void setLine(String line, int lineNumber) {
        this.index = 0;
        this.line = line;
        this.lineNumber = lineNumber;
        this.lastEscapedChar = -1;
        this.indentStartIndex = -1;
        this.tokensForMappingValue = new Token.TokenType[0];
        this.tabInWhitespace = -1;
        this.isNewLine = false;
        this.keyDefinedForLine = false;
    }

    // Reset the current lexer state
    public void resetState() {
        this.addIndent = 1;
        this.captureIndent = false;
        this.enforceMapping = false;
        this.indentStartIndex = -1;
        this.indent = -1;
        this.indents = new ArrayList<>();
        this.lexeme = "";
        this.context = LexerContext.LEXER_START;
    }

    public boolean isFlowCollection() {
        return this.numOpenedFlowCollections > 0;
    }

    public boolean isEndOfStream() {
        return this.index >= this.line.length();
    }

    public static enum LexerContext {
        LEXER_START,
        LEXER_TAG_HANDLE,
        LEXER_TAG_PREFIX,
        LEXER_NODE_PROPERTY,
        LEXER_DIRECTIVE,
        LEXER_DOUBLE_QUOTE,
        LEXER_SINGLE_QUOTE,
        LEXER_BLOCK_HEADER,
        LEXER_LITERAL,
        LEXER_RESERVED_DIRECTIVE
    }
}
