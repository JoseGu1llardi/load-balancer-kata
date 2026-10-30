import java.util.*;

public class LoadBalancer {
    private static final int MAX_INSTANCE_CAPACITY = 10;

    private final List<String> instances = new ArrayList<>();

    private final Random random;

    public LoadBalancer() {
        this(new Random());       // normal use: a real random generator
    }

    LoadBalancer(Random random) {
        this.random = random;     // tests: we choose the generator
    }

    public void register(String address) {
        validateAddress(address);

        if (instances.size() >= MAX_INSTANCE_CAPACITY) {
            throw new IllegalStateException("Cannot register more than " + MAX_INSTANCE_CAPACITY + " instances.");
        }
        if (instances.contains(address)) {
            throw new IllegalStateException("Address already registered: " + address);
        }
        instances.add(address);
    }

    public String get() {
        if (instances.isEmpty()) {
            throw new IllegalStateException("No instances registered");
        }
        int index = random.nextInt(instances.size());
        return instances.get(index);
    }

    public List<String> getInstances() {
        return List.copyOf(instances);
    }

    private void validateAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be null or blank.");
        }
    }
}
