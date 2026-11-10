package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The classes of this module: their bases, members, and class-level assignments. */
final class ClassIndex {

  private static final Pattern BASES =
      Pattern.compile("^(?:async\\s*)?class\\s*\\w+\\s*\\(([^)]*)\\)");

  private static final Set<String> CLASSES = Set.of("classdef");
  private static final Set<String> ASSIGNMENT_KINDS = Set.of("expr_stmt", "annassign");
  private static final Pattern CLASS_NAME = Pattern.compile("^(?:async\\s*)?class\\s*(\\w+)");
  private static final Pattern ASSIGNMENT = Pattern.compile("^([A-Za-z_]\\w*)(?::[^=]*)?=(?!=)");
  private static final Pattern DEF_NAME = Pattern.compile("def\\s*(\\w+)\\s*\\(");

  private ClassIndex() {}

  /** What the check needs to know about one class of the module. */
  record Info(String name, String text, List<String> bases, Set<String> members, SyntaxNode node) {}

  static Map<String, Info> classes(final SyntaxNode root) {
    return SyntaxWalk.visits(root, CLASSES).stream()
        .map(visit -> info(visit.node()))
        .flatMap(Optional::stream)
        .collect(
            Collectors.toMap(Info::name, info -> info, (first, second) -> first, HashMap::new));
  }

  private static Optional<Info> info(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    final Matcher name = CLASS_NAME.matcher(text);
    return name.find()
        ? Optional.of(new Info(name.group(1), text, bases(text), members(text), node))
        : Optional.empty();
  }

  static List<String> bases(final String classText) {
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

  static Set<String> members(final String classText) {
    final Set<String> members = new java.util.HashSet<>();
    final Matcher def = DEF_NAME.matcher(classText);
    while (def.find()) {
      members.add(def.group(1));
    }
    return Set.copyOf(members);
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
      if (SyntaxKinds.CLASSDEF.equals(kind)) {
        return classNameOf(SyntaxWalk.text(ancestors.get(index)));
      }
      if ("funcdef".equals(kind) || "lambdef".equals(kind)) {
        return "";
      }
    }
    return "";
  }

  static String classNameOf(final String classText) {
    final Matcher name = CLASS_NAME.matcher(classText);
    return name.find() ? name.group(1) : "";
  }

  static String enclosingClass(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CLASSES.contains(ancestors.get(index).kind())) {
        return classNameOf(SyntaxWalk.text(ancestors.get(index)));
      }
    }
    return "";
  }

  static Set<String> classesWithAssignments(final SyntaxNode root, final Set<String> names) {
    final Set<String> with = new java.util.HashSet<>();
    for (final String name : names) {
      if (!classAssignments(root, name).isEmpty()) {
        with.add(name);
      }
    }
    return Set.copyOf(with);
  }
}
