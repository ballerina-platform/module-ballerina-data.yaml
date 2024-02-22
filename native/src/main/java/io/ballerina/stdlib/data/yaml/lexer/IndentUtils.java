package io.ballerina.stdlib.data.yaml.lexer;

import java.util.ArrayList;
import java.util.List;

public class IndentUtils {

    public static void assertIndent(YamlLexer.StateMachine stateMachine) {
        assertIndent(stateMachine, 0, false);
    }

    public static void assertIndent(YamlLexer.StateMachine stateMachine, int offset) {
        assertIndent(stateMachine, offset, false);
    }

    public static void assertIndent(YamlLexer.StateMachine stateMachine, int offset, boolean captureIndentationBreak) {
        if (stateMachine.getColumn() < stateMachine.getIndent() + offset) {
            if (captureIndentationBreak) {
                stateMachine.setIndentationBreak(true);
                return;
            }
            throw new RuntimeException("Invalid indentation");
        }
    }

    public static boolean isTabInIndent(YamlLexer.StateMachine stateMachine, int upperLimit) {
        int tabInWhitespace = stateMachine.getTabInWhitespace();
        return stateMachine.getIndent() > -1 && tabInWhitespace > -1 && tabInWhitespace <= upperLimit;
    }

    public static void handleMappingValueIndent(YamlLexer.StateMachine stateMachine, Token.TokenType outputToken) {
        handleMappingValueIndent(stateMachine, outputToken, null);
    }

    public static void handleMappingValueIndent(YamlLexer.StateMachine stateMachine, Token.TokenType outputToken,
                                                Scanner.Scan scan) {
        stateMachine.setIndentationBreak(false);
        boolean enforceMapping = stateMachine.getEnforceMapping();
        stateMachine.setEnforceMapping(false);

        boolean notSufficientIndent;
        if (scan == null) {
            stateMachine.tokenize(outputToken);
            notSufficientIndent = stateMachine.getColumn() < stateMachine.getIndentStartIndex();
        } else {
            assertIndent(stateMachine, 1);
            stateMachine.updateStartIndex();
            Scanner.iterate(stateMachine, scan, outputToken);
            notSufficientIndent = false;
        }

        if (stateMachine.isFlowCollection()) {
            return;
        }

        // Ignore whitespace until a character is found
        int numWhitespace = 0;
        while (Utils.WHITE_SPACE_PATTERN.pattern(stateMachine.peek())) {
            numWhitespace += 1;
        }

        if (notSufficientIndent) {
            if (stateMachine.peek(numWhitespace) == ':' && !stateMachine.isFlowCollection()) {
                stateMachine.forward(numWhitespace);
                stateMachine.setIndentation(handleIndent(stateMachine, stateMachine.getIndentStartIndex()));
                return;
            }

            throw new RuntimeException("Insufficient indentation for a scalar");
        }

        if (stateMachine.peek(numWhitespace) == ':' && !stateMachine.isFlowCollection()) {
            stateMachine.forward(numWhitespace);
            stateMachine.setIndentation(handleIndent(stateMachine, stateMachine.getIndentStartIndex()));
            return;
        }

        if (enforceMapping) {
            throw new RuntimeException("Insufficient indentation for a scalar");
        }
    }

    public static Indentation handleIndent(YamlLexer.StateMachine sm, int mapIndex) {
        int startIndex = mapIndex == -1 ? sm.getColumn() - 1 : mapIndex;

        if (mapIndex != -1) {
            sm.setKeyDefinedForLine(true);
        }

        if (isTabInIndent(sm, startIndex)) {
            throw new RuntimeException("Cannot have tab as an indentation");
        }

        Indentation.Collection collection;
        if (mapIndex == -1) {
            collection = Indentation.Collection.SEQUENCE;
        } else {
            collection = Indentation.Collection.MAPPING;
        }

        if (sm.getIndent() == startIndex) {

            List<Indentation.Collection> existingIndentType = sm.getIndents().stream()
                    .filter(indent -> indent.getColumn() == startIndex)
                    .map(Indent::getCollection).toList();

            // The current token is a mapping key and a sequence entry exists for the indent
            if (collection == Indentation.Collection.MAPPING
                    && existingIndentType.contains(Indentation.Collection.SEQUENCE)) {
                if (existingIndentType.contains(Indentation.Collection.MAPPING)) {
                    int indentsLength = sm.getIndents().size();
                    return new Indentation(
                            Indentation.IndentationChange.INDENT_DECREASE,
                            List.of(sm.getIndents().remove(indentsLength -1).getCollection()),
                            sm.getTokensForMappingValue());
                } else {
                    throw new RuntimeException("Block mapping cannot have the same indent as a block sequence");
                }
            }

            // The current token is a sequence entry and a mapping key exists for the indent
            if (collection == Indentation.Collection.SEQUENCE
                    && existingIndentType.contains(Indentation.Collection.MAPPING)) {
                if (existingIndentType.contains(Indentation.Collection.SEQUENCE)) {
                    return new Indentation(
                            Indentation.IndentationChange.INDENT_NO_CHANGE,
                            List.of(),
                            sm.getTokensForMappingValue());
                } else {
                    sm.getIndents().add(new Indent(startIndex, Indentation.Collection.SEQUENCE));
                    return new Indentation(
                            Indentation.IndentationChange.INDENT_INCREASE,
                            List.of(Indentation.Collection.SEQUENCE),
                            sm.getTokensForMappingValue());
                }
            }
            return new Indentation(
                    Indentation.IndentationChange.INDENT_NO_CHANGE,
                    List.of(),
                    sm.getTokensForMappingValue());
        }

        if (sm.getIndent() < startIndex) {
            sm.getIndents().add(new Indent(startIndex, collection));
            sm.setIndent(startIndex);
            return new Indentation(
                    Indentation.IndentationChange.INDENT_INCREASE,
                    List.of(collection),
                    sm.getTokensForMappingValue());
        }

        Indent removedIndent = null;
        List<Indentation.Collection> returnCollection = new ArrayList<>();

        int indentsSize = sm.getIndents().size();
        while (sm.getIndent() > startIndex && indentsSize > 0) {
            removedIndent = sm.getIndents().remove(--indentsSize);
            sm.setIndent(removedIndent.getColumn());
            returnCollection.add(removedIndent.getCollection());
        }


        if (indentsSize > 0 && removedIndent != null) {
            Indent removedSecondIndent = sm.getIndents().remove(--indentsSize);
            if (removedSecondIndent.getColumn() == startIndex && collection == Indentation.Collection.MAPPING) {
                returnCollection.add(removedIndent.getCollection());
            } else {
                sm.getIndents().add(removedSecondIndent);
            }
        }

        int indent = sm.getIndent();
        if (indent == startIndex) {
            sm.getIndents().add(new Indent(indent, collection));
            int returnCollectionSize = returnCollection.size();
            if (returnCollectionSize > 1) {
                returnCollection.remove(returnCollectionSize-1);
                return new Indentation(
                        Indentation.IndentationChange.INDENT_DECREASE,
                        returnCollection,
                        sm.getTokensForMappingValue());
            }
        }

        throw new RuntimeException("Invalid indentation");
    }
}
