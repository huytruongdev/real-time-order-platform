package com.realtimeorder.user;

import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.user.application.AuthExceptions.EmailAlreadyUsedException;
import com.realtimeorder.user.application.AuthExceptions.InvalidRefreshTokenException;
import com.realtimeorder.user.application.AuthService;
import com.realtimeorder.user.application.AuthTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrency test với PostgreSQL thật.
 *
 * Mọi thread chờ ở cùng một {@link CountDownLatch} rồi chạy đồng thời,
 * để tăng khả năng các transaction thực sự chồng lấn nhau.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthConcurrencyTest {

    private static final String PASSWORD = "password123";

    @Autowired
    AuthService authService;

    @Test
    void concurrentRegistrationWithSameEmailCreatesExactlyOneUser() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";

        List<Outcome<Object>> outcomes = runConcurrently(5, () -> authService.register(email, PASSWORD, "Racer"));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .allSatisfy(o -> assertThat(o.error()).isInstanceOf(EmailAlreadyUsedException.class));
    }

    /**
     * Hai request refresh cùng một token đồng thời (ví dụ client retry, hoặc kẻ tấn công và user thật).
     *
     * Nhờ {@code SELECT ... FOR UPDATE}, request thứ hai chỉ đọc token sau khi request đầu commit,
     * lúc đó token đã bị rotate nên bị xem là reuse: cả family bị revoke,
     * kể cả token mới mà request đầu vừa nhận được.
     */
    @Test
    void concurrentRefreshWithSameTokenSucceedsOnceAndRevokesFamily() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        authService.register(email, PASSWORD, "Racer");
        String refreshToken = authService.login(email, PASSWORD).refreshToken();

        List<Outcome<AuthTokens>> outcomes = runConcurrently(2, () -> authService.refresh(refreshToken));

        List<Outcome<AuthTokens>> successes = outcomes.stream().filter(Outcome::succeeded).toList();
        assertThat(successes).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .singleElement()
                .satisfies(o -> assertThat(o.error()).isInstanceOf(InvalidRefreshTokenException.class));

        String winnerToken = successes.getFirst().value().refreshToken();
        assertThatThrownBy(() -> authService.refresh(winnerToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private static <T> List<Outcome<T>> runConcurrently(int threads, Callable<T> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(Outcome.success(future.get(30, TimeUnit.SECONDS)));
                } catch (ExecutionException e) {
                    outcomes.add(Outcome.failure(e.getCause()));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private record Outcome<T>(T value, Throwable error) {

        static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> failure(Throwable error) {
            return new Outcome<>(null, error);
        }

        boolean succeeded() {
            return error == null;
        }
    }
}
