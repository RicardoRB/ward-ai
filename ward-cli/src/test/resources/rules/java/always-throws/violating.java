import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.Period;
import java.util.UUID;

class Always {
  void run() {
    Instant.parse("not an instant");
    UUID.fromString("zzz");
    Instant.now().plus(Period.ofMonths(1));
    ImmutableMap.of("a", 1, "a", 2);
  }
}
