package io.ballerina.stdlib.data.yaml.parser;


import io.ballerina.stdlib.data.yaml.lexer.LexerState;
import io.ballerina.stdlib.data.yaml.lexer.Token;

import java.util.List;

public class Directive {

    /**
     * Check the grammar productions for YAML directives.
     * Update the yamlVersion of the document.
     *
     * @param state - Current parser state
     */
    public static void yamlDirective(ParserState state) {
        if (state.getYamlVersion() != null) {
            throw new RuntimeException("YAML document version is already defined");
        }

        // Expects a separate in line.
        YamlParser.getNextToken(state, List.of(Token.TokenType.SEPARATION_IN_LINE));
        state.updateLexerState(LexerState.LEXER_DIRECTIVE);

        // Expect yaml version
        YamlParser.getNextToken(state, List.of(Token.TokenType.DECIMAL));
        String lexemeBuffer = state.getCurrentToken().getValue();
        YamlParser.getNextToken(state, List.of(Token.TokenType.DOT));
        lexemeBuffer += ".";
        YamlParser.getNextToken(state, List.of(Token.TokenType.DECIMAL));
        lexemeBuffer += state.getCurrentToken().getValue();

        float yamlVersion = Float.parseFloat(lexemeBuffer);
        if (yamlVersion != 1.2) {
            if (yamlVersion >= 2.0 || yamlVersion < 1.0) {
                throw new RuntimeException("Incompatible yaml version for the 1.2 parser");
            }
        }
        state.setYamlVersion(yamlVersion);
    }

    /**
     * Check the grammar production for TAG directives.
     * Update the tag handle map.
     *
     * @param state - Current parser state
     */
    public static void tagDirective(ParserState state) {
        YamlParser.getNextToken(state, List.of(Token.TokenType.SEPARATION_IN_LINE));

        // Expect a tag handle
        state.updateLexerState(LexerState.LEXER_TAG_HANDLE_STATE);
        YamlParser.getNextToken(state, List.of(Token.TokenType.TAG_HANDLE));
        String tagHandle = state.getCurrentToken().getValue();
        YamlParser.getNextToken(state, List.of(Token.TokenType.SEPARATION_IN_LINE));

        // Tag handles cannot be redefined
        if (state.getCustomTagHandles().containsKey(tagHandle)) {
            throw new RuntimeException("Duplicate tag handle");
        }

        state.updateLexerState(LexerState.LEXER_TAG_PREFIX_STATE);
        YamlParser.getNextToken(state, List.of(Token.TokenType.TAG_PREFIX));
        String tagPrefix = state.getCurrentToken().getValue();

        state.getCustomTagHandles().put(tagHandle, tagPrefix);
    }

    /**
     * Check the grammar productions for YAML reserved directives.
     * Update the reserved directives of the document.
     *
     * @param state - Current parser state
     */
    public static void reservedDirective(ParserState state) {
        StringBuilder reservedDirective = new StringBuilder(state.getCurrentToken().getValue());
        state.updateLexerState(LexerState.LEXER_RESERVED_DIRECTIVE);

        // Check for the reserved directive parameters
        YamlParser.getNextToken(state, true);
        while (state.getBufferedToken().getType() == Token.TokenType.SEPARATION_IN_LINE) {
            YamlParser.getNextToken(state);
            YamlParser.getNextToken(state, true);
            if (state.getBufferedToken().getType() != Token.TokenType.PRINTABLE_CHAR) {
                break;
            }
            YamlParser.getNextToken(state);
            reservedDirective.append(" ").append(state.getCurrentToken().getValue());
            YamlParser.getNextToken(state, true);
        }
        state.getReservedDirectives().add(reservedDirective.toString());
    }
}
