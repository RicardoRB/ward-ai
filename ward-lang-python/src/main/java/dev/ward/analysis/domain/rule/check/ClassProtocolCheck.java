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
 * Reports a class of this module used in a way its body does not allow, like pylint {@code
 * not-context-manager}, {@code not-async-context-manager}, {@code non-iterator-returned}, {@code
 * invalid-enum-extension} and {@code invalid-metaclass}. A base or metaclass that is not a class of
 * this module is unknown, and nothing is reported for it.
 */
public final class ClassProtocolCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** A {@code with} on a class that has no {@code __enter__} and {@code __exit__}. */
    NOT_CONTEXT_MANAGER,
    /** An {@code async with} on a class that has no {@code __aenter__} and {@code __aexit__}. */
    NOT_ASYNC_CONTEXT_MANAGER,
    /** An {@code __iter__} that returns {@code self} from a class with no {@code __next__}. */
    NON_ITERATOR_RETURNED,
    /** A class that derives from an enum of this module that has members. */
    INVALID_ENUM_EXTENSION,
    /** A {@code metaclass=} that is not a class deriving from {@code type}. */
    INVALID_METACLASS
  }

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> ASSIGNMENT_KINDS = Set.of("expr_stmt", "annassign");
  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> WITHS = Set.of("with_stmt", "async_stmt");
  private static final Pattern CLASS_NAME = Pattern.compile("^(?:async\\s*)?class\\s*(\\w+)");
  private static final Pattern BASES =
      Pattern.compile("^(?:async\\s*)?class\\s*\\w+\\s*\\(([^)]*)\\)");
  private static final Pattern WITH_CALL = Pattern.compile("^with\\s*(\\w+)\\(");
  private static final Pattern ASYNC_WITH_CALL = Pattern.compile("^async\\s*with\\s*(\\w+)\\(");
  private static final Pattern METACLASS = Pattern.compile("metaclass=(\\w+)");
  private static final Pattern ASSIGNMENT = Pattern.compile("^([A-Za-z_]\\w*)(?::[^=]*)?=(?!=)");
  private static final Pattern DEF_NAME = Pattern.compile("def\\s*(\\w+)\\s*\\(");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public ClassProtocolCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** What the check needs to know about one class of the module. */
  private record Info(
      String name, String text, List<String> bases, Set<String> members, SyntaxNode node) {}

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, Info> classes = classes(root);
    final List<RuleMatch> found = new ArrayList<>();
    switch (problem) {
      case NOT_CONTEXT_MANAGER ->
          withCalls(root, WITH_CALL, "__enter__", "__exit__", classes, found);
      case NOT_ASYNC_CONTEXT_MANAGER ->
          withCalls(root, ASYNC_WITH_CALL, "__aenter__", "__aexit__", classes, found);
      case NON_ITERATOR_RETURNED -> nonIterators(root, classes, found);
      case INVALID_ENUM_EXTENSION -> enumExtensions(root, classes, found);
      case INVALID_METACLASS -> invalidMetaclasses(root, classes, found);
    }
    return List.copyOf(found);
  }

  private static Map<String, Info> classes(final SyntaxNode root) {
    final Map<String, Info> classes = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final String text = SyntaxWalk.text(visit.node());
      final Matcher name = CLASS_NAME.matcher(text);
      if (name.find()) {
        classes.put(
            name.group(1), new Info(name.group(1), text, bases(text), members(text), visit.node()));
      }
    }
    return classes;
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

  private static Set<String> members(final String classText) {
    final Set<String> members = new java.util.HashSet<>();
    final Matcher def = DEF_NAME.matcher(classText);
    while (def.find()) {
      members.add(def.group(1));
    }
    return Set.copyOf(members);
  }

  private void withCalls(
      final SyntaxNode root,
      final Pattern call,
      final String enter,
      final String exit,
      final Map<String, Info> classes,
      final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, WITHS)) {
      final Matcher named = call.matcher(SyntaxWalk.text(visit.node()));
      final Info info = named.find() ? classes.get(named.group(1)) : null;
      if (info != null
          && info.bases().isEmpty()
          && !(info.members().contains(enter) && info.members().contains(exit))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
  }

  private void nonIterators(
      final SyntaxNode root, final Map<String, Info> classes, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final String text = SyntaxWalk.text(visit.node());
      final Info info = classes.get(enclosingClass(visit.ancestors()));
      if (text.startsWith("def__iter__")
          && info != null
          && !info.members().contains("__next__")
          && returnsSelf(text)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
  }

  private static String enclosingClass(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CLASSES.contains(ancestors.get(index).kind())) {
        return classNameOf(SyntaxWalk.text(ancestors.get(index)));
      }
    }
    return "";
  }

  private static boolean returnsSelf(final String functionText) {
    return functionText.contains("returnself") && !functionText.contains("returnself.");
  }

  private void enumExtensions(
      final SyntaxNode root, final Map<String, Info> classes, final List<RuleMatch> found) {
    final Set<String> assigners = classesWithAssignments(root, classes.keySet());
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final Info info = classes.get(classNameOf(SyntaxWalk.text(visit.node())));
      if (info != null
          && info.bases().stream().anyMatch(base -> enumWithMembers(base, classes, assigners))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
  }

  private static boolean enumWithMembers(
      final String base, final Map<String, Info> classes, final Set<String> assigners) {
    final Info parent = classes.get(base);
    return parent != null
        && parent.bases().stream().anyMatch(name -> name.endsWith("Enum"))
        && assigners.contains(base);
  }

  private static Set<String> classesWithAssignments(
      final SyntaxNode root, final Set<String> names) {
    final Set<String> with = new java.util.HashSet<>();
    for (final String name : names) {
      if (!classAssignments(root, name).isEmpty()) {
        with.add(name);
      }
    }
    return Set.copyOf(with);
  }

  /** The names assigned in the class body of {@code className}, found by their statement scope. */
  static Set<String> classAssignments(final SyntaxNode root, final String className) {
    final Set<String> names = new java.util.HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, ASSIGNMENT_KINDS)) {
      if (className.equals(nearestScopeClass(visit.ancestors()))) {
        final Matcher assigned = ASSIGNMENT.matcher(SyntaxWalk.text(visit.node()));
        if (assigned.find()) {
          names.add(assigned.group(1));
        }
      }
    }
    return Set.copyOf(names);
  }

  /** The class that directly contains a statement, or "" when a function does. */
  static String nearestScopeClass(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if ("classdef".equals(kind)) {
        return classNameOf(SyntaxWalk.text(ancestors.get(index)));
      }
      if ("funcdef".equals(kind) || "lambdef".equals(kind)) {
        return "";
      }
    }
    return "";
  }

  private void invalidMetaclasses(
      final SyntaxNode root, final Map<String, Info> classes, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CLASSES)) {
      final Matcher meta = METACLASS.matcher(SyntaxWalk.text(visit.node()));
      if (meta.find() && invalidMetaclass(meta.group(1), classes)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
  }

  private static boolean invalidMetaclass(final String name, final Map<String, Info> classes) {
    if (name.matches("\\d.*|None|True|False")) {
      return true;
    }
    final Info info = classes.get(name);
    return info != null && info.bases().isEmpty();
  }

  private static String classNameOf(final String classText) {
    final Matcher name = CLASS_NAME.matcher(classText);
    return name.find() ? name.group(1) : "";
  }
}
