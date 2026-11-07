import org.jspecify.annotations.Nullable;

class Varargs {
  void first(String @Nullable ... values) {}

  void second(int count, Object @Nullable ... rest) {}
}
