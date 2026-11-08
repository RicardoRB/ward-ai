@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void sink(@Tainted(when = When.NEVER) String value) {}

  void test(@Tainted String text) {
    sink(text);
  }
}
