package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The parameter lists of this module's top-level functions. */
final class FunctionSignatures {

  private static final String SLASH = "/";
  private static final String DEF = "def";

  private FunctionSignatures() {}

  /** A parameter: its name and whether it has a default. */
  record Param(String name, boolean hasDefault) {}

  /** The parameters of a top-level function. */
  record Signature(
      List<Param> positional,
      List<Param> keywordOnly,
      List<String> positionalOnly,
      boolean hasKwargs) {}

  static Map<String, Signature> topLevelFunctions(final SyntaxNode root) {
    final Map<String, Signature> functions = new HashMap<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      if (topLevel(visit.ancestors())) {
        functions.put(
            functionName(visit.node()), signature(SyntaxWalk.text(parameters(visit.node()))));
      }
    }
    return functions;
  }

  static boolean topLevel(final List<SyntaxNode> ancestors) {
    return ancestors.stream()
        .noneMatch(node -> "funcdef".equals(node.kind()) || "classdef".equals(node.kind()));
  }

  static SyntaxNode parameters(final SyntaxNode function) {
    for (final SyntaxNode child : function.children()) {
      if (SyntaxKinds.PARAMETERS.equals(child.kind())) {
        return child;
      }
    }
    return function;
  }

  static String functionName(final SyntaxNode function) {
    final List<SyntaxNode> children = function.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (DEF.equals(SyntaxWalk.text(children.get(index)))) {
        return SyntaxWalk.text(children.get(index + 1));
      }
    }
    return "";
  }

  /** Parses a parameter list "(a, b=1, /, c, *, d, e=2, **f)" into its parts. */
  static Signature signature(final String parameters) {
    final String inside = parameters.strip();
    final List<String> items =
        ArgumentList.split(inside.length() >= 2 ? inside.substring(1, inside.length() - 1) : "");
    final List<Param> positional = new ArrayList<>();
    final List<Param> keywordOnly = new ArrayList<>();
    final List<String> positionalOnly = new ArrayList<>();
    boolean afterStar = false;
    boolean kwargs = false;
    for (final String item : items) {
      if (SLASH.equals(item)) {
        positionalOnly.addAll(names(positional));
      } else if (item.startsWith("**")) {
        kwargs = true;
      } else if (item.startsWith("*")) {
        afterStar = true;
      } else {
        final Param param = param(item);
        if (afterStar) {
          keywordOnly.add(param);
        } else {
          positional.add(param);
        }
      }
    }
    return new Signature(
        List.copyOf(positional), List.copyOf(keywordOnly), List.copyOf(positionalOnly), kwargs);
  }

  static List<String> names(final List<Param> params) {
    return params.stream().map(Param::name).toList();
  }

  static Param param(final String item) {
    final Matcher name = Pattern.compile("^(\\w+)").matcher(item);
    final String identifier = name.find() ? name.group(1) : "";
    return new Param(identifier, item.contains("="));
  }
}
