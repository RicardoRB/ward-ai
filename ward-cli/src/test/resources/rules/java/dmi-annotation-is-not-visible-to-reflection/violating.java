@interface Marker {
}

final class Check {
  boolean test(Class<?> type) {
    return type.isAnnotationPresent(Marker.class);
  }

  @Documented
  @interface Hidden {}

  boolean hidden(Class<?> c) {
    return c.isAnnotationPresent(Hidden.class);
  }
}
