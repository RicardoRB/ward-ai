// passes
@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
}

final class Check {
  boolean test(Class<?> type) {
    return type.isAnnotationPresent(Marker.class);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Nested {}

  @Target(ElementType.TYPE)
  @Retention(RUNTIME)
  public @interface Imported {}

  boolean nested(Class<?> c) {
    return c.isAnnotationPresent(Nested.class) || c.getAnnotation(Imported.class) != null;
  }
}
