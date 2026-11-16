@interface Mine {}

final class T {
  boolean run(Class<?> c) {
    return c.isAnnotationPresent(Mine.class);
  }

  @Documented
  @interface Hidden {}

  boolean hidden(Class<?> c) {
    return c.isAnnotationPresent(Hidden.class);
  }
}
