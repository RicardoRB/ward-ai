// padding 0 keeps the positions apart from violating.java
// padding 1 keeps the positions apart from violating.java
// padding 2 keeps the positions apart from violating.java
// padding 3 keeps the positions apart from violating.java
// padding 4 keeps the positions apart from violating.java
// padding 5 keeps the positions apart from violating.java
// padding 6 keeps the positions apart from violating.java
// padding 7 keeps the positions apart from violating.java
// padding 8 keeps the positions apart from violating.java
// padding 9 keeps the positions apart from violating.java
// padding 10 keeps the positions apart from violating.java
// padding 11 keeps the positions apart from violating.java
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

class Always {
  void run(String text) {
    Instant.parse("2020-01-01T00:00:00Z");
    UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    Instant.now().plus(Duration.ofDays(1));
    ImmutableMap.of("a", 1, "b", 2);
    Instant.parse(text);
  }
}
