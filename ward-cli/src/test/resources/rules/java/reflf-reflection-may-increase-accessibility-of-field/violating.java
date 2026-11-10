final class Check {
  Object field(Class<?> type, String name) throws NoSuchFieldException {
    return type.getDeclaredField(name);
  }
}
