import java.util.Optional;

class Finders {
  Optional<String> first() {
    return null;
  }

  java.util.Optional<String> second(boolean flag) {
    if (flag) {
      return null;
    }
    return Optional.empty();
  }
}
