package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.stdlib.data.yaml.lexer.CharacterReader;
import io.ballerina.stdlib.data.yaml.lexer.LexerState;
import io.ballerina.stdlib.data.yaml.lexer.Token;

import java.util.HashMap;
import java.util.Map;

public class ParserState {
    // Properties for the YAML lines
    String[] lines;
    int numLines;
    int lineIndex = -1;

    // Current token
    Token currentToken = new Token(Token.TokenType.DUMMY);

    // Previous YAML token
    Token.TokenType prevToken = Token.TokenType.DUMMY;

    // Used to store the token after peeked.
    // Used later when the checkToken method is invoked.
    Token tokenBuffer = new Token(Token.TokenType.DUMMY);

    // Lexical analyzer tool for getting the tokens
    private final LexerState lexerState;

    boolean expectBlockSequenceValue = false;

    boolean explicitKey = false;

    boolean explicitDoc = false;

    Map<String, String> customTagHandles = new HashMap<>();

    String[] reservedDirectives = new String[0];

    int lastKeyLine = -1;
    int lastExplicitKeyLine = -1;

    boolean isLastExplicitKey = false;

    // YAML version of the document.
    Float yamlVersion = null;

    public boolean directiveDocument = false;

    boolean tagPropertiesInLine = false;

    boolean emptyKey = false;

    boolean indentationProcessed = false;

    public ParserState(CharacterReader characterReader) {
        this.lexerState = new LexerState(characterReader);
    }

//    Event[] eventBuffer = new Event[0];

//    public ParsingError init(String[] lines) {
//        this.lines = lines;
//        this.numLines = lines.length;
//        ParsingError err = this.initLexer();
//        if (err != null && err instanceof ParsingError) {
//            this.eventBuffer = new Event[]{new Event(common.EventType.STREAM)};
//        }
//        return err;
//    }

//    void updateLexerContext(Context context) {
//        this.lexerState.context = context;
//    }

    public int getLineNumber() {
        return this.lexerState.lineNumber + 1;
    }

    public int getIndex() {
        return this.lexerState.index;
    }

//    // Initialize the lexer with the attributes of a new line.
//    //
//    // + message - Error message to display when if the initialization fails
//    // + return - An error if it fails to initialize
//    ParsingError initLexer(String message) {
//        this.lineIndex += 1;
//        String line;
//        if (this.lexerState.isNewLine) {
//            line = this.lexerState.line.substring(this.lexerState.index);
//        } else {
//            if (this.lineIndex >= this.numLines) {
//                return generateGrammarError(message);
//            }
//            line = this.lines[this.lineIndex];
//        }
//
//        this.explicitDoc = false;
//        this.expectBlockSequenceValue = false;
//        this.tagPropertiesInLine = false;
//        this.lexerState.setLine(line, this.lineIndex);
//        return null;
//    }
}
