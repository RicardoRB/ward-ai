package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a member of a class of this module that conflicts with the class it derives from, like
 * pylint {@code invalid-overridden-method}, {@code method-hidden}, {@code non-parent-init-called}
 * and {@code overridden-final-method}. Bases that are not classes of this module are unknown.
 */
public final class ClassOverrideCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** A method overrides a property of a base class, or a property overrides a method. */
    INVALID_OVERRIDDEN_METHOD,
    /** A method is hidden by an attribute of the same name in the class. */
    METHOD_HIDDEN,
    /** An {@code __init__} calls the {@code __init__} of a class that is not a base. */
    NON_PARENT_INIT_CALLED,
    /** A method overrides a base method decorated with {@code final}. */
    OVERRIDDEN_FINAL_METHOD
  }

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern CLASS_NAME = Pattern.compile("^(?:async\\s*)?class\\s*(\\w+)");
  private static final Pattern FUNCTION_NAME = Pattern.compile("^def\\s*(\\w+)");
  private static final Pattern INIT_CALL = Pattern.compile("^(\\w+)\\.__init__\\(self");
  private static final String NON_PARENT_EXEMPT = "object";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public ClassOverrideCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, List<String>> bases = new HashMap<>();
    final Map<String, ClassMembers.Members> members = new HashMap<>();
    final Map<String, SyntaxNode> nodes = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final String text = SyntaxWalk.text(visit.node());
      final String name = ClassMembers.first(CLASS_NAME, text);
      bases.put(name, ClassMembers.bases(text));
      members.put(name, ClassMembers.members(root, name));
      nodes.put(name, visit.node());
    }
    return switch (problem) {
      case INVALID_OVERRIDDEN_METHOD -> overrides(root, bases, members);
      case METHOD_HIDDEN -> hidden(root, members);
      case NON_PARENT_INIT_CALLED -> parentInit(root, bases);
      case OVERRIDDEN_FINAL_METHOD -> finals(root, bases, members);
    };
  }

  private List<RuleMatch> overrides(
      final SyntaxNode root,
      final Map<String, List<String>> bases,
      final Map<String, ClassMembers.Members> members) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String owner = ClassMembers.owner(visit.ancestors());
      final String name = ClassMembers.first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
      if (owner.isEmpty() || name.isEmpty()) {
        continue;
      }
      final boolean property = ClassMembers.property(visit.ancestors());
      for (final String base : bases.getOrDefault(owner, List.of())) {
        final ClassMembers.Members parent = members.get(base);
        if (parent != null
            && parent.methods().containsKey(name)
            && parent.methods().get(name) != property) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> hidden(
      final SyntaxNode root, final Map<String, ClassMembers.Members> members) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String owner = ClassMembers.owner(visit.ancestors());
      final String name = ClassMembers.first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
      final ClassMembers.Members own = members.get(owner);
      if (own != null && own.attributes().contains(name)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> parentInit(final SyntaxNode root, final Map<String, List<String>> bases) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final String text = SyntaxWalk.text(visit.node());
      final Matcher call = INIT_CALL.matcher(text);
      final String owner = ClassMembers.enclosingClass(visit.ancestors());
      if (call.find()
          && !owner.isEmpty()
          && !NON_PARENT_EXEMPT.equals(call.group(1))
          && !bases.getOrDefault(owner, List.of()).contains(call.group(1))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> finals(
      final SyntaxNode root,
      final Map<String, List<String>> bases,
      final Map<String, ClassMembers.Members> members) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String owner = ClassMembers.owner(visit.ancestors());
      final String name = ClassMembers.first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
      for (final String base : bases.getOrDefault(owner, List.of())) {
        final ClassMembers.Members parent = members.get(base);
        if (parent != null && parent.finalMethods().contains(name)) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }
}
