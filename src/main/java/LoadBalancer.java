import java.util.*;

public class LoadBalancer {
    private static final int MAX_INSTANCE_CAPACITY = 10;

    private final List<String> instances = new ArrayList<>();

    private final SelectionStrategy strategy;

    public LoadBalancer() {
        this(new RandomStrategy());
    }

    public LoadBalancer(SelectionStrategy selectionStrategy) {
        this.strategy = Objects.requireNonNull(selectionStrategy, "Strategy cannot be null.");
    }

    public synchronized void register(String address) {
        validateAddress(address);

        if (instances.size() >= MAX_INSTANCE_CAPACITY) {
            throw new IllegalStateException("Cannot register more than " + MAX_INSTANCE_CAPACITY + " instances.");
        }
        if (instances.contains(address)) {
            throw new IllegalStateException("Address already registered: " + address);
        }
        instances.add(address);
    }

    public synchronized String get() {
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances registered.");
        }
        return strategy.select(Collections.unmodifiableList(instances));
    }

    public synchronized List<String> getInstances() {
        return List.copyOf(instances);
    }

    private void validateAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be null or blank.");
        }
    }
}
