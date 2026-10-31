import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoundRobinStrategyTest {
    private RoundRobinStrategy robinStrategy;
    private List<String> instances;

    @BeforeEach
    void setup() {
        robinStrategy = new RoundRobinStrategy();
        instances = List.of(
                "http://10.0.0.1:8080",
                "http://10.0.0.2:8080",
                "http://10.0.0.3:8080"
        );
    }

    private void completeOneRound() {
        for (int i = 0; i < instances.size(); i++) {
            robinStrategy.select(instances);
        }
    }

    @Test
    void returnsInstancesInOrder() {
        assertEquals(instances, List.of(
                robinStrategy.select(instances),
                robinStrategy.select(instances),
                robinStrategy.select(instances)
        ));
    }

    @Test
    void startsAgainFromTheFirstAfterTheLast() {
        completeOneRound();

        assertEquals(instances.getFirst(), robinStrategy.select(instances));
    }

    @Test
    void withOneInstanceAlwaysReturnsIt() {
        List<String> singleInstance = List.of("http://10.0.0.1:8080");

        for (int i = 1; i <= 3; i++) {
            assertEquals(singleInstance.getFirst(), robinStrategy.select(singleInstance));
        }
    }

    @RepeatedTest(20)
    void concurrentSelectsAreEvenlyDistributed() throws InterruptedException {
        int threadCount = 30;
        int callsPerThread = 100;
        Map<String, Integer> counts = new ConcurrentHashMap<>(); // instance -> how many times it was returned
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        start.await(); // wait for the starting signal
                        for (int call = 0; call < callsPerThread; call++) {
                            String instance = robinStrategy.select(instances);
                            counts.merge(instance, 1, Integer::sum);
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
        }

        int expected = threadCount * callsPerThread / instances.size();

        for (String instance : instances) {
            assertEquals(expected, counts.get(instance));
        }
    }
}
