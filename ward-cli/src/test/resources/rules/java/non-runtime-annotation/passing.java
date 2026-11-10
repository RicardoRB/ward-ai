@Retention(RetentionPolicy.RUNTIME)
@interface Mine {}

final class T {
  boolean run(Class<?> c) {
    return c.isAnnotationPresent(Mine.class);
  }

  boolean other(Class<?> c) {
    return c.isAnnotationPresent(Unknown.class);
  }
}
