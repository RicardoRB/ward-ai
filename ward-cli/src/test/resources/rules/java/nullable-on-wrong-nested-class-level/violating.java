import org.jspecify.annotations.Nullable;

class Nested {
  @Nullable Outer.Inner first;
  @Nullable Outer.Inner<String> second;
}
