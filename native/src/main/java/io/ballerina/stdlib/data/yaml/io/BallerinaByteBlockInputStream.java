package io.ballerina.stdlib.data.yaml.io;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.async.StrandMetadata;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.stdlib.data.yaml.utils.DiagnosticLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class BallerinaByteBlockInputStream extends InputStream {

    private final BObject iterator;
    private final Environment env;
    private final String nextMethodName;
    private final Type returnType;
    private final String strandName;
    private final StrandMetadata metadata;
    private final Map<String, Object> properties;
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final MethodType closeMethod;
    private final Consumer<Object> futureResultConsumer;

    private byte[] currentChunk = new byte[0];
    private int nextChunkIndex = 0;

    public BallerinaByteBlockInputStream(Environment env, BObject iterator, MethodType nextMethod,
                                         MethodType closeMethod, Consumer<Object> futureResultConsumer) {
        this.env = env;
        this.iterator = iterator;
        this.nextMethodName = nextMethod.getName();
        this.returnType = nextMethod.getReturnType();
        this.closeMethod = closeMethod;
        this.strandName = env.getStrandName().orElse("");
        this.metadata = env.getStrandMetadata();
        this.properties = Map.of();
        this.futureResultConsumer = futureResultConsumer;
    }

    @Override
    public int read() {
        if (done.get()) {
            return -1;
        }
        if (hasBytesInCurrentChunk()) {
            return currentChunk[nextChunkIndex++];
        }
        // Need to get a new block from the stream, before reading again.
        nextChunkIndex = 0;
        try {
            if (readNextChunk()) {
                return read();
            }
        } catch (InterruptedException e) {
            BError error = DiagnosticLog.getYamlError("Cannot read the stream, interrupted error");
            futureResultConsumer.accept(error);
            return -1;
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        super.close();
        Semaphore semaphore = new Semaphore(0);
        if (closeMethod != null) {
            env.getRuntime().invokeMethodAsyncSequentially(iterator, closeMethod.getName(), strandName, metadata,
                    new Callback() {
                        @Override
                        public void notifyFailure(BError bError) {
                            semaphore.release();
                        }

                        @Override
                        public void notifySuccess(Object result) {
                            semaphore.release();
                        }
                    }, properties, returnType);
        }
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new IOException("Error while closing the stream", e);
        }
    }

    private boolean hasBytesInCurrentChunk() {
        return currentChunk.length != 0 && nextChunkIndex < currentChunk.length;
    }

    private boolean readNextChunk() throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        Callback callback = new Callback() {

            @Override
            public void notifyFailure(BError bError) {
                // Panic with an error
                done.set(true);
                futureResultConsumer.accept(bError);
                currentChunk = new byte[0];
                semaphore.release();
                // TODO : Should we panic here?
            }

            @Override
            public void notifySuccess(Object result) {
                if (result == null) {
                    done.set(true);
                    currentChunk = new byte[0];
                    semaphore.release();
                    return;
                }
                if (result instanceof BMap<?, ?>) {
                    BMap<BString, Object> valueRecord = (BMap<BString, Object>) result;
                    final BString value = Arrays.stream(valueRecord.getKeys()).findFirst().get();
                    final BArray arrayValue = valueRecord.getArrayValue(value);
                    currentChunk = arrayValue.getByteArray();
                    semaphore.release();
                } else {
                    // Case where Completes with an error
                    done.set(true);
                    semaphore.release();
                }
            }

        };
        env.getRuntime().invokeMethodAsyncSequentially(iterator, nextMethodName, strandName, metadata, callback,
                properties, returnType);
        semaphore.acquire();
        return !done.get();
    }
}
