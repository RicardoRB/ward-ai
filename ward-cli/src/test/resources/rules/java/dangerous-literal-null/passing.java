
import java.util.Optional;

class Sample {
  String a(Optional<String> o) {
    return o.orElse(null);
  }

  String b(Optional<String> o) {
    return o.orElseGet(() -> "x");
  }

  String c(Optional<String> o) {
    return o.orElseThrow(IllegalStateException::new);
  }
}
