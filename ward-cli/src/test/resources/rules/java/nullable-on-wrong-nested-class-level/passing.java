import org.jspecify.annotations.Nullable;

class Nested {
  Outer.@Nullable Inner first;
  @Nullable Outer second;
  @Nullable java.util.List<String> third;
}
