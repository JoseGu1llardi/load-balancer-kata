import java.util.List;

public class RoundRobinStrategy implements SelectionStrategy {
    private int currentIndex;

    @Override
    public String select(List<String> instances) {
        String instance = instances.get(currentIndex);

        currentIndex = (currentIndex + 1) % instances.size();

        return instance;
    }
}
