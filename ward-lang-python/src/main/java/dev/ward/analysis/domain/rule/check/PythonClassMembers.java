package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The members of this module's classes: methods, properties, final methods and attributes. */
final class PythonClassMembers {

  private static final Pattern BASES =
      Pattern.compile("^(?:async\\s*)?class\\s*\\w+\\s*\\(([^)]*)\\)");

  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Pattern CLASS_NAME = Pattern.compile("^(?:async\\s*)?class\\s*(\\w+)");
  private static final Pattern FUNCTION_NAME = Pattern.compile("^def\\s*(\\w+)");

  private PythonClassMembers() {}

  /** The member kinds of a class: plain methods, properties, final methods, and attributes. */
  record Members(Map<String, Boolean> methods, Set<String> finalMethods, Set<String> attributes) {}

  /** The name of the class a method is a direct member of, or "" when it is not a method. */
  static String owner(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if (SyntaxKinds.CLASSDEF.equals(kind)) {
        return first(CLASS_NAME, SyntaxWalk.text(ancestors.get(index)));
      }
      if (SyntaxKinds.FUNCDEF.equals(kind)) {
        return "";
      }
    }
    return "";
  }

  /** The name of the nearest class above a node, through any number of functions. */
  static String enclosingClass(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (SyntaxKinds.CLASSDEF.equals(ancestors.get(index).kind())) {
        return first(CLASS_NAME, SyntaxWalk.text(ancestors.get(index)));
      }
    }
    return "";
  }

  static boolean property(final List<SyntaxNode> ancestors) {
    return !ancestors.isEmpty()
        && "decorated".equals(ancestors.get(ancestors.size() - 1).kind())
        && SyntaxWalk.text(ancestors.get(ancestors.size() - 1)).startsWith("@property");
  }

  static Members members(final SyntaxNode root, final String name) {
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

  static boolean decoratedWith(final List<SyntaxNode> ancestors, final String decorator) {
    return !ancestors.isEmpty()
        && "decorated".equals(ancestors.get(ancestors.size() - 1).kind())
        && SyntaxWalk.text(ancestors.get(ancestors.size() - 1)).startsWith("@" + decorator);
  }

  /** Class-level assignments and {@code self.x=} assignments of a class. */
  static Set<String> attributes(final SyntaxNode root, final String name) {
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
    attributes.addAll(ClassIndex.classAssignments(root, name));
    return Set.copyOf(attributes);
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

  static String first(final Pattern pattern, final String text) {
    final Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1) : "";
  }
}
