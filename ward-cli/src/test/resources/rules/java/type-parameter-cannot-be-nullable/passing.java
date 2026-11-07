import org.jspecify.annotations.Nullable;

class Box<T extends @Nullable Object> {}

class Pair<A, B extends @Nullable Object> {}

class Users {
  Box<@Nullable String> first = new Box<@Nullable String>();
  Pair<String, @Nullable String> second;
  Other<@Nullable String> unknown;
}
