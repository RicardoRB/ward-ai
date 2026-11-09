package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a method in a class body whose first parameter is not {@code self}, like pylint {@code
 * no-self-argument}. Static methods, class methods, {@code __class_getitem__} and the constructor
 * hooks are not regular methods and are not judged. Metaclass methods are judged by the metaclass
 * rule of their own, so they are skipped here. A method without a parameter is not reported.
 *
 * <p>Shortcut: a class is a metaclass when a base is named {@code type} or ends in {@code Meta},
 * {@code Type}; a metaclass declared under another name is judged as a regular class.
 */
public final class FirstArgumentCheck implements RuleCheck {

  private static final Set<String> SCOPES =
      Set.of("funcdef", "classdef", "async_funcdef", "lambdef");
  private static final Set<String> EXEMPT_NAMES =
      Set.of("__class_getitem__", "__new__", "__init_subclass__");
  private static final Pattern BASES =
      Pattern.compile("^(?:async\\s*)?class\\s*\\w+\\s*\\(([^)]*)\\)\\s*:", Pattern.DOTALL);
  private static final Pattern METACLASS_BASE = Pattern.compile("^(type|\\w*Meta|\\w*Type)$");
  private static final Pattern FIRST_NAME = Pattern.compile("^\\**\\s*(\\w+)");
  private static final String DEF = "def";
  private static final String SELF = "self";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public FirstArgumentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      if (judged(visit.ancestors()) && wrongFirst(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** True when the function is a regular method of a class that is not a metaclass. */
  private static boolean judged(final List<SyntaxNode> ancestors) {
    final SyntaxNode owner = nearestScope(ancestors);
    if (owner == null || !"classdef".equals(owner.kind())) {
      return false;
    }
    final String header = SyntaxWalk.text(owner);
    return !metaclass(header) && !FunctionDecorators.exemptDecorator(ancestors);
  }

  private static SyntaxNode nearestScope(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (SCOPES.contains(ancestors.get(index).kind())) {
        return ancestors.get(index);
      }
    }
    return null;
  }

  private static boolean metaclass(final String classText) {
    final Matcher header = BASES.matcher(classText.strip());
    if (!header.find()) {
      return false;
    }
    for (final String base : header.group(1).split(",")) {
      final String name = base.strip();
      if (!name.contains("=") && METACLASS_BASE.matcher(name).matches()) {
        return true;
      }
    }
    return false;
  }

  private static boolean wrongFirst(final SyntaxNode function) {
    final String name = functionName(function);
    if (name.isEmpty() || EXEMPT_NAMES.contains(name)) {
      return false;
    }
    final String first = firstParameter(function);
    return first != null && !SELF.equals(first);
  }

  private static String functionName(final SyntaxNode function) {
    final List<SyntaxNode> children = function.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (DEF.equals(SyntaxWalk.text(children.get(index)))) {
        return SyntaxWalk.text(children.get(index + 1));
      }
    }
    return "";
  }

  /** The name of the first parameter, or null when the function declares none. */
  private static String firstParameter(final SyntaxNode function) {
    for (final SyntaxNode child : function.children()) {
      if (SyntaxKinds.PARAMETERS.equals(child.kind())) {
        return firstName(SyntaxWalk.text(child));
      }
    }
    return null;
  }

  private static String firstName(final String parameters) {
    final String inside = parameters.strip().replaceAll("^\\(", "").replaceAll("\\)$", "").strip();
    if (inside.isEmpty()) {
      return null;
    }
    final Matcher name = FIRST_NAME.matcher(inside);
    return name.find() ? name.group(1) : null;
  }
}
