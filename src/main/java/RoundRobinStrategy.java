import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinStrategy implements SelectionStrategy {
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public String select(List<String> instances) {
        int index = Math.floorMod(counter.getAndIncrement(), instances.size());
        return instances.get(index);
    }
}
