package dev.ward.domain.license;

import java.util.ArrayList;
import java.util.List;

/** An SPDX license expression: identifiers, {@code WITH} exceptions, {@code AND} and {@code OR}. */
public sealed interface LicenseExpression {

  /** Returns the expression in SPDX syntax, with parentheses only where precedence needs them. */
  String render();

  /** Returns the license identifiers of every term, in order of appearance. */
  List<String> identifiers();

  /**
   * One license identifier, optionally with an exception.
   *
   * @param id the SPDX identifier, or a {@code LicenseRef-*} identifier
   * @param exception the identifier after {@code WITH}, or {@code null} when there is none
   */
  record Term(String id, String exception) implements LicenseExpression {
    @Override
    public String render() {
      return exception == null ? id : id + " WITH " + exception;
    }

    @Override
    public List<String> identifiers() {
      return List.of(id);
    }
  }

  /**
   * Both operands apply.
   *
   * @param left the first operand
   * @param right the second operand
   */
  record And(LicenseExpression left, LicenseExpression right) implements LicenseExpression {
    @Override
    public String render() {
      return operand(left) + " AND " + operand(right);
    }

    @Override
    public List<String> identifiers() {
      return concat(left, right);
    }

    private static String operand(final LicenseExpression expression) {
      return expression instanceof Or ? "(" + expression.render() + ")" : expression.render();
    }
  }

  /**
   * A choice of one operand.
   *
   * @param left the first alternative
   * @param right the second alternative
   */
  record Or(LicenseExpression left, LicenseExpression right) implements LicenseExpression {
    @Override
    public String render() {
      return left.render() + " OR " + right.render();
    }

    @Override
    public List<String> identifiers() {
      return concat(left, right);
    }
  }

  /** Joins the identifiers of two operands. */
  private static List<String> concat(final LicenseExpression left, final LicenseExpression right) {
    final List<String> all = new ArrayList<>(left.identifiers());
    all.addAll(right.identifiers());
    return List.copyOf(all);
  }
}
