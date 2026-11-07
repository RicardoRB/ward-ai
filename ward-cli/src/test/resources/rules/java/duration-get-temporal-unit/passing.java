
final class Temporal {
  Object run(java.time.Duration value) {
    return value.get(java.time.temporal.ChronoUnit.SECONDS);
  }
}
