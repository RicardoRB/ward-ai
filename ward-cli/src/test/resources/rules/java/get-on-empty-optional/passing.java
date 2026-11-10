import java.util.Optional;

class Optionals {
  String first(Optional<String> value) {
    if (value.isPresent()) {
      return value.get();
    }
    if (!value.isEmpty()) {
      return value.get();
    }
    return Optional.of("x").get();
  }
}
