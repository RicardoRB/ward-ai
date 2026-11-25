import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Creation {
  @Target(ElementType.TYPE_USE)
  @interface Nullable {}

  @Target(ElementType.TYPE_USE)
  @interface A {}

  @Target(ElementType.TYPE_USE)
  @interface B {}

  String[] elementType(String first, String last) {
    return new @Nullable String[] {first, last};
  }

  String[] arrayType() {
    return new String @Nullable [] {"x"};
  }

  int[][] dimensions() {
    return new int @A [2] @B [];
  }

  Object instance() {
    return new @A Object();
  }

  Object qualifiedInstance() {
    return new <String> @A Object();
  }

  int[][] initialized() {
    return new int @A [] @B [] {{1}, {2}};
  }
}
