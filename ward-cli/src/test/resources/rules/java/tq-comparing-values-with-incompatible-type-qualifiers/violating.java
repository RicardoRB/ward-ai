@TypeQualifier
@interface Tainted {
  When when() default When.ALWAYS;
}

@TypeQualifier
@interface Strict {}

final class Check {
  boolean test(@Tainted String left, @Tainted(when = When.NEVER) String right) {
    return left == right;
  }
}
