package io.ballerina.stdlib.data.yaml.io;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DataReaderThreadPool {

    // TODO : Make this configurable, in Ballerina Library.
    private static final int CORE_POOL_SIZE = 0;
    private static final int MAX_POOL_SIZE = 50;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final String THREAD_NAME = "bal-data-yaml-thread";
    public static final ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(CORE_POOL_SIZE,
            MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new SynchronousQueue<>(), new DataThreadFactory());

    /**
     * Thread factory for data reader.
     *
     * @since 0.1.0
     */
    static class DataThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread ballerinaData = new Thread(runnable);
            ballerinaData.setName(THREAD_NAME);
            return ballerinaData;
        }
    }
}
