import java.util.Optional;

class Optionals {
  String first() {
    return Optional.empty().get();
  }

  String second(Optional<String> value) {
    if (!value.isPresent()) {
      return value.get();
    }
    if (value.isEmpty()) {
      return value.get();
    }
    return "";
  }
}
