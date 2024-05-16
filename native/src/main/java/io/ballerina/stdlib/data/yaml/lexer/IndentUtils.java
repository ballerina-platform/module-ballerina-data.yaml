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

package io.ballerina.stdlib.data.yaml.lexer;

import io.ballerina.stdlib.data.yaml.common.Types.Collection;
import io.ballerina.stdlib.data.yaml.utils.Error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IndentUtils {

    /** Check if the current index have sufficient indent.
     *
     * @param lexerState - Current lexer state
     */
    public static void assertIndent(LexerState lexerState) throws Error.YamlParserException {
        assertIndent(lexerState, 0, false);
    }

    /** Check if the current index have sufficient indent.
     *
     * @param lexerState - Current lexer state
     * @param offset - Additional white spaces after the parent indent
     */
    public static void assertIndent(LexerState lexerState, int offset) throws Error.YamlParserException {
        assertIndent(lexerState, offset, false);
    }

    /** Check if the current index have sufficient indent.
     *
     * @param lexerState - Current lexer state
     * @param offset - Additional white spaces after the parent indent
     * @param captureIndentationBreak The token is allowed as prefix to a mapping key name
     */
    public static void assertIndent(LexerState lexerState, int offset, boolean captureIndentationBreak)
            throws Error.YamlParserException {
        if (lexerState.getColumn() < lexerState.getIndent() + offset) {
            if (captureIndentationBreak) {
                lexerState.setIndentationBreak(true);
                return;
            }
            throw new Error.YamlParserException("invalid indentation", lexerState.getIndent(), lexerState.getColumn());
        }
    }

    public static boolean isTabInIndent(LexerState lexerState, int upperLimit) {
        int tabInWhitespace = lexerState.getTabInWhitespace();
        return lexerState.getIndent() > -1 && tabInWhitespace > -1 && tabInWhitespace <= upperLimit;
    }

    /**
     * Differentiate the planar and anchor keys against the key of a mapping.
     *
     * @param lexerState Current state of the lexer
     * @param outputToken - Planar or anchor key
     */
    public static void handleMappingValueIndent(LexerState lexerState, Token.TokenType outputToken)
            throws Error.YamlParserException {
        handleMappingValueIndent(lexerState, outputToken, null);
    }

    /**
     * Differentiate the planar and anchor keys against the key of a mapping.
     *
     * @param lexerState Current state of the lexer
     * @param outputToken - Planar or anchor key
     * @param scan - Scanner instance use for scanning
     */
    public static void handleMappingValueIndent(LexerState lexerState, Token.TokenType outputToken,
                                                Scanner.Scan scan) throws Error.YamlParserException {
        lexerState.setIndentationBreak(false);
        boolean enforceMapping = lexerState.getEnforceMapping();
        lexerState.setEnforceMapping(false);

        boolean notSufficientIndent = false;
        if (scan == null) {
            lexerState.forward();
            lexerState.tokenize(outputToken);
            notSufficientIndent = lexerState.getColumn() < lexerState.getIndentStartIndex();
        } else {
            try {
                assertIndent(lexerState, 1);
            } catch (Error.YamlParserException ex) {
                notSufficientIndent = true;
            }
            lexerState.updateStartIndex();
            Scanner.iterate(lexerState, scan, outputToken);
        }

        if (lexerState.isFlowCollection()) {
            return;
        }

        // Ignore whitespace until a character is found
        int numWhitespace = 0;
        while (Utils.WHITE_SPACE_PATTERN.pattern(lexerState.peek(numWhitespace))) {
            numWhitespace += 1;
        }

        if (notSufficientIndent) {
            if (lexerState.peek(numWhitespace) == ':' && !lexerState.isFlowCollection()) {
                lexerState.forward(numWhitespace);
                lexerState.setIndentation(handleIndent(lexerState, lexerState.getIndentStartIndex()));
                return;
            }

            throw new Error.YamlParserException("insufficient indentation for a scalar",
                    lexerState.getLine(), lexerState.getColumn());
        }

        if (lexerState.peek(numWhitespace) == ':' && !lexerState.isFlowCollection()) {
            lexerState.forward(numWhitespace);
            lexerState.setIndentation(handleIndent(lexerState, lexerState.getIndentStartIndex()));
            return;
        }

        if (enforceMapping) {
            throw new Error.YamlParserException("insufficient indentation for a scalar",
                    lexerState.getLine(), lexerState.getColumn());
        }
    }

    public static Indentation handleIndent(LexerState sm) throws Error.YamlParserException {
        return handleIndent(sm, null);
    }
    /** Validate the indentation of block collections.
     */
    public static Indentation handleIndent(LexerState sm, Integer mapIndex) throws Error.YamlParserException {
        int startIndex = mapIndex == null ? sm.getColumn() - 1 : mapIndex;

        if (mapIndex != null) {
            sm.setKeyDefinedForLine(true);
        }

        if (isTabInIndent(sm, startIndex)) {
            throw new Error.YamlParserException("cannot have tab as an indentation", sm.getLine(), sm.getColumn());
        }

        Collection collection = mapIndex == null ? Collection.SEQUENCE : Collection.MAPPING;

        if (sm.getIndent() == startIndex) {

            List<Collection> existingIndentType = sm.getIndents().stream()
                    .filter(indent -> indent.getColumn() == startIndex)
                    .map(Indent::getCollection)
                    .toList();

            // The current token is a mapping key and a sequence entry exists for the indent
            if (collection == Collection.MAPPING
                    && existingIndentType.contains(Collection.SEQUENCE)) {
                if (existingIndentType.contains(Collection.MAPPING)) {
                    return new Indentation(
                            Indentation.IndentationChange.INDENT_DECREASE,
                            new ArrayList<>(Collections.singleton(sm.getIndents().pop().getCollection())),
                            sm.getClonedTokensForMappingValue());
                } else {
                    throw new Error.YamlParserException("block mapping cannot have the " +
                            "same indent as a block sequence", sm.getLine(), sm.getColumn());
                }
            }

            // The current token is a sequence entry and a mapping key exists for the indent
            if (collection == Collection.SEQUENCE
                    && existingIndentType.contains(Collection.MAPPING)) {
                if (existingIndentType.contains(Collection.SEQUENCE)) {
                    return new Indentation(
                            Indentation.IndentationChange.INDENT_NO_CHANGE,
                            new ArrayList<>(),
                            sm.getClonedTokensForMappingValue());
                } else {
                    sm.getIndents().push(new Indent(startIndex, Collection.SEQUENCE));
                    return new Indentation(
                            Indentation.IndentationChange.INDENT_INCREASE,
                            new ArrayList<>(Collections.singleton(Collection.SEQUENCE)),
                            sm.getClonedTokensForMappingValue());
                }
            }
            return new Indentation(
                    Indentation.IndentationChange.INDENT_NO_CHANGE,
                    new ArrayList<>(),
                    sm.getClonedTokensForMappingValue());
        }

        if (sm.getIndent() < startIndex) {
            sm.getIndents().push(new Indent(startIndex, collection));
            sm.setIndent(startIndex);
            return new Indentation(
                    Indentation.IndentationChange.INDENT_INCREASE,
                    new ArrayList<>(Collections.singleton(collection)),
                    sm.getClonedTokensForMappingValue());
        }

        if (sm.getIndent() > startIndex) {

            List<Collection> existingIndentType = sm.getIndents().stream()
                    .filter(indent -> indent.getColumn() == startIndex)
                    .map(Indent::getCollection)
                    .toList();

            // The current token is a mapping key and a sequence entry exists for the indent
            if (collection == Collection.MAPPING
                    && existingIndentType.contains(Collection.SEQUENCE)) {
                if (!existingIndentType.contains(Collection.MAPPING)) {
                    throw new Error.YamlParserException("block mapping cannot have the " +
                            "same indent as a block sequence", sm.getLine(), sm.getColumn());
                }
            }
        }

        Indent removedIndent = null;
        List<Collection> returnCollection = new ArrayList<>();

        int indentsSize = sm.getIndents().size();
        while (sm.getIndent() > startIndex && indentsSize > 0) {
            removedIndent = sm.getIndents().pop();
            sm.setIndent(removedIndent.getColumn());
            returnCollection.add(removedIndent.getCollection());
            --indentsSize;
        }


        if (indentsSize > 0 && removedIndent != null) {
            Indent removedSecondIndent = sm.getIndents().pop();
            if (removedSecondIndent.getColumn() == startIndex && collection == Collection.MAPPING) {
                returnCollection.add(removedIndent.getCollection());
            } else {
                sm.getIndents().add(removedSecondIndent);
            }
        }

        int indent = sm.getIndent();
        if (indent == startIndex) {
            sm.getIndents().push(new Indent(indent, collection));
            int returnCollectionSize = returnCollection.size();
            if (returnCollectionSize > 1) {
                returnCollection.remove(returnCollectionSize - 1);
                return new Indentation(
                        Indentation.IndentationChange.INDENT_DECREASE,
                        returnCollection,
                        sm.getClonedTokensForMappingValue());
            }
        }

        throw new Error.YamlParserException("invalid indentation", sm.getLine(), sm.getColumn());
    }
}
