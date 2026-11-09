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
 * The checks on a class's {@code __slots__}, like Pylint's {@code class-variable-slots-conflict} (a
 * slot name that a class-level definition also binds), {@code declare-non-slot} (an annotated class
 * attribute that is not a slot) and {@code assigning-non-slot} (a {@code self} attribute that is
 * not a slot). The last two apply to a class with no bases and no {@code __dict__} slot. Shortcut:
 * a base class is judged by its presence, not by its own slots.
 */
public final class SlotsCheck implements RuleCheck {

  /** The problem each check reports. */
  public enum Problem {
    CLASS_VARIABLE_CONFLICT,
    DECLARE_NON_SLOT,
    ASSIGNING_NON_SLOT
  }

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> SCOPES =
      Set.of("funcdef", "async_funcdef", "lambdef", "classdef");
  private static final String SLOTS = "__slots__";
  private static final String DICT = "__dict__";
  private static final Pattern SELF_TARGET = Pattern.compile("^self\\.(\\w+)$");
  private static final Pattern STRING =
      Pattern.compile("^(?:[rRbBuU]?)(['\"])(.*)\\1$", Pattern.DOTALL);

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the problem this check reports
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public SlotsCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
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

  private List<RuleMatch> classFindings(final SyntaxNode classNode) {
    final SyntaxNode body =
        classNode.children().stream()
            .filter(child -> "block".equals(child.kind()))
            .findFirst()
            .orElse(null);
    if (body == null) {
      return List.of();
    }
    final SyntaxNode slotsValue = slotsValue(body);
    if (slotsValue == null) {
      return List.of();
    }
    final List<SyntaxNode> slotLeaves = new ArrayList<>();
    collectStrings(slotsValue, slotLeaves);
    if (slotLeaves.isEmpty() && !SyntaxWalk.text(slotsValue).startsWith("(")) {
      return List.of();
    }
    final Set<String> slots = new HashSet<>();
    slotLeaves.forEach(leaf -> slots.add(unquote(SyntaxWalk.text(leaf))));
    return switch (problem) {
      case CLASS_VARIABLE_CONFLICT -> conflicts(body, slotLeaves);
      case DECLARE_NON_SLOT -> declares(body, slots, classNode);
      case ASSIGNING_NON_SLOT -> assignments(body, slots, classNode);
    };
  }

  private List<RuleMatch> conflicts(final SyntaxNode body, final List<SyntaxNode> slotLeaves) {
    final Set<String> classLevel = classLevelNames(body);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode leaf : slotLeaves) {
      final String name = unquote(SyntaxWalk.text(leaf));
      if (classLevel.contains(name)) {
        found.add(new RuleMatch(leaf.span(), message.replace("{value}", name)));
      }
    }
    return found;
  }

  private List<RuleMatch> declares(
      final SyntaxNode body, final Set<String> slots, final SyntaxNode classNode) {
    final List<RuleMatch> found = new ArrayList<>();
    if (slots.contains(DICT) || hasBases(classNode)) {
      return found;
    }
    for (final SyntaxNode statement : body.children()) {
      final SyntaxNode simple = unwrap(statement);
      if (simple == null || simple.children().size() != 2) {
        continue;
      }
      final String annotation = SyntaxWalk.text(simple.children().get(1));
      final String name = SyntaxWalk.text(simple.children().get(0));
      if (annotation.startsWith(":")
          && !annotation.contains("ClassVar")
          && !slots.contains(name)
          && name.matches("\\w+")) {
        found.add(new RuleMatch(simple.children().get(0).span(), message.replace("{value}", name)));
      }
    }
    return found;
  }

  private List<RuleMatch> assignments(
      final SyntaxNode body, final Set<String> slots, final SyntaxNode classNode) {
    final List<RuleMatch> found = new ArrayList<>();
    if (slots.contains(DICT) || hasBases(classNode)) {
      return found;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(body, Set.of("expr_stmt"))) {
      if (!directlyInMethod(visit.ancestors())) {
        continue;
      }
      final List<SyntaxNode> children = visit.node().children();
      if (children.size() < 3 || !"=".equals(SyntaxWalk.text(children.get(1)))) {
        continue;
      }
      final Matcher matcher = SELF_TARGET.matcher(SyntaxWalk.text(children.get(0)));
      if (matcher.matches() && !slots.contains(matcher.group(1))) {
        found.add(new RuleMatch(visit.node().span(), message.replace("{value}", matcher.group(1))));
      }
    }
    return found;
  }

  /** Whether the nearest enclosing scope is a method, and the only scope around it. */
  private static boolean directlyInMethod(final List<SyntaxNode> ancestors) {
    final List<SyntaxNode> scopes =
        ancestors.stream().filter(a -> SCOPES.contains(a.kind())).toList();
    return scopes.size() == 1 && "funcdef".equals(scopes.get(0).kind());
  }

  /** The value of the class-level {@code __slots__ = ...} statement, or null. */
  private static SyntaxNode slotsValue(final SyntaxNode body) {
    for (final SyntaxNode statement : body.children()) {
      final SyntaxNode simple = unwrap(statement);
      if (simple != null
          && simple.children().size() == 3
          && SLOTS.equals(SyntaxWalk.text(simple.children().get(0)))
          && "=".equals(SyntaxWalk.text(simple.children().get(1)))) {
        return simple.children().get(2);
      }
    }
    return null;
  }

  /** The expression statement inside a block statement, or null. */
  private static SyntaxNode unwrap(final SyntaxNode statement) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(statement, Set.of("expr_stmt"))) {
      if (visit.ancestors().stream().skip(1).noneMatch(a -> SCOPES.contains(a.kind()))) {
        return visit.node();
      }
    }
    return null;
  }

  private static Set<String> classLevelNames(final SyntaxNode body) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(body, Set.of("expr_stmt", "funcdef", "classdef"))) {
      if (visit.ancestors().stream().anyMatch(a -> SCOPES.contains(a.kind()))) {
        continue;
      }
      final SyntaxNode node = visit.node();
      if (node.kind().equals("expr_stmt")) {
        final List<SyntaxNode> children = node.children();
        if (children.size() >= 3 && "=".equals(SyntaxWalk.text(children.get(1)))) {
          names.add(SyntaxWalk.text(children.get(0)));
        }
      } else {
        node.children().stream()
            .filter(child -> "name".equals(child.kind()) || "NAME".equals(child.kind()))
            .findFirst()
            .ifPresent(child -> names.add(SyntaxWalk.text(child)));
      }
    }
    return names;
  }

  private static boolean hasBases(final SyntaxNode classNode) {
    final String text = SyntaxWalk.text(classNode);
    final int open = text.indexOf('(');
    final int colon = text.indexOf(':');
    return open >= 0 && open < colon && !text.substring(open + 1, text.indexOf(')')).isEmpty();
  }

  private static void collectStrings(final SyntaxNode node, final List<SyntaxNode> strings) {
    if (node.children().isEmpty()) {
      if (STRING.matcher(SyntaxWalk.text(node)).matches()) {
        strings.add(node);
      }
      return;
    }
    node.children().forEach(child -> collectStrings(child, strings));
  }

  private static String unquote(final String text) {
    final Matcher matcher = STRING.matcher(text);
    return matcher.matches() ? matcher.group(2) : text;
  }
}
