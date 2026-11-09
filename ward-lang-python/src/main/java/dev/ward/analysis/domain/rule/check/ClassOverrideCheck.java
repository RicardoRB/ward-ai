package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
  private static final Pattern BASES =
      Pattern.compile("^(?:async\\s*)?class\\s*\\w+\\s*\\(([^)]*)\\)");
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

  /** The member kinds of a class: plain methods, properties, final methods, and attributes. */
  private record Members(
      Map<String, Boolean> methods, Set<String> finalMethods, Set<String> attributes) {}

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, List<String>> bases = new HashMap<>();
    final Map<String, Members> members = new HashMap<>();
    final Map<String, SyntaxNode> nodes = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final String text = SyntaxWalk.text(visit.node());
      final String name = first(CLASS_NAME, text);
      bases.put(name, bases(text));
      members.put(name, members(root, name));
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
      final Map<String, Members> members) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String owner = owner(visit.ancestors());
      final String name = first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
      if (owner.isEmpty() || name.isEmpty()) {
        continue;
      }
      final boolean property = property(visit.ancestors());
      for (final String base : bases.getOrDefault(owner, List.of())) {
        final Members parent = members.get(base);
        if (parent != null
            && parent.methods().containsKey(name)
            && parent.methods().get(name) != property) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> hidden(final SyntaxNode root, final Map<String, Members> members) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String owner = owner(visit.ancestors());
      final String name = first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
      final Members own = members.get(owner);
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
      final String owner = enclosingClass(visit.ancestors());
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
      final Map<String, Members> members) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String owner = owner(visit.ancestors());
      final String name = first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
      for (final String base : bases.getOrDefault(owner, List.of())) {
        final Members parent = members.get(base);
        if (parent != null && parent.finalMethods().contains(name)) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The name of the class a method is a direct member of, or "" when it is not a method. */
  private static String owner(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if ("classdef".equals(kind)) {
        return first(CLASS_NAME, SyntaxWalk.text(ancestors.get(index)));
      }
      if ("funcdef".equals(kind)) {
        return "";
      }
    }
    return "";
  }

  /** The name of the nearest class above a node, through any number of functions. */
  private static String enclosingClass(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if ("classdef".equals(ancestors.get(index).kind())) {
        return first(CLASS_NAME, SyntaxWalk.text(ancestors.get(index)));
      }
    }
    return "";
  }

  private static boolean property(final List<SyntaxNode> ancestors) {
    return ancestors.size() >= 1
        && "decorated".equals(ancestors.get(ancestors.size() - 1).kind())
        && SyntaxWalk.text(ancestors.get(ancestors.size() - 1)).startsWith("@property");
  }

  private static Members members(final SyntaxNode root, final String name) {
    final Map<String, Boolean> methods = new HashMap<>();
    final Set<String> finals = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      if (name.equals(owner(visit.ancestors()))) {
        final String method = first(FUNCTION_NAME, SyntaxWalk.text(visit.node()));
        methods.put(method, property(visit.ancestors()));
        if (decoratedWith(visit.ancestors(), "final")) {
          finals.add(method);
        }
      }
    }
    return new Members(Map.copyOf(methods), Set.copyOf(finals), attributes(root, name));
  }

  private static boolean decoratedWith(final List<SyntaxNode> ancestors, final String decorator) {
    return ancestors.size() >= 1
        && "decorated".equals(ancestors.get(ancestors.size() - 1).kind())
        && SyntaxWalk.text(ancestors.get(ancestors.size() - 1)).startsWith("@" + decorator);
  }

  /** Class-level assignments and {@code self.x=} assignments of a class. */
  private static Set<String> attributes(final SyntaxNode root, final String name) {
    final Set<String> attributes = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classdef"))) {
      if (name.equals(first(CLASS_NAME, SyntaxWalk.text(visit.node())))) {
        final Matcher self =
            Pattern.compile("self\\.(\\w+)=(?!=)").matcher(SyntaxWalk.text(visit.node()));
        while (self.find()) {
          attributes.add(self.group(1));
        }
      }
    }
    attributes.addAll(ClassProtocolCheck.classAssignments(root, name));
    return Set.copyOf(attributes);
  }

  private static List<String> bases(final String classText) {
    final Matcher header = BASES.matcher(classText);
    if (!header.find()) {
      return List.of();
    }
    final List<String> bases = new ArrayList<>();
    for (final String base : header.group(1).split(",")) {
      if (!base.contains("=") && !base.isBlank()) {
        bases.add(base.strip());
      }
    }
    return List.copyOf(bases);
  }

  private static String first(final Pattern pattern, final String text) {
    final Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1) : "";
  }
}
