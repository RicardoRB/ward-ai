package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an exception instance created as a statement and never raised, like pylint {@code
 * pointless-exception-statement}: a builtin exception class, or a class of this module that derives
 * from one, called on its own line.
 */
public final class ExceptionStatementCheck implements RuleCheck {

  private static final Set<String> BUILTIN =
      Set.of(
          "ArithmeticError",
          "AssertionError",
          "AttributeError",
          "BaseException",
          "BlockingIOError",
          "BrokenPipeError",
          "BufferError",
          "ConnectionError",
          "EOFError",
          "EnvironmentError",
          "Exception",
          "FileExistsError",
          "FileNotFoundError",
          "FloatingPointError",
          "IOError",
          "ImportError",
          "IndexError",
          "InterruptedError",
          "IsADirectoryError",
          "KeyError",
          "KeyboardInterrupt",
          "LookupError",
          "MemoryError",
          "ModuleNotFoundError",
          "NameError",
          "NotADirectoryError",
          "NotImplementedError",
          "OSError",
          "OverflowError",
          "PermissionError",
          "ProcessLookupError",
          "RecursionError",
          "ReferenceError",
          "RuntimeError",
          "StopIteration",
          "SyntaxError",
          "SystemError",
          "TimeoutError",
          "TypeError",
          "UnboundLocalError",
          "UnicodeError",
          "ValueError",
          "ZeroDivisionError");
  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> STATEMENTS = Set.of("expr_stmt");
  private static final Pattern CLASS_HEADER = Pattern.compile("^class(\\w+)\\(([^)]*)\\):");
  private static final Pattern CALL = Pattern.compile("^(\\w+)\\(");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ExceptionStatementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> exceptions = new HashSet<>(BUILTIN);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      addIfException(SyntaxWalk.text(visit.node()), exceptions);
    }
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final Matcher call = CALL.matcher(SyntaxWalk.text(visit.node()));
      if (call.find() && exceptions.contains(call.group(1))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Adds a class of this module when one of its bases is an exception class already known. */
  private static void addIfException(final String classText, final Set<String> exceptions) {
    final Matcher header = CLASS_HEADER.matcher(classText);
    if (!header.find()) {
      return;
    }
    for (final String base : header.group(2).split(",")) {
      if (exceptions.contains(base.strip())) {
        exceptions.add(header.group(1));
        return;
      }
    }
  }
}
