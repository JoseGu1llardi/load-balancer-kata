import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LoadBalancerTest {
    // from the requirements, not from LoadBalancer
    private static final int REQUIRED_LIMIT = 10;

    private LoadBalancer balancer;

    @BeforeEach
    void setup() {
        balancer = new LoadBalancer();
    }

    private void registerUpToTheLimit() {
        for (int i = 1; i <= REQUIRED_LIMIT; i++) {
            balancer.register("http://10.0.0." + i + ":8080");
        }
    }

    @Test
    void newLoadBalancerHasNoInstances() {
        assertTrue(balancer.getInstances().isEmpty());
    }

    @Test
    void registeredInstanceAppearsInInstances() {
        String address = "http://10.0.0.1:8080";

        balancer.register(address);

        assertEquals(List.of(address), balancer.getInstances());
    }

    @Test
    void registeringSameAddressTwiceThrows() {
        String address = "http://10.0.0.1:8080";

        balancer.register(address);

        assertThrows(IllegalStateException.class, () -> balancer.register(address));
    }

    @Test
    void registeringEleventhInstanceThrows() {
        registerUpToTheLimit();

        String address = "http://10.0.0.55:8080";

        assertThrows(IllegalStateException.class, () -> balancer.register(address));
    }

    @Test
    void failedRegistrationDoesNotChangeInstances() {
        registerUpToTheLimit();

        String address = "http://10.0.0.55:8080";

        assertThrows(IllegalStateException.class, () -> balancer.register(address));
        assertEquals(REQUIRED_LIMIT, balancer.getInstances().size());
        assertFalse(balancer.getInstances().contains(address));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void registeringInvalidAddressThrows(String address) {
        assertThrows(IllegalArgumentException.class, () -> balancer.register(address));
    }

    @Test
    void returnedInstancesCannotBeModified() {
        List<String> instances = balancer.getInstances();

        assertThrows(UnsupportedOperationException.class, () -> instances.add("http://10.0.0.55:8080"));
    }

    @Test
    void gettingWithNoInstancesThrows() {
        assertThrows(IllegalStateException.class, balancer::get);
    }

    @Test
    void gettingWithOneInstanceReturnsIt() {
        String address = "http://10.0.0.55:8080";

        balancer.register(address);

        assertEquals(address, balancer.get());
    }

    @Test
    void returnedInstanceIsAlwaysARegisteredOne() {
        List<String> addresses = List.of(
                "http://10.0.0.1:8080",
                "http://10.0.0.2:8080",
                "http://10.0.0.3:8080"
        );
        addresses.forEach(balancer::register);

        for (int i = 1; i <= 100; i++) {
            assertTrue(addresses.contains(balancer.get()));
        }
    }

    @Test
    void getReturnsTheInstanceAtTheRandomIndex() {
        Random random = mock(Random.class);
        when(random.nextInt(3)).thenReturn(1); // the random index will be 1

        LoadBalancer loadBalancer = new LoadBalancer(new RandomStrategy(random));
        loadBalancer.register("http://10.0.0.1:8080");
        loadBalancer.register("http://10.0.0.2:8080");
        loadBalancer.register("http://10.0.0.3:8080");

        assertEquals("http://10.0.0.2:8080", loadBalancer.get());
    }

    @Test
    void newInstanceJoinsTheRotationAtTheEnd() {
        LoadBalancer loadBalancer = new LoadBalancer(new RoundRobinStrategy());

        loadBalancer.register("http://10.0.0.1:8080");
        loadBalancer.register("http://10.0.0.2:8080");
        loadBalancer.register("http://10.0.0.3:8080");

        loadBalancer.get();
        loadBalancer.get();

        loadBalancer.register("http://10.0.0.4:8080");

        List<String> expectedReturnedInstances = List.of(
                "http://10.0.0.3:8080",
                "http://10.0.0.4:8080",
                "http://10.0.0.1:8080",
                "http://10.0.0.2:8080"
        );

        assertEquals(expectedReturnedInstances, List.of(
                loadBalancer.get(),
                loadBalancer.get(),
                loadBalancer.get(),
                loadBalancer.get()
        ));
    }

    @Test
    void creatingWithNullStrategyThrows() {
        assertThrows(NullPointerException.class, () -> new LoadBalancer(null));
    }

    @Test
    void strategyCannotModifyTheInstances() {
        LoadBalancer loadBalancer = new LoadBalancer(instances -> {
            instances.clear();
            return "http://10.0.0.1:8080";
        });
        loadBalancer.register("http://10.0.0.1:8080");

        assertThrows(UnsupportedOperationException.class, loadBalancer::get);
    }
}
