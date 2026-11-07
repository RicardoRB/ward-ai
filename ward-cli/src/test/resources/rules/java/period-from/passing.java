final class T {
  Object run(Period p, TemporalAmount t) {
    // ok
    return Period.from(t);
  }
}
