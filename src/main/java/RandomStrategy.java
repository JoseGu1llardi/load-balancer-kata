import java.util.List;
import java.util.Random;

public class RandomStrategy implements SelectionStrategy {
    private final Random random;

    public RandomStrategy() {
        this(new Random());
    }

    RandomStrategy(Random random) {
        this.random = random;
    }

    @Override
    public String select(List<String> instances) {
        int index = random.nextInt(instances.size());
        return instances.get(index);
    }
}
