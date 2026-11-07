final class Temporal {
  Object run(java.time.Period value) {
    return value.get(java.time.temporal.ChronoUnit.HOURS);
  }
}
