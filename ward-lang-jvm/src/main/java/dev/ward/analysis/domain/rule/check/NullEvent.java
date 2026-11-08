package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * One thing a block does to the nullness of its local variables, in evaluation order: a write, a
 * dereference, a call that receives values, or a use that needs a value that is not null.
 *
 * @param kind what happens
 * @param variable the declaration of the variable written, killed, dereferenced or compared
 * @param node the expression the event is about, where a finding about it is reported
 * @param operand the value written, returned or stored
 * @param args the arguments of a call
 * @param name the method of a dereferencing call, or the field of a store
 */
record NullEvent(
    Kind kind, Span variable, SyntaxNode node, Operand operand, List<Arg> args, String name) {

  /** What an event does. */
  enum Kind {
    /** A local gets a value. */
    WRITE,
    /** A local gets a value that is not tracked. */
    KILL,
    /** A local is dereferenced. */
    DEREF,
    /** A method or constructor is called. */
    CALL,
    /** A value is returned. */
    RETURN,
    /** A value is stored into a field. */
    STORE,
    /** A local is compared with null. */
    COMPARE
  }

  /**
   * An expression as the analysis sees it.
   *
   * @param variable the local it names, or null
   * @param isNull whether it is the literal null
   * @param nonNull whether it is never null: a new object, a literal or this
   */
  record Operand(Span variable, boolean isNull, boolean nonNull) {
    /** An expression the analysis knows nothing about. */
    static final Operand UNKNOWN = new Operand(null, false, false);
  }

  /**
   * An argument of a call.
   *
   * @param operand the value
   * @param node the expression
   */
  record Arg(Operand operand, SyntaxNode node) {}

  NullEvent {
    args = List.copyOf(args);
  }

  static NullEvent write(final Span variable, final SyntaxNode node, final Operand operand) {
    return new NullEvent(Kind.WRITE, variable, node, operand, List.of(), "");
  }

  static NullEvent kill(final Span variable) {
    return new NullEvent(Kind.KILL, variable, null, Operand.UNKNOWN, List.of(), "");
  }

  static NullEvent deref(final Span variable, final SyntaxNode node, final String method) {
    return new NullEvent(Kind.DEREF, variable, node, Operand.UNKNOWN, List.of(), method);
  }

  static NullEvent call(final SyntaxNode node, final List<Arg> args, final String method) {
    return new NullEvent(Kind.CALL, null, node, Operand.UNKNOWN, args, method);
  }

  static NullEvent returned(final SyntaxNode node, final Operand operand) {
    return new NullEvent(Kind.RETURN, null, node, operand, List.of(), "");
  }

  static NullEvent store(final SyntaxNode node, final Operand operand, final String field) {
    return new NullEvent(Kind.STORE, null, node, operand, List.of(), field);
  }

  static NullEvent compare(final Span variable, final SyntaxNode node) {
    return new NullEvent(Kind.COMPARE, variable, node, Operand.UNKNOWN, List.of(), "");
  }
}
