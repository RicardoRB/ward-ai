@Retention(RetentionPolicy.RUNTIME)
@interface Mine {}

final class T {
  boolean run(Class<?> c) {
    return c.isAnnotationPresent(Mine.class);
  }

  boolean other(Class<?> c) {
    return c.isAnnotationPresent(Unknown.class);
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
