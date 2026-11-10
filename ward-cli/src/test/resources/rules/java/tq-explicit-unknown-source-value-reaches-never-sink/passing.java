// passes
@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void hide(@Tainted(when = When.NEVER) String value) {}

  void test(String text) {
    hide(text);
  }
}
