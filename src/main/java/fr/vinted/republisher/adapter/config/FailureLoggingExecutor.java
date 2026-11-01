package fr.vinted.republisher.adapter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Logs a background task that fails: a thread pool only prints it on the standard error.
 * The failure stops there, the use case having already recorded its outcome.
 */
class FailureLoggingExecutor implements Executor {

    private static final Logger LOG = LoggerFactory.getLogger(FailureLoggingExecutor.class);

    private final Executor executor;

    FailureLoggingExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException failure) {
                LOG.error("Background task failed", failure);
            }
        });
    }
}
