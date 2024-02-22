package io.ballerina.stdlib.data.yaml.parser;

import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.stdlib.data.yaml.lexer.YamlLexer;

import java.io.Reader;

public class YamlParser {

    /**
     * Parses the contents in the given {@link Reader} and returns subtype of anydata value.
     *
     * @param reader reader which contains the YAML content
     * @param expectedType Shape of the YAML content required
     * @return subtype of anydata value
     * @throws BError for any parsing error
     */
    public static Object parse(Reader reader, Type expectedType) throws BError {
        YamlLexer lexer = new YamlLexer(reader);

        return null;
    }
}
