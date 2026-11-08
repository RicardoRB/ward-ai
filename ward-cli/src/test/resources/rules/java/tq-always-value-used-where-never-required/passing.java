// passes
@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void sink(@Tainted(when = When.NEVER) String value) {}

  void test(@Tainted(when = When.NEVER) String text) {
    sink(text);
  }
}
