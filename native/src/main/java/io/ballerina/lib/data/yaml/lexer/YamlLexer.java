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

package io.ballerina.lib.data.yaml.lexer;

import io.ballerina.lib.data.yaml.utils.Error;

import static io.ballerina.lib.data.yaml.lexer.LexerState.LEXER_DOUBLE_QUOTE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.EMPTY_LINE;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.TAG;
import static io.ballerina.lib.data.yaml.lexer.Token.TokenType.EOL;
import static io.ballerina.lib.data.yaml.lexer.Utils.isTagChar;

/**
 * Core logic of the YAML Lexer.
 *
 * @since 0.1.0
 */
public class YamlLexer {

    private YamlLexer() {
    }

    /**
     * Generate a Token for the next immediate lexeme.
     *
     * @param state Current lexer state
     * @return Updated lexer state is returned
     */
    public static LexerState.State scanTokens(LexerState state) throws Error.YamlParserException {
        // Check the lexeme buffer for the lexeme stored by the primary tag
        if (state.getLexemeBuffer().length() > 0) {
            state.setLexeme(state.getLexemeBuffer());
            state.setLexemeBuffer("");

            // If lexeme buffer contains only up to a hexadecimal value,
            // then check for the remaining content of the primary tag.
            if (isTagChar(state)) {
                Scanner.iterate(state, Scanner.TAG_CHARACTER_SCANNER, TAG);
                return state.getState();
            }

            state.tokenize(TAG);
            return state.getState();
        }

        // Generate EOL token at the last index
        if (state.isEndOfStream()) {
            if (state.isIndentationBreak()) {
                throw new Error.YamlParserException("invalid indentation", state.getLine(), state.getColumn());
            }
            if (state.getColumn() == 0) {
                state.forward();
                state.tokenize(EMPTY_LINE);
            } else {
                state.forward();
                state.tokenize(EOL);
            }
            return state.getState();
        }

        // Check for line breaks when reading from string
        if (state.peek() == '\n' && state.getState() != LEXER_DOUBLE_QUOTE) {
            state.setNewLine(true);
            state.forward();
            state.tokenize(EOL);
            return state.getState();
        }

        // Check for line breaks when reading from string
        if (state.peek() == '\r' && state.peek(1) == '\n' && state.getState() != LEXER_DOUBLE_QUOTE) {
            state.setNewLine(true);
            state.forward();
            state.tokenize(EOL);
            return state.getState();
        }

        return state.getState().transition(state);
    }
}
