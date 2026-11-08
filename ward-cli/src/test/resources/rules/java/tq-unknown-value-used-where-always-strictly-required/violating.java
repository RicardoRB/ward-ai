@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  void need(@Strict String value) {}

  void test(String text) {
    need(text);
  }
}
