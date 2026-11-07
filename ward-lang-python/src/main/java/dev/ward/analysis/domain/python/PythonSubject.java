package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;

/**
 * What an expression denotes: a value, a module, a class or a function. The {@code name} is the
 * module or class name, or the owner of a stub method; {@code call} is set when the expression is a
 * call of a stub.
 */
record PythonSubject(
    Kind kind, TypeRef type, String name, SyntaxNode definition, String module, CallTarget call) {

  /** The kinds of denotation. */
  enum Kind {
    /** A value of {@code type}, which may be unknown. */
    VALUE,
    /** A module. */
    MODULE,
    /** A class. */
    CLASS,
    /** A function declared in this file. */
    SOURCE_FUNCTION,
    /** A function or method declared by a stub. */
    STUB_FUNCTION,
    /** Nothing known. */
    UNKNOWN
  }

  /** The unknown denotation. */
  static final PythonSubject UNKNOWN =
      new PythonSubject(Kind.UNKNOWN, null, null, null, null, null);

  /** A value of {@code type}. */
  static PythonSubject value(final TypeRef type) {
    return new PythonSubject(Kind.VALUE, type, null, null, null, null);
  }

  /** The result of a call of a stub, returning {@code type}. */
  static PythonSubject called(final TypeRef type, final CallTarget call) {
    return new PythonSubject(Kind.VALUE, type, null, null, null, call);
  }

  /** The module {@code name}. */
  static PythonSubject module(final String name) {
    return new PythonSubject(Kind.MODULE, null, name, null, null, null);
  }

  /** The class {@code name}. */
  static PythonSubject klass(final String name) {
    return new PythonSubject(Kind.CLASS, null, name, null, null, null);
  }

  /** The function declared by {@code definition} in this file. */
  static PythonSubject sourceFunction(final SyntaxNode definition) {
    return new PythonSubject(Kind.SOURCE_FUNCTION, null, null, definition, null, null);
  }

  /**
   * The stub function {@code function}, a method of {@code owner} or null for a module function.
   */
  static PythonSubject stubFunction(final PythonStubs.StubFunction function, final String owner) {
    return new PythonSubject(
        Kind.STUB_FUNCTION, null, owner, function.definition(), function.module(), null);
  }
}
