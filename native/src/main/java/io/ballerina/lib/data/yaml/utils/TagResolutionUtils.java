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

package io.ballerina.lib.data.yaml.utils;

import io.ballerina.lib.data.yaml.parser.YamlParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve tagged values and create BValues.
 *
 * @since 0.1.0
 */
public class TagResolutionUtils {

    public static Object constructSimpleNull(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (value.equals("null")) {
            return null;
        }
        throw new Error.YamlParserException("cannot cast " + value + "to null", state.getLine(), state.getColumn());
    }

    public static Object constructNull(String value, YamlParser.ComposerState state)
        throws Error.YamlParserException {
        if (value.equals("null") || value.equals("Null") || value.equals("NULL") || value.equals("~")) {
            return null;
        }
        throw new Error.YamlParserException("cannot cast " + value + "to null", state.getLine(), state.getColumn());
    }

    public static Object constructSimpleBool(String value, YamlParser.ComposerState state)
        throws Error.YamlParserException {
        if (value.equals("true")) {
            return true;
        } else if (value.equals("false")) {
            return false;
        }
        throw new Error.YamlParserException("cannot cast " + value + "to boolean", state.getLine(), state.getColumn());
    }

    public static Object constructBool(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {

        if (value.equals("true") || value.equals("True") || value.equals("TRUE")) {
            return true;
        } else if (value.equals("false") || value.equals("False") || value.equals("FALSE")) {
            return false;
        }
        throw new Error.YamlParserException("cannot cast " + value + "to boolean", state.getLine(), state.getColumn());
    }

    public static Object constructSimpleInt(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        Pattern simpleIntPattern = Pattern.compile("-?(\\d+)");
        Matcher matcher = simpleIntPattern.matcher(value);
        if (matcher.find()) {
            return Long.valueOf(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + "to int", state.getLine(), state.getColumn());
    }

    public static Object constructInt(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (value.length() > 1) {
            if (value.startsWith("0o")) {
                return Long.parseLong(value.substring(2), 8);
            } else if (value.startsWith("0x")) {
                return Long.parseLong(value.substring(2), 16);
            }
        }
        Pattern simpleIntPattern = Pattern.compile("[+-]?(\\d+)");
        Matcher matcher = simpleIntPattern.matcher(value);
        if (matcher.find()) {
            return Long.valueOf(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + "to int", state.getLine(), state.getColumn());
    }

    public static Object constructSimpleFloat(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        Pattern simpleFloatPattern = Pattern.compile("-?(0|\\d+)(\\.\\d*)?([eE][-+]?\\d+)?");
        Matcher matcher = simpleFloatPattern.matcher(value);
        if (matcher.find()) {
            return Double.parseDouble(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + "to float", state.getLine(), state.getColumn());
    }

    public static Object constructFloat(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (value.length() > 1) {
            if (value.startsWith(".")) {
                String valueSuffix = value.substring(1);
                if (valueSuffix.equals("nan") || valueSuffix.equals("NaN") || valueSuffix.equals("NAN")) {
                    return Double.NaN;
                }
                if (valueSuffix.equals("inf") || valueSuffix.equals("Inf") || valueSuffix.equals("INF")) {
                    return Double.POSITIVE_INFINITY;
                }
            } else if (value.startsWith("+.") || value.startsWith("-.")) {
                String valueSuffix = value.substring(1);
                boolean isInfinity = valueSuffix.equals("inf") ||
                        valueSuffix.equals("Inf") || valueSuffix.equals("INF");
                if (isInfinity) {
                    return value.startsWith("+") ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
            }
        }
        Pattern floatPattern = Pattern.compile("[-+]?(\\.\\d+|\\d+(\\.\\d*)?)([eE][-+]?\\d+)?");
        Matcher matcher = floatPattern.matcher(value);
        if (matcher.find()) {
            return Double.parseDouble(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + "to float", state.getLine(), state.getColumn());
    }
}
