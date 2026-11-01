package fr.vinted.republisher.adapter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class FailureLoggingExecutorTest {

    private final Executor onTestThread = Runnable::run;

    @Test
    void logs_a_failed_background_task(CapturedOutput logs) {
        Executor background = new FailureLoggingExecutor(onTestThread);

        background.execute(() -> {
            throw new IllegalStateException("unexpected browser failure");
        });

        assertThat(logs).contains("Background task failed").contains("unexpected browser failure");
    }

    @Test
    void runs_the_task_on_the_given_executor() {
        StringBuilder trace = new StringBuilder();

        new FailureLoggingExecutor(onTestThread).execute(() -> trace.append("ran"));

        assertThat(trace).hasToString("ran");
    }
}
