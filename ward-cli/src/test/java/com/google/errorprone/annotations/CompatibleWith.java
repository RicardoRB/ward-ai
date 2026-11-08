package com.google.errorprone.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A stand-in for Error Prone's annotation, so that a test class file carries the real name. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface CompatibleWith {
  /**
   * Returns the name of the type variable the argument must be compatible with.
   *
   * @return the type variable name
   */
  String value();
}
