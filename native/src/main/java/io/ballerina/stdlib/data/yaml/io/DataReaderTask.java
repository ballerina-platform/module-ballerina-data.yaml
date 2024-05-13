package io.ballerina.stdlib.data.yaml.io;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.stdlib.data.yaml.parser.YamlParser;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticLog;

import java.io.InputStreamReader;
import java.util.function.Consumer;

public class DataReaderTask implements Runnable {

    private static final String METHOD_NAME_NEXT = "next";
    private static final String METHOD_NAME_CLOSE = "close";

    private final Environment env;
    private final BObject iteratorObj;
    private final Future future;
    private final BTypedesc typed;
    private final BMap<BString, Object> options;

    public DataReaderTask(Environment env, BObject iteratorObj, Future future, BTypedesc typed,
                          BMap<BString, Object> options) {
        this.env = env;
        this.iteratorObj = iteratorObj;
        this.future = future;
        this.typed = typed;
        this.options = options;
    }

    static MethodType resolveNextMethod(BObject iterator) {
        MethodType method = getMethodType(iterator, METHOD_NAME_NEXT);
        if (method != null) {
            return method;
        }
        throw new IllegalStateException("next method not found in the iterator object");
    }

    static MethodType resolveCloseMethod(BObject iterator) {
        return getMethodType(iterator, METHOD_NAME_CLOSE);
    }

    private static MethodType getMethodType(BObject iterator, String methodNameClose) {
        ObjectType objectType = (ObjectType) TypeUtils.getReferredType(iterator.getOriginalType());
        MethodType[] methods = objectType.getMethods();
        // Assumes compile-time validation of the iterator object
        for (MethodType method : methods) {
            if (method.getName().equals(methodNameClose)) {
                return method;
            }
        }
        return null;
    }

    @Override
    public void run() {
        ResultConsumer<Object> resultConsumer = new ResultConsumer<>(future);
        try (var byteBlockSteam = new BallerinaByteBlockInputStream(env, iteratorObj, resolveNextMethod(iteratorObj),
                resolveCloseMethod(iteratorObj), resultConsumer)) {
            Object result = YamlParser.compose(new InputStreamReader(byteBlockSteam),
                    options, typed.getDescribingType());
            future.complete(result);
        } catch (Exception e) {
            future.complete(DiagnosticLog.getYamlError("Error occurred while reading the stream: " + e.getMessage()));
        }
    }

    /**
     * This class will hold module related utility functions.
     *
     * @param <T>    The type of the result
     * @param future The future to complete
     * @since 0.1.0
     */
    public record ResultConsumer<T>(Future future) implements Consumer<T> {

        @Override
        public void accept(T t) {
            future.complete(t);
        }
    }
}
