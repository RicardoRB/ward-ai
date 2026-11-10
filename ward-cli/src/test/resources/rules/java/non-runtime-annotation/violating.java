@interface Mine {}

final class T {
  boolean run(Class<?> c) {
    return c.isAnnotationPresent(Mine.class);
  }
}
