
final class Temporal {
  Object run(java.time.Duration value) {
    return value.plus(1, java.time.temporal.ChronoUnit.HOURS);
  }
}
