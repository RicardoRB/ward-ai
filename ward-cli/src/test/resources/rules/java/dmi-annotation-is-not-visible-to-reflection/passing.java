// passes
@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
}

final class Check {
  boolean test(Class<?> type) {
    return type.isAnnotationPresent(Marker.class);
  }
}
