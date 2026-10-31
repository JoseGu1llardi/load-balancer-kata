import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
