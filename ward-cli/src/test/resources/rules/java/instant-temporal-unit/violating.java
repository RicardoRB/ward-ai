final class Temporal {
  Object run(java.time.Instant value) {
    return value.plus(1, java.time.temporal.ChronoUnit.MONTHS);
  }
}
