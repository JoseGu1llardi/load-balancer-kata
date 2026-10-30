# Load Balancer Kata

A simple load balancer in Java, built step by step with TDD during a live-coding interview. It keeps a registry of backend instances and returns one of them at random on each call.

## Requirements

- Java 21+
- Maven 3.x

```bash
mvn test
```

## Usage

```java
LoadBalancer balancer = new LoadBalancer();

balancer.register("http://10.0.0.1:8080");
balancer.register("http://10.0.0.2:8080");
balancer.register("http://10.0.0.3:8080");

String instance = balancer.get();             // e.g. "http://10.0.0.2:8080"
List<String> all = balancer.getInstances();   // immutable copy
```

## API

| Method | Description | Throws |
|---|---|---|
| `LoadBalancer()` | Creates a load balancer backed by a real `Random`. | — |
| `LoadBalancer(Random random)` | *Package-private.* Lets tests inject the random generator. | — |
| `void register(String address)` | Registers a backend instance. | `IllegalArgumentException` if the address is `null` or blank; `IllegalStateException` if it is already registered or the limit of 10 is reached |
| `String get()` | Returns one of the registered instances, chosen at random. | `IllegalStateException` if no instances are registered |
| `List<String> getInstances()` | Returns an immutable copy of the registered instances. | — |

**Exception rule:** `IllegalArgumentException` when the argument itself is the problem; `IllegalStateException` when the argument is valid but the object's state doesn't allow the operation.

---

## Step 1: Registering instances

> Implement a `LoadBalancer` class that allows registering backend instances.
> - Each instance is identified by its address, a string such as `"http://10.0.0.1:8080"`.
> - The load balancer can hold at most 10 instances.
> - The same address cannot be registered twice.

### Tests

| # | Test | Checklist |
|---|---|---|
| 1 | `newLoadBalancerHasNoInstances` | Initial state |
| 2 | `registeredInstanceAppearsInInstances` | Happy path |
| 3 | `registeringSameAddressTwiceThrows` | Duplicates |
| 4 | `registeringEleventhInstanceThrows` | Limits |
| 5 | `failedRegistrationDoesNotChangeInstances` | Bonus: a failure doesn't change the state |
| 6 | `registeringInvalidAddressThrows` (parameterized) | Invalid input |
| 7 | `returnedInstancesCannotBeModified` | Encapsulation |

### Design decisions

- **Validate before changing state:** the argument is checked first, then the limit and duplicates, and only then is the address added. A failed registration leaves the instances untouched.
- **One source of truth:** the count is always `instances.size()`, never a separate counter.
- **No leaking of internals:** `getInstances()` returns `List.copyOf(...)`, so callers can't bypass the limit or duplicate rules.
- **Messages built from the constant:** `"Cannot register more than " + MAX_INSTANCE_CAPACITY + " instances."`
- **The tests use the limit from the requirements** (`REQUIRED_LIMIT = 10`), not the implementation's constant, so a wrong constant can't make a wrong test pass.

### Mistakes and fixes

| Mistake | Why it's a problem | Fix |
|---|---|---|
| Test list included removal tests | The interviewer said "for now, just registering". Testing features that weren't requested wastes interview time | Test only the current step's requirements |
| Test names referred to a "counter" | The agreed API has no counter; behaviour is observed through `getInstances()` | Verify behaviour through the agreed API |
| Two tests for the same protection (`...Throws` and `...DoesNotAffect...`) | They test two different designs (immutable copy vs mutable copy) | Choose one design (`List.copyOf`) and write one test |
| Separate tests for `null` and blank | Duplicated test code | One `@ParameterizedTest` with `@NullSource` and `@ValueSource(strings = {"", "   "})` |
| Created a `size()` method | It wasn't part of the agreed API | Use `getInstances()`, as agreed with the interviewer |
| `register` returned the address | No one asked for it, and it adds no information | Make `register` `void` |
| Test compared `register`'s return value with `getInstances()` | Circular: if both were wrong, the test would still pass | Compare with the original address: `assertEquals(List.of(address), getInstances())` |
| Missing space in the message (`10 + "instances"`) | Produced `"10instances"` | Re-read every concatenated string |
| Hard-coded `10` in an assertion after creating `REQUIRED_LIMIT` | The constant existed exactly for this | Use `REQUIRED_LIMIT` everywhere in the tests |
| Constant declared after instance fields | Breaks the usual Java convention | Put `static final` constants at the top of the class |
| Registered 10 instances to test the immutable list | The test isn't about the limit, so the Arrange confused the intent | Register one instance, or use `add()`, which works even on an empty list |
| Almost used `removeFirst()` on an empty list | Throws `NoSuchElementException`, not `UnsupportedOperationException` | Make sure the Arrange removes any other reason for failure |

---

## Step 2: Random selection

> Add a `get()` method that returns one of the registered instances, chosen at random.

### Clarifications from the interviewer

1. **Is an unordered `HashSet` random enough?** No. A `HashSet` doesn't guarantee any order, but that isn't the same as random: for the same addresses it usually iterates in the same order, so `get()` would keep returning the same instance. Every call should have a fair chance of returning any registered instance.
2. **What if there are no instances?** `get()` should throw. Returning `null` would just push the problem to the caller. The exception type was left to me.
3. **How do you test something random?** You can't predict which instance comes back, but you can guarantee it is always one of the registered instances. How to make it testable was left to me.

### Tests

| # | Test | Where it came from |
|---|---|---|
| 1 | `gettingWithNoInstancesThrows` | Clarification 2 |
| 2 | `gettingWithOneInstanceReturnsIt` | The deterministic case |
| 3 | `returnedInstanceIsAlwaysARegisteredOne` | Clarification 3: 100 calls with the real `Random` |
| 4 | `getReturnsTheInstanceAtTheRandomIndex` | Controlled randomness with Mockito |

### Design decisions

- **`ArrayList` instead of `HashSet`:** `get()` runs on every request, so it gets O(1) index access: `instances.get(random.nextInt(instances.size()))`. Duplicates are prevented with `contains` before `add`.
- **Injected `Random`:** the public constructor uses `new Random()`. A package-private constructor accepts a `Random`, so a test can mock it and fix the index. The mock also confirms that the bound is `nextInt(size)`.
- **Two kinds of test for randomness:** a property test (the result is always a registered instance) with the real generator, and a deterministic test with a mocked one.

### Mistakes and fixes

| Mistake | Why it's a problem | Fix |
|---|---|---|
| Suggested returning the `Set`'s first element as "random" | Unordered is not random: a `HashSet` returns the same element every time. Also, `HashSet` has no `getFirst()` | Use an explicit source of randomness (`Random`) |
| Test checking the generated random number | It's an internal detail, not visible behaviour | Verify the returned instance; the mock also checks the bound (`nextInt(size)`) |
| `get()` already returned the first element in cycle 1 | Implemented ahead of the test, so the next test passed on the first run | Write only what the current test needs |
| Loop over the address list to call `get()` | Only 3 calls, and the loop variable was never used | Use a counting loop (100 calls), independent of the list size |
| Addresses created in one place and registered from another | Two sources that only matched by chance | One list: `List.of(...)` + `addresses.forEach(balancer::register)` |
| Used `getInstances()` as the expected reference | Circular: the code was checked against itself | The expected values come from the test's own list |
| Random index test passed before any implementation | `HashSet` order happened to put the expected address first | Changed the index to 1 (middle). Investigate every test that passes on the first run |
| Considered converting the `Set` to a `List` inside `get()` | Insertion order is lost, and the whole collection is copied on every request | Change the field to `ArrayList`: O(1) index access for the most frequent operation |
| Kept `if (!instances.add(...))` after switching to `List` | `List.add` always returns `true`, so duplicates were silently accepted | Check with `contains` before `add`. The Step 1 test caught the bug |
| Message `"No instance registered"` | Minor grammar issue | `"No instances registered."` |

---

## Test results

11 test methods (7 from Step 1, 4 from Step 2). Maven reports **13 tests, all passing**, because `registeringInvalidAddressThrows` runs once for each of `null`, `""` and `"   "`.

## Project structure

```
.
├── pom.xml
└── src
    ├── main/java/LoadBalancer.java
    └── test/java/LoadBalancerTest.java
```

## Known limitations

- **Not thread-safe:** `register()` is check-then-act (`contains`/`size` followed by `add`) on a plain `ArrayList`, so two threads can both pass the checks and break the rules. A fix would be `synchronized` or an atomic operation on a concurrent structure.
- No removal of instances and no health checks. These weren't requested.
- Addresses aren't validated as URLs; any non-blank string is accepted.
- The class is in the default package.
