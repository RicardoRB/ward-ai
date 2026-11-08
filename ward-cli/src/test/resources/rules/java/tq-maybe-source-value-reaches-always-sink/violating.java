@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void need(@Tainted String value) {}

  void test(@Tainted(when = When.MAYBE) String text) {
    need(text);
  }
}
