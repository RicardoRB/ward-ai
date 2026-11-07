final class Check {
  Object field(Class<?> type) throws NoSuchFieldException {
    return type.getDeclaredField("value");
  }
}
