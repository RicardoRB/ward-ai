@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void hide(@Tainted(when = When.NEVER) String value) {}

  void test(@Tainted(when = When.UNKNOWN) String text) {
    hide(text);
  }
}
