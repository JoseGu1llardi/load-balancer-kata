# Load Balancer Kata

A simple load balancer in Java, built step by step with TDD during a live-coding interview. It keeps a registry of backend instances and returns one of them on each call, using a selection strategy chosen at creation: random (the default) or round-robin.

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

With round-robin selection:

```java
LoadBalancer balancer = new LoadBalancer(new RoundRobinStrategy());

balancer.register("http://10.0.0.1:8080");
balancer.register("http://10.0.0.2:8080");

balancer.get();   // "http://10.0.0.1:8080"
balancer.get();   // "http://10.0.0.2:8080"
balancer.get();   // "http://10.0.0.1:8080"
```

## API

### `LoadBalancer`

| Method | Description | Throws |
|---|---|---|
| `LoadBalancer()` | Creates a load balancer with the `RandomStrategy`. | — |
| `LoadBalancer(SelectionStrategy strategy)` | Creates a load balancer with the given strategy. | `NullPointerException` if the strategy is `null` |
| `void register(String address)` | Registers a backend instance. | `IllegalArgumentException` if the address is `null` or blank; `IllegalStateException` if it is already registered or the limit of 10 is reached |
| `String get()` | Returns one of the registered instances, chosen by the strategy. | `IllegalStateException` if no instances are registered |
| `List<String> getInstances()` | Returns an immutable copy of the registered instances. | — |

**Exception rule:** `IllegalArgumentException` when the argument itself is the problem; `IllegalStateException` when the argument is valid but the object's state doesn't allow the operation.

### Strategies

| Type | Description |
|---|---|
| `SelectionStrategy` | Interface with a single method, `String select(List<String> instances)`. `LoadBalancer` only calls it with a non-empty, unmodifiable list. |
| `RandomStrategy()` | Picks a random index with a real `Random`. |
| `RandomStrategy(Random random)` | *Package-private.* Lets tests inject the random generator. |
| `RoundRobinStrategy()` | Returns the instances in order and starts again from the first one after the last. |

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

## Step 3: Round-robin selection

> Some of our clients want a different strategy. Add support for round-robin selection: instead of choosing at random, `get()` should return the instances in order, one after the other, and start again from the first one after the last.
>
> For example, with instances A, B and C, calls to `get()` should return A, B, C, A, B, C, and so on.
>
> The random strategy must keep working: it should be possible to create a load balancer with either strategy.

### Clarifications from the interviewer

1. **Can the strategy change after creation?** No. The strategy is chosen when the load balancer is created, and there's no need to change it later.
2. **What if no strategy is given?** Use random as the default. Existing clients that create a load balancer without a strategy should keep working exactly as before.
3. **What happens to the rotation when an instance is registered?** Keep it simple: the new instance joins the rotation at the end, and the sequence continues from where it was. With A, B and C, after returning A and B, if D is registered, the next calls return C, D, A, B, and so on.

### Tests

| # | Test | Where it came from |
|---|---|---|
| 1 | `returnsInstancesInOrder` | `RoundRobinStrategy`, calling `select`. The example: A, B, C |
| 2 | `startsAgainFromTheFirstAfterTheLast` | `RoundRobinStrategy`. The example: after C, back to A |
| 3 | `withOneInstanceAlwaysReturnsIt` | `RoundRobinStrategy`. Checklist: edge case |
| 4 | `newInstanceJoinsTheRotationAtTheEnd` | `LoadBalancer`, calling `register` and `get`. Clarification 3: C, D, A, B |
| 5 | `creatingWithNullStrategyThrows` | `LoadBalancer`. Invalid input at creation |
| 6 | `strategyCannotModifyTheInstances` | `LoadBalancer`. Encapsulation: a strategy can't bypass the registration rules |

Clarification 2 is covered by the existing tests: every Step 1 and Step 2 test still uses `new LoadBalancer()` and passes unchanged.

### Design decisions

- **Strategy pattern:** `LoadBalancer` keeps the registry and its rules, and delegates only the choice to a `SelectionStrategy`. Adding a strategy doesn't touch `LoadBalancer`.
- **Chosen in the constructor, stored in a `final` field:** this follows clarification 1. There is no setter.
- **Default constructor delegates:** `LoadBalancer()` calls `this(new RandomStrategy())`, so existing clients get the same behaviour as before (clarification 2).
- **The randomness moved into `RandomStrategy`:** the package-private constructor that accepts a `Random` moved with it, so the Step 2 Mockito test now builds `new LoadBalancer(new RandomStrategy(random))`.
- **Strategies are tested on their own:** `RoundRobinStrategyTest` calls `select` directly with a fixed list. Only the behaviour that depends on `register` (clarification 3) is tested through `LoadBalancer`.
- **Clarification 3 for free:** instances are kept in insertion order in an `ArrayList`, and `RoundRobinStrategy` only keeps the index of the next one, so a new instance lands at the end of the rotation.
- **Fail fast on `null`:** `Objects.requireNonNull` in the constructor, instead of a `NullPointerException` on the first `get()`.
- **The strategy gets a read-only view:** `get()` passes `Collections.unmodifiableList(instances)`. A strategy can't clear or add to the registry, and there's no copy on every request.
- **`SelectionStrategy` is a single-method interface:** a test can pass a lambda, as `strategyCannotModifyTheInstances` does.

### Mistakes and fixes

| Mistake | Why it's a problem | Fix |
|---|---|---|
| One clarifying question asked in Portuguese | In the interview, everything is in English | *"What should the default strategy be if no strategy is provided?"* |
| Clarifying questions copied from the suggested directions | In the interview, nobody gives you a list | Read the requirement, look for what it doesn't say, and write your own questions |
| `LoadBalancer(SelectionStrategy)` constructor was package-private | The requirement says clients must be able to choose the strategy, so it's part of the public API | Make the constructor `public` |
| Passed the internal mutable list to the strategy | A badly written strategy could clear or change the instances, bypassing the limit and duplicate rules | `strategy.select(Collections.unmodifiableList(instances))`: a read-only view, O(1), no copy |
| Default constructor built `new RandomStrategy(new Random())` | Duplicates a decision `RandomStrategy` already makes | `new RandomStrategy()`: each class builds its own dependencies |
| No protection against a `null` strategy | The error would only appear on the first `get()`, far from the real cause | `Objects.requireNonNull(strategy, "Strategy cannot be null.")` |
| Added `requireNonNull` without a test | Untested behaviour can be removed without anyone noticing | `assertThrows(NullPointerException.class, () -> new LoadBalancer(null))` |
| Test list checked the internal index (`...HasIndexZero`, `...IncreasesCurrentIndex`) | Internal details are invisible to callers, and the tests break if the implementation changes | Ask "what does this cause, seen from outside?": the first call returns the first instance; calls return instances in order |
| Vague test (`selectedInstanceIsReturned`) | A test needs a concrete expectation | Name and assert the exact expected instance |
| Didn't know where else to find test cases | Missed cases that were written in the requirement itself | Turn every example and every interviewer answer into a test (A, B, C, A; and C, D, A, B) |
| `currentIndex` field without `private` | Other classes in the package could change the rotation | Make the field `private` (and drop the redundant `= 0`) |
| Wrap-around (`%`) implemented in the first cycle | Implemented ahead of the test, so the next test passed on the first run | Write only what the current test needs; break the code on purpose to confirm the test can fail |
| Index stored already limited to the old list size | Works when the list grows, but could go out of bounds if the list ever shrinks | Keep an ever-increasing counter and compute `counter % size` at selection time (for the future) |
| Called one test method from another (`returnsInstancesInOrder()`) | One bug causes two failures, tests become coupled, and each test no longer reads on its own | Reuse scenarios through a `private` helper without assertions (`completeOneRound()`) |
| Mixed hard-coded strings and `instances.get(...)` as expected values | Inconsistent style, and tests break silently if the setup data changes | Use the test's own list as the single source of expected values |
| Loop `i = 0; i <= 100` | Runs 101 times, not 100 | Standard "repeat N times": `for (int i = 0; i < N; i++)` |
| 100 calls to test a deterministic behaviour | Many calls only add confidence for random behaviour | Two or three calls prove deterministic behaviour |
| Variable named `newInstances` for a one-element list | Doesn't express the intent of the test | `singleInstance` |

---

## Test results

17 test methods: 14 in `LoadBalancerTest` (7 from Step 1, 4 from Step 2, 3 from Step 3) and 3 in `RoundRobinStrategyTest`. Maven reports **19 tests, all passing**, because `registeringInvalidAddressThrows` runs once for each of `null`, `""` and `"   "`.

## Project structure

```
.
├── pom.xml
└── src
    ├── main/java
    │   ├── LoadBalancer.java
    │   ├── SelectionStrategy.java
    │   ├── RandomStrategy.java
    │   └── RoundRobinStrategy.java
    └── test/java
        ├── LoadBalancerTest.java
        └── RoundRobinStrategyTest.java
```

## Known limitations

- **Not thread-safe:** `register()` is check-then-act (`contains`/`size` followed by `add`) on a plain `ArrayList`, so two threads can both pass the checks and break the rules. `RoundRobinStrategy` has the same problem: `currentIndex` is a plain `int`, so concurrent calls can return the same instance twice. A fix would be `synchronized`, or an `AtomicInteger` for the index.
- **`RoundRobinStrategy` keeps state:** each load balancer needs its own instance. Sharing one between two load balancers mixes their rotations.
- **Round-robin assumes the list never shrinks:** if removal of instances were added, `currentIndex` could point past the end of the list and `select` would throw `IndexOutOfBoundsException`. The fix is an ever-increasing counter, with `counter % size` computed at selection time.
- No removal of instances and no health checks. These weren't requested.
- Addresses aren't validated as URLs; any non-blank string is accepted.
- The class is in the default package.
