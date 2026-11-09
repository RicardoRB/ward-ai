package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code Sendable} conformance on an actor, or on a type isolated to the main actor,
 * where the isolation already makes the type safe to send, like SwiftLint's {@code
 * redundant_sendable}. The name of the type is reported.
 */
public final class SwiftRedundantSendableCheck implements RuleCheck {

  private static final Set<String> TYPES =
      Set.of("class_declaration", "struct_declaration", "enum_declaration", "actor_declaration");
  private static final String ACTOR = "actor_declaration";
  private static final String ACTOR_KEYWORD = "actor";
  private static final String BODY_SUFFIX = "body";
  private static final String INHERITANCE = "type_inheritance_clause";
  private static final String ATTRIBUTES = "attributes";
  private static final String NAME_SUFFIX = "_name";
  private static final String SENDABLE = "Sendable";
  private static final String MAIN_ACTOR = "MainActor";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftRedundantSendableCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final String kind : TYPES) {
      SwiftTrees.descendants(root, kind).forEach(node -> addIfRedundant(node, matches));
    }
    return List.copyOf(matches);
  }

  private void addIfRedundant(final SyntaxNode type, final List<RuleMatch> matches) {
    if (conformsToSendable(type) && (isActor(type) || isMainActor(type))) {
      nameOf(type).ifPresent(name -> matches.add(SwiftTrees.at(name, message)));
    }
  }

  private static boolean conformsToSendable(final SyntaxNode type) {
    return SwiftTrees.child(type, INHERITANCE)
        .map(clause -> SwiftTrees.hasToken(clause, SENDABLE))
        .orElse(false);
  }

  /** The grammar parses an actor as a class declaration whose keyword is {@code actor}. */
  private static boolean isActor(final SyntaxNode type) {
    return ACTOR.equals(type.kind())
        || type.children().stream()
            .filter(child -> !child.kind().endsWith(BODY_SUFFIX))
            .anyMatch(child -> SwiftTrees.hasToken(child, ACTOR_KEYWORD));
  }

  private static boolean isMainActor(final SyntaxNode type) {
    return SwiftTrees.child(type, ATTRIBUTES)
        .map(attributes -> SwiftTrees.compact(attributes).contains(MAIN_ACTOR))
        .orElse(false);
  }

  private static Optional<SyntaxNode> nameOf(final SyntaxNode type) {
    return type.children().stream().filter(child -> child.kind().endsWith(NAME_SUFFIX)).findFirst();
  }
}
