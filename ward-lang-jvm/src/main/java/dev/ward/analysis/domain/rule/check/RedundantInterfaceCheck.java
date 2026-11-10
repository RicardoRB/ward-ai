package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an interface that a type lists although another listed interface, or its superclass,
 * already provides it, as PMD's UnnecessaryInterfaceDeclaration does. Only types declared in the
 * same file are followed.
 */
public final class RedundantInterfaceCheck implements RuleCheck {

  private static final String TYPE_LIST = "typeList";
  private static final String TYPE = "typeType";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the redundant interface
   */
  public RedundantInterfaceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxWalk.Visit> declarations = SyntaxWalk.visits(root, JavaNodes.TYPE_KINDS);
    final Map<String, Set<String>> supers = new HashMap<>();
    declarations.forEach(
        visit ->
            supers
                .computeIfAbsent(JavaNodes.name(visit.node()), key -> new HashSet<>())
                .addAll(direct(visit.node())));
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : declarations) {
      redundant(visit.node(), supers, matches);
    }
    return List.copyOf(matches);
  }

  private void redundant(
      final SyntaxNode declaration,
      final Map<String, Set<String>> supers,
      final List<RuleMatch> matches) {
    final List<SyntaxNode> listed = listed(declaration);
    final Set<String> provided = new HashSet<>();
    superclass(declaration).ifPresent(parent -> provided.addAll(closure(parent, supers)));
    for (final SyntaxNode other : listed) {
      provided.addAll(closure(simpleName(other), supers));
    }
    listed.stream()
        .filter(type -> provided.contains(simpleName(type)))
        .forEach(
            type ->
                matches.add(
                    CheckSupport.at(
                        type.span(), CheckSupport.fill(message, simpleName(type), ""))));
  }

  private static Set<String> direct(final SyntaxNode declaration) {
    final Set<String> names = new HashSet<>();
    superclass(declaration).ifPresent(names::add);
    listed(declaration).forEach(type -> names.add(simpleName(type)));
    return names;
  }

  private static Optional<String> superclass(final SyntaxNode declaration) {
    return "classDeclaration".equals(declaration.kind())
        ? JavaNodes.kid(declaration, TYPE).map(RedundantInterfaceCheck::simpleName)
        : Optional.empty();
  }

  private static List<SyntaxNode> listed(final SyntaxNode declaration) {
    final String marker =
        "classDeclaration".equals(declaration.kind()) ? "'implements'" : "'extends'";
    final List<SyntaxNode> children = declaration.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (marker.equals(children.get(index).kind())
          && TYPE_LIST.equals(children.get(index + 1).kind())) {
        return JavaNodes.kids(children.get(index + 1), TYPE);
      }
    }
    return List.of();
  }

  private static Set<String> closure(final String start, final Map<String, Set<String>> supers) {
    final Set<String> seen = new HashSet<>();
    final List<String> queue = new ArrayList<>(supers.getOrDefault(start, Set.of()));
    while (!queue.isEmpty()) {
      final String next = queue.remove(queue.size() - 1);
      if (seen.add(next)) {
        queue.addAll(supers.getOrDefault(next, Set.of()));
      }
    }
    return seen;
  }

  private static String simpleName(final SyntaxNode type) {
    final String text = SyntaxWalk.text(type);
    final int generic = text.indexOf('<');
    final String bare = generic < 0 ? text : text.substring(0, generic);
    return bare.substring(bare.lastIndexOf('.') + 1);
  }
}
