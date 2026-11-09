package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a method of a metaclass whose first parameter has the wrong name, like pylint's {@code
 * bad-mcs-classmethod-argument} (a class method takes {@code mcs}) and {@code
 * bad-mcs-method-argument} (an instance method takes {@code cls}). A class is a metaclass when a
 * base is {@code type}, {@code ABCMeta} or a name ending in {@code Meta}; the check does not follow
 * base classes further.
 */
public final class PythonMetaclassArgumentCheck implements RuleCheck {

  /** The mistake the check reports, one per rule. */
  public enum Problem {
    /** A class method of a metaclass whose first parameter is not {@code mcs}. */
    CLASSMETHOD,
    /** An instance method of a metaclass whose first parameter is not {@code cls}. */
    METHOD
  }

  private static final String STMT = "stmt";
  private static final String COMPOUND = "compound_stmt";
  private static final String DECORATED = "decorated";
  private static final String FUNCDEF = "funcdef";
  private static final String BLOCK = "block";
  private static final String TYPED_PARAMETER = "tfpdef";
  private static final String CLASSMETHOD = "@classmethod";
  private static final String STATICMETHOD = "@staticmethod";
  private static final String META_CLS = "cls";
  private static final String META_MCS = "mcs";
  private static final Pattern METACLASS_BASE =
      Pattern.compile("^(?:\\w+\\.)*(?:type|ABCMeta|\\w*Meta)$");
  private static final Pattern CLASS_HEADER = Pattern.compile("^class\\w+\\(([^)]*)\\)");
  private static final Pattern IDENTIFIER = Pattern.compile("\\w+");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public PythonMetaclassArgumentCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if ("classdef".equals(node.kind()) && isMetaclass(node)) {
      methods(node, found);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found);
    }
  }

  private void methods(final SyntaxNode classdef, final List<RuleMatch> found) {
    for (final SyntaxNode block : children(classdef, BLOCK)) {
      for (final SyntaxNode statement : children(block, STMT)) {
        for (final SyntaxNode compound : children(statement, COMPOUND)) {
          method(compound, found);
        }
      }
    }
  }

  private void method(final SyntaxNode compound, final List<RuleMatch> found) {
    final Optional<SyntaxNode> function = function(compound);
    if (function.isEmpty()) {
      return;
    }
    final String decorators = decoratorText(compound);
    final boolean classMethod = decorators.contains(CLASSMETHOD);
    if (decorators.contains(STATICMETHOD)) {
      return;
    }
    final String first = firstParameter(function.get());
    if (first.isEmpty() || wanted(classMethod).equals(first)) {
      return;
    }
    if (problem == Problem.CLASSMETHOD && classMethod
        || problem == Problem.METHOD && !classMethod) {
      found.add(new RuleMatch(function.get().span(), message));
    }
  }

  private static String wanted(final boolean classMethod) {
    return classMethod ? META_MCS : META_CLS;
  }

  /** The function a compound statement defines, decorated or not. */
  private static Optional<SyntaxNode> function(final SyntaxNode compound) {
    for (final SyntaxNode child : compound.children()) {
      if (FUNCDEF.equals(child.kind())) {
        return Optional.of(child);
      }
      if (DECORATED.equals(child.kind())) {
        return children(child, FUNCDEF).stream().findFirst();
      }
    }
    return Optional.empty();
  }

  private static String decoratorText(final SyntaxNode compound) {
    final StringBuilder text = new StringBuilder();
    for (final SyntaxNode child : compound.children()) {
      if (DECORATED.equals(child.kind())) {
        children(child, "decorators")
            .forEach(decorators -> text.append(SyntaxWalk.text(decorators)));
      }
    }
    return text.toString();
  }

  /** The first parameter of a function, read from its signature before the body. */
  private static String firstParameter(final SyntaxNode function) {
    final int bodyStart =
        children(function, BLOCK).stream()
            .findFirst()
            .map(block -> block.span().startOffset())
            .orElse(Integer.MAX_VALUE);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of(TYPED_PARAMETER))) {
      if (visit.node().span().startOffset() < bodyStart) {
        return SyntaxWalk.leaves(visit.node()).stream()
            .map(SyntaxWalk::text)
            .filter(text -> IDENTIFIER.matcher(text).matches())
            .findFirst()
            .orElse("");
      }
    }
    return "";
  }

  /** Tells whether a class header lists a metaclass among its bases. */
  private static boolean isMetaclass(final SyntaxNode classdef) {
    final Matcher header = CLASS_HEADER.matcher(SyntaxWalk.text(classdef));
    return header.find()
        && Arrays.stream(header.group(1).split(","))
            .anyMatch(base -> METACLASS_BASE.matcher(base).matches());
  }

  private static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        found.add(child);
      }
    }
    return found;
  }
}
