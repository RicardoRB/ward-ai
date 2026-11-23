package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.ProjectCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports an async function whose body never awaits, like SwiftLint's {@code async_without_await},
 * unless the function witnesses an async requirement of a protocol that its type conforms to,
 * declared anywhere in the project, since then {@code async} cannot be dropped. A requirement of a
 * protocol outside the project is not known, so a witness of it is still reported.
 */
public final class SwiftAsyncWithoutAwaitCheck implements RuleCheck, ProjectCheck {
  private static final String FUNCTION = "function_declaration";
  private static final String REQUIREMENT = "protocol_method_declaration";
  private static final String PROTOCOL = "protocol_declaration";
  private static final String SIGNATURE = "function_signature";
  private static final String ASYNC = "async";
  private static final String AWAIT = "await";
  private static final String PARAMETER = "parameter";
  private static final String EXTERNAL = "external_parameter_name";
  private static final String LOCAL = "local_parameter_name";
  private static final String FUNCTION_NAME = "function_name";
  private static final Set<String> SCOPES_OF_OTHER_FUNCTIONS = Set.of(
      FUNCTION,
      "closure_expression"
  );

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftAsyncWithoutAwaitCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final Map<String, Set<String>> witnessed = witnessedKeys(trees);
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach((path, tree) -> scan(tree, null, witnessed, path, found));
    return List.copyOf(found);
  }

  /** The async requirement keys each type name must satisfy, through every conformance. */
  private static Map<String, Set<String>> witnessedKeys(final SortedMap<String, SyntaxNode> trees) {
    final Map<String, Set<String>> requirements = new HashMap<>();
    final Map<String, Set<String>> inherits = new HashMap<>();
    for (final SyntaxNode tree : trees.values()) {
      for (final String kind : SwiftInheritance.KINDS) {
        SwiftTrees.descendants(tree, kind).forEach(d -> record(d, requirements, inherits));
      }
    }
    final Map<String, Set<String>> witnessed = new HashMap<>();
    inherits.keySet().forEach(type -> witnessed.put(type, closure(type, requirements, inherits)));
    return witnessed;
  }

  private static void record(
      final SyntaxNode declaration,
      final Map<String,
      Set<String>> requirements,
      final Map<String,
      Set<String>> inherits
  ) {
    SwiftInheritance.nameOf(declaration)
        .ifPresent(
            name -> {
              inherits
                  .computeIfAbsent(name, key -> new HashSet<>())
                  .addAll(SwiftInheritance.inherited(declaration));
              if (PROTOCOL.equals(declaration.kind())) {
                SwiftTrees.descendants(declaration, REQUIREMENT).stream()
                    .filter(SwiftAsyncWithoutAwaitCheck::isAsync)
                    .forEach(
                        method ->
                            requirements.computeIfAbsent(name, key -> new HashSet<>()).add(
                                key(method)
                            )
                    );
              }
            }
        );
  }

  private static Set<String> closure(
      final String type,
      final Map<String,
      Set<String>> requirements,
      final Map<String,
      Set<String>> inherits
  ) {
    final Set<String> keys = new HashSet<>();
    final Set<String> seen = new HashSet<>();
    final List<String> pending = new ArrayList<>(List.of(type));
    while (!pending.isEmpty()) {
      final String next = pending.remove(pending.size() - 1);
      if (seen.add(next)) {
        keys.addAll(requirements.getOrDefault(next, Set.of()));
        pending.addAll(inherits.getOrDefault(next, Set.of()));
      }
    }
    return keys;
  }

  /** Walks the tree with the type whose member the node is, and none inside another function. */
  private void scan(
      final SyntaxNode node,
      final String type,
      final Map<String,
      Set<String>> witnessed,
      final String path,
      final List<ProjectMatch> found
  ) {
    if (FUNCTION.equals(node.kind()) && reportable(node, type, witnessed)) {
      found.add(new ProjectMatch(path, SwiftTrees.at(node, message)));
    }
    final String inner = innerType(node, type);
    node.children().forEach(child -> scan(child, inner, witnessed, path, found));
  }

  private static String innerType(final SyntaxNode node, final String type) {
    if (SCOPES_OF_OTHER_FUNCTIONS.contains(node.kind())) {
      return null;
    }
    return SwiftInheritance.KINDS.contains(node.kind())
        ? SwiftInheritance.nameOf(node).orElse(null)
        : type;
  }

  private static boolean reportable(
      final SyntaxNode function,
      final String type,
      final Map<String,
      Set<String>> witnessed
  ) {
    return isAsync(function)
        && !SwiftTrees.hasToken(function, AWAIT)
        && !witnessed.getOrDefault(type, Set.of()).contains(key(function));
  }

  private static boolean isAsync(final SyntaxNode function) {
    return SwiftTrees.child(function, SIGNATURE)
        .map(
            signature -> signature.children().stream().anyMatch(
                c -> ASYNC.equals(c.fields().get("text"))
            )
        )
        .orElse(false);
  }

  /** The function name and the argument labels, which is how Swift tells overloads apart. */
  private static String key(final SyntaxNode function) {
    final StringBuilder key =
        new StringBuilder(
            SwiftTrees.child(function, FUNCTION_NAME).map(SwiftTrees::compact).orElse("")
        );
    key.append('(');
    SwiftTrees.child(function, SIGNATURE)
        .ifPresent(
            signature ->
                SwiftTrees.descendants(signature, PARAMETER).forEach(
                    parameter -> key.append(label(parameter)).append(':')
                )
        );
    return key.append(')').toString();
  }

  private static String label(final SyntaxNode parameter) {
    return SwiftTrees.child(parameter, EXTERNAL)
        .or(() -> SwiftTrees.child(parameter, LOCAL))
        .map(SwiftTrees::compact)
        .orElse("");
  }
}
