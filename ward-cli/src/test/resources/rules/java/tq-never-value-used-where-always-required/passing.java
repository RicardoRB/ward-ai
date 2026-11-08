// passes
@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void need(@Tainted String value) {}

  void test(@Tainted String text) {
    String copy = text;
    need(copy);
  }
}
