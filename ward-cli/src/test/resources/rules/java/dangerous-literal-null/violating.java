import java.util.Optional;

class Sample {
  String a(Optional<String> o) {
    return o.orElseGet(null);
  }

  String b(Optional<String> o) {
    return o.orElseThrow(null);
  }
}
