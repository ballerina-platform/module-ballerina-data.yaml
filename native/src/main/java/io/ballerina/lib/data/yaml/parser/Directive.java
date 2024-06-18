/*
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.yaml.parser;


import io.ballerina.lib.data.yaml.lexer.LexerState;
import io.ballerina.lib.data.yaml.lexer.Token;
import io.ballerina.lib.data.yaml.utils.Constants;
import io.ballerina.lib.data.yaml.utils.Error;

import java.util.List;

/**
 * This class will hold utility functions to process directives.
 *
 * @since 0.1.0
 */
public class Directive {

    private Directive() {
    }

    /**
     * Check the grammar productions for YAML directives.
     * Update the yamlVersion of the document.
     *
     * @param state - Current parser state
     */
    public static void yamlDirective(ParserState state) throws Error.YamlParserException {
        if (state.getYamlVersion() != null) {
            throw new Error.YamlParserException("YAML document version is already defined",
                    state.getLine(), state.getColumn());
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
                throw new Error.YamlParserException("incompatible yaml version for the 1.2 parser",
                        state.getLine(), state.getColumn());
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
    public static void tagDirective(ParserState state) throws Error.YamlParserException {
        YamlParser.getNextToken(state, List.of(Token.TokenType.SEPARATION_IN_LINE));

        // Expect a tag handle
        state.updateLexerState(LexerState.LEXER_TAG_HANDLE_STATE);
        YamlParser.getNextToken(state, List.of(Token.TokenType.TAG_HANDLE));
        String tagHandle = state.getCurrentToken().getValue();
        YamlParser.getNextToken(state, List.of(Token.TokenType.SEPARATION_IN_LINE));

        // Tag handles cannot be redefined
        if (state.getCustomTagHandles().containsKey(tagHandle)) {
            throw new Error.YamlParserException("duplicate tag handle", state.getLine(), state.getColumn());
        }

        state.updateLexerState(LexerState.LEXER_TAG_PREFIX_STATE);
        YamlParser.getNextToken(state, List.of(Token.TokenType.TAG_PREFIX));
        String tagPrefix = state.getCurrentToken().getValue();

        if (!tagPrefix.equals(Constants.DEFAULT_GLOBAL_TAG_HANDLE)) {
            throw new Error.YamlParserException("custom tags not supported", state.getLine(), state.getColumn());
        }

        state.getCustomTagHandles().put(tagHandle, tagPrefix);
    }

    /**
     * Check the grammar productions for YAML reserved directives.
     * Update the reserved directives of the document.
     *
     * @param state - Current parser state
     */
    public static void reservedDirective(ParserState state) throws Error.YamlParserException {
        StringBuilder reservedDirective = new StringBuilder(state.getCurrentToken().getValue());
        state.updateLexerState(LexerState.LEXER_RESERVED_DIRECTIVE);

        // Check for the reserved directive parameters
        YamlParser.getNextToken(state, true);
        while (state.getBufferedToken().getType() == Token.TokenType.SEPARATION_IN_LINE) {
            YamlParser.getNextToken(state);
            YamlParser.getNextToken(state, true);
            YamlParser.getNextToken(state);
            reservedDirective.append(" ").append(state.getCurrentToken().getValue());
            YamlParser.getNextToken(state, true);
        }
        state.getReservedDirectives().add(reservedDirective.toString());
    }
}
