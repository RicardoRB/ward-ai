import org.jspecify.annotations.Nullable;

class Box<T> {}

class Pair<A, B extends @Nullable Object> {}

class Users {
  Box<@Nullable String> first = new Box<@Nullable String>();
  Pair<@Nullable String, String> second;
}
