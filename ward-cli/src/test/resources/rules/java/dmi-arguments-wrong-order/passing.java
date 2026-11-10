// passes
final class Check {
  Object test(Object name) {
    return Objects.requireNonNull(name, "name");
  }
}
