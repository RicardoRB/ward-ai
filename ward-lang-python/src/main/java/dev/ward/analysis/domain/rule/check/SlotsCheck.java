package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
  private static final int ASSIGNMENT_PARTS = 3;
  private static final Pattern SELF_TARGET = Pattern.compile("^self\\.(\\w+)$");
  private static final Pattern NAME = Pattern.compile("\\w+");
  private static final String MESSAGE_NAME = "{value}";

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
        classNode.children().stream().filter(SlotsSyntax::isBlockNode).findFirst().orElse(null);
    if (body == null) {
      return List.of();
    }
    final SyntaxNode slotsValue = SlotsSyntax.slotsValue(body);
    if (slotsValue == null) {
      return List.of();
    }
    final List<SyntaxNode> slotLeaves = SlotsSyntax.stringLeaves(slotsValue);
    if (slotLeaves.isEmpty() && !SyntaxWalk.text(slotsValue).startsWith("(")) {
      return List.of();
    }
    final Set<String> slots = new HashSet<>();
    slotLeaves.forEach(leaf -> slots.add(SlotsSyntax.unquote(SyntaxWalk.text(leaf))));
    return switch (problem) {
      case CLASS_VARIABLE_CONFLICT -> conflicts(body, slotLeaves);
      case DECLARE_NON_SLOT -> declares(body, slots, classNode);
      case ASSIGNING_NON_SLOT -> assignments(body, slots, classNode);
    };
  }

  private List<RuleMatch> conflicts(final SyntaxNode body, final List<SyntaxNode> slotLeaves) {
    final Set<String> classLevel = SlotsSyntax.classLevelNames(body);
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode leaf : slotLeaves) {
      final String name = SlotsSyntax.unquote(SyntaxWalk.text(leaf));
      if (classLevel.contains(name)) {
        found.add(new RuleMatch(leaf.span(), message.replace(MESSAGE_NAME, name)));
      }
    }
    return found;
  }

  private List<RuleMatch> declares(
      final SyntaxNode body, final Set<String> slots, final SyntaxNode classNode) {
    final List<RuleMatch> found = new ArrayList<>();
    if (slots.contains(SlotsSyntax.DICT) || SlotsSyntax.hasBases(classNode)) {
      return found;
    }
    for (final SyntaxNode statement : SlotsSyntax.statements(body)) {
      final Optional<RuleMatch> declared = declaredNonSlot(statement, slots);
      declared.ifPresent(found::add);
    }
    return found;
  }

  /** An annotated class attribute without a value that is not a slot or a ClassVar. */
  private Optional<RuleMatch> declaredNonSlot(final SyntaxNode statement, final Set<String> slots) {
    final SyntaxNode simple = SlotsSyntax.unwrap(statement);
    if (simple == null || simple.children().size() != 2) {
      return Optional.empty();
    }
    final String annotation = SyntaxWalk.text(simple.children().get(1));
    final String name = SyntaxWalk.text(simple.children().get(0));
    if (annotation.startsWith(":")
        && !annotation.contains("ClassVar")
        && !slots.contains(name)
        && NAME.matcher(name).matches()) {
      return Optional.of(
          new RuleMatch(simple.children().get(0).span(), message.replace(MESSAGE_NAME, name)));
    }
    return Optional.empty();
  }

  private List<RuleMatch> assignments(
      final SyntaxNode body, final Set<String> slots, final SyntaxNode classNode) {
    final List<RuleMatch> found = new ArrayList<>();
    if (slots.contains(SlotsSyntax.DICT) || SlotsSyntax.hasBases(classNode)) {
      return found;
    }
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(body, Set.of("expr_stmt"))) {
      if (!directlyInMethod(visit.ancestors())) {
        continue;
      }
      final List<SyntaxNode> children = visit.node().children();
      if (children.size() < ASSIGNMENT_PARTS || !"=".equals(SyntaxWalk.text(children.get(1)))) {
        continue;
      }
      final Matcher matcher = SELF_TARGET.matcher(SyntaxWalk.text(children.get(0)));
      if (matcher.matches() && !slots.contains(matcher.group(1))) {
        found.add(
            new RuleMatch(visit.node().span(), message.replace(MESSAGE_NAME, matcher.group(1))));
      }
    }
    return found;
  }

  /** Whether the nearest enclosing scope is a method, and the only scope around it. */
  private static boolean directlyInMethod(final List<SyntaxNode> ancestors) {
    final List<SyntaxNode> scopes =
        ancestors.stream().filter(a -> SlotsSyntax.SCOPES.contains(a.kind())).toList();
    return scopes.size() == 1 && "funcdef".equals(scopes.get(0).kind());
  }
}
