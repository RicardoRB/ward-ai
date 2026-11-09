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
 * Reports an instance attribute that a method assigns but no defining method ({@code __init__},
 * {@code __new__}, {@code setUp}, {@code asyncSetUp}, {@code __post_init__}, or a property setter)
 * assigns, like Pylint's {@code attribute-defined-outside-init}. An attribute that is a class-level
 * name, or that a defining method calls the method of, is not reported. Shortcut: the attributes of
 * base classes are not read, so an attribute a base defines is reported here.
 */
public final class AttributeOutsideInitCheck implements RuleCheck {

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> SCOPES =
      Set.of("funcdef", "async_funcdef", "lambdef", "classdef");
  private static final Set<String> DEFINING =
      Set.of("__init__", "__new__", "setUp", "asyncSetUp", "__post_init__");
  private static final Pattern SELF_TARGET = Pattern.compile("^self\\.(\\w+)$");
  private static final Pattern ACCESSOR = Pattern.compile("@\\w+\\.(setter|deleter)");
  private static final String MESSAGE_NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the attribute
   */
  public AttributeOutsideInitCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      found.addAll(classFindings(visit.node()));
    }
    return List.copyOf(found);
  }

  /** The findings of one class: its methods' assignments that no defining method accounts for. */
  private List<RuleMatch> classFindings(final SyntaxNode classNode) {
    final List<RuleMatch> found = new ArrayList<>();
    final List<SyntaxNode> methods = new ArrayList<>();
    final List<SyntaxNode> accessors = new ArrayList<>();
    final Set<String> classLevel = new HashSet<>();
    for (final SyntaxNode child : classNode.children()) {
      if ("block".equals(child.kind())) {
        collectMethodsAndNames(child, methods, accessors, classLevel);
      }
    }
    final Set<String> defined = new HashSet<>(classLevel);
    final StringBuilder definingText = new StringBuilder();
    final List<Assignment> assignments = new ArrayList<>();
    for (final SyntaxNode method : methods) {
      final String name = methodName(method);
      final boolean defining = DEFINING.contains(name) || accessors.contains(method);
      if (defining) {
        definingText.append(SyntaxWalk.text(method));
      }
      for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(method, Set.of("expr_stmt"))) {
        if (visit.ancestors().stream().skip(1).anyMatch(a -> SCOPES.contains(a.kind()))) {
          continue;
        }
        final Assignment assignment = assignmentOf(visit.node(), name, defining);
        if (assignment != null) {
          assignments.add(assignment);
          if (defining) {
            defined.add(assignment.attribute());
          }
        }
      }
    }
    for (final Assignment assignment : assignments) {
      if (assignment.defining() || defined.contains(assignment.attribute())) {
        continue;
      }
      if (definingText.toString().contains("self." + assignment.method() + "(")) {
        continue;
      }
      found.add(
          new RuleMatch(
              assignment.node().span(), message.replace(MESSAGE_NAME, assignment.attribute())));
    }
    return found;
  }

  private static void collectMethodsAndNames(
      final SyntaxNode block,
      final List<SyntaxNode> methods,
      final List<SyntaxNode> accessors,
      final Set<String> classLevel) {
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(block, Set.of("funcdef", "classdef", "expr_stmt"))) {
      final boolean nested = visit.ancestors().stream().anyMatch(a -> SCOPES.contains(a.kind()));
      if (nested) {
        continue;
      }
      final SyntaxNode node = visit.node();
      if ("funcdef".equals(node.kind())) {
        methods.add(node);
        if (decoratedAsAccessor(visit.ancestors())) {
          accessors.add(node);
        }
        classLevel.add(methodName(node));
      } else if ("classdef".equals(node.kind())) {
        classLevel.add(methodName(node));
      } else if (node.children().size() >= 3
          && "=".equals(SyntaxWalk.text(node.children().get(1)))) {
        classLevel.add(SyntaxWalk.text(node.children().get(0)));
      }
    }
  }

  /** Whether the function's decorators make it a property setter or deleter. */
  private static boolean decoratedAsAccessor(final List<SyntaxNode> ancestors) {
    return !ancestors.isEmpty()
        && "decorated".equals(ancestors.get(ancestors.size() - 1).kind())
        && ACCESSOR.matcher(SyntaxWalk.text(ancestors.get(ancestors.size() - 1))).find();
  }

  /** The name of a function or class, the text of its name node. */
  private static String methodName(final SyntaxNode definition) {
    return definition.children().stream()
        .filter(child -> "name".equals(child.kind()) || "NAME".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  /**
   * An attribute assignment in a method: the attribute, the statement and whether it is defining.
   */
  private record Assignment(String attribute, SyntaxNode node, String method, boolean defining) {}

  private static Assignment assignmentOf(
      final SyntaxNode statement, final String method, final boolean defining) {
    final List<SyntaxNode> children = statement.children();
    if (children.size() < 3 || !"=".equals(SyntaxWalk.text(children.get(1)))) {
      return null;
    }
    final Matcher matcher = SELF_TARGET.matcher(SyntaxWalk.text(children.get(0)));
    if (!matcher.matches()) {
      return null;
    }
    return new Assignment(matcher.group(1), statement, method, defining);
  }
}
