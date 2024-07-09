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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve tagged values and create BValues.
 *
 * @since 0.1.0
 */
public class TagResolutionUtils {

    private static final String DOT = ".";
    private static final String PLUS = "+";
    private static final String POSITIVE_FLOAT = "+.";
    private static final String NEGATIVE_FLOAT = "-.";
    private static final String SIMPLE_NULL = "null";
    private static final String SIMPLE_FALSE = "false";
    private static final String SIMPLE_TRUE = "true";
    private static final String OCTAL_START = "0o";
    private static final String HEXA_START = "0x";
    private static final Pattern SIMPLE_INT_PATTERN = Pattern.compile("-?(\\d+)");
    private static final Pattern COMPLEX_INT_PATTERN = Pattern.compile("[+-]?(\\d+)");
    private static final Pattern SIMPLE_FLOAT_PATTERN = Pattern.compile("-?(0|\\d+)(\\.\\d*)?([eE][-+]?\\d+)?");
    private static final Pattern COMPLEX_FLOAT_PATTERN = Pattern
            .compile("[-+]?(\\.\\d+|\\d+(\\.\\d*)?)([eE][-+]?\\d+)?");
    private static final Set<String> CORE_SCHEMA_INF = Set.of("inf", "Inf", "INF");
    private static final Set<String> CORE_SCHEMA_NAN = Set.of("nan", "NaN", "NAN");
    private static final Set<String> CORE_SCHEMA_NULL = Set.of("null", "Null", "NULL", "~");
    private static final Set<String> CORE_SCHEMA_TRUE = Set.of("true", "True", "TRUE");
    private static final Set<String> CORE_SCHEMA_FALSE = Set.of("false", "False", "FALSE");

    private TagResolutionUtils() {
    }

    public static Object constructSimpleNull(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (value.equals(SIMPLE_NULL)) {
            return null;
        }
        throw new Error.YamlParserException("cannot cast " + value + " to null", state.getLine(), state.getColumn());
    }

    public static Object constructNull(String value, YamlParser.ComposerState state)
        throws Error.YamlParserException {
        if (isCoreSchemaNull(value)) {
            return null;
        }
        throw new Error.YamlParserException("cannot cast " + value + " to null", state.getLine(), state.getColumn());
    }

    public static Object constructSimpleBool(String value, YamlParser.ComposerState state)
        throws Error.YamlParserException {
        if (value.equals(SIMPLE_TRUE)) {
            return true;
        } else if (value.equals(SIMPLE_FALSE)) {
            return false;
        }
        throw new Error.YamlParserException("cannot cast " + value + " to boolean", state.getLine(), state.getColumn());
    }

    public static Object constructBool(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (isCoreSchemaTrue(value)) {
            return true;
        } else if (isCoreSchemaFalse(value)) {
            return false;
        }
        throw new Error.YamlParserException("cannot cast " + value + " to boolean", state.getLine(), state.getColumn());
    }

    public static Object constructSimpleInt(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        Matcher matcher = SIMPLE_INT_PATTERN.matcher(value);
        if (matcher.find()) {
            return Long.valueOf(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + " to int", state.getLine(), state.getColumn());
    }

    public static Object constructInt(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (value.length() > 1) {
            if (value.startsWith(OCTAL_START)) {
                return Long.parseLong(value.substring(2), 8);
            } else if (value.startsWith(HEXA_START)) {
                return Long.parseLong(value.substring(2), 16);
            }
        }
        Matcher matcher = COMPLEX_INT_PATTERN.matcher(value);
        if (matcher.find()) {
            return Long.valueOf(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + " to int", state.getLine(), state.getColumn());
    }

    public static Object constructSimpleFloat(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        Matcher matcher = SIMPLE_FLOAT_PATTERN.matcher(value);
        if (matcher.find()) {
            return Double.parseDouble(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + " to float", state.getLine(), state.getColumn());
    }

    public static Object constructFloat(String value, YamlParser.ComposerState state)
            throws Error.YamlParserException {
        if (value.length() > 1) {
            if (value.startsWith(DOT)) {
                String valueSuffix = value.substring(1);
                if (isCoreSchemaNaN(valueSuffix)) {
                    return Double.NaN;
                }
                if (isCoreSchemaInf(valueSuffix)) {
                    return Double.POSITIVE_INFINITY;
                }
            } else if (value.startsWith(POSITIVE_FLOAT) || value.startsWith(NEGATIVE_FLOAT)) {
                String valueSuffix = value.substring(2);
                boolean isInfinity = isCoreSchemaInf(valueSuffix);
                if (isInfinity) {
                    return value.startsWith(PLUS) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
            }
        }
        Matcher matcher = COMPLEX_FLOAT_PATTERN.matcher(value);
        if (matcher.find()) {
            return Double.parseDouble(value);
        }
        throw new Error.YamlParserException("cannot cast " + value + " to float", state.getLine(), state.getColumn());
    }

    private static boolean isCoreSchemaInf(String valueSuffix) {
        return CORE_SCHEMA_INF.contains(valueSuffix);
    }

    private static boolean isCoreSchemaNaN(String valueSuffix) {
        return CORE_SCHEMA_NAN.contains(valueSuffix);
    }

    public static boolean isCoreSchemaNull(String value) {
        return CORE_SCHEMA_NULL.contains(value);
    }

    public static boolean isCoreSchemaBoolean(String value) {
        return isCoreSchemaTrue(value) || isCoreSchemaFalse(value);
    }

    private static boolean isCoreSchemaTrue(String value) {
        return CORE_SCHEMA_TRUE.contains(value);
    }

    private static boolean isCoreSchemaFalse(String value) {
        return CORE_SCHEMA_FALSE.contains(value);
    }
}
