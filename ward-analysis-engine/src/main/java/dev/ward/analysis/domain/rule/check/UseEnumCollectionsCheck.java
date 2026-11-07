package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.TypeNeed;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code HashMap} or {@code HashSet} created with an enum as its key type, which {@code
 * EnumMap} or {@code EnumSet} would hold more compactly. Only an explicit key type is read; a
 * diamond is not inferred.
 */
public final class UseEnumCollectionsCheck implements RuleCheck {

  private static final String CREATOR = "creator";
  private static final String TYPE = "typeType";
  private static final String IDENTIFIER = "identifier";
  private static final Set<String> HASH_COLLECTIONS = Set.of("HashMap", "HashSet");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the creation
   */
  public UseEnumCollectionsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public Set<TypeNeed> typeNeeds() {
    return Set.of(TypedChecks.SHAPES);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final dev.ward.analysis.domain.rule.FlowView flow,
      final dev.ward.analysis.domain.rule.ScopeView scopes,
      final dev.ward.analysis.domain.ir.SourceUnit unit,
      final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode creator : SyntaxWalk.descendants(root)) {
      if (CREATOR.equals(creator.kind())
          && HASH_COLLECTIONS.contains(createdName(creator))
          && keyIsEnum(creator, types)) {
        matches.add(CheckSupport.at(creator.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static String createdName(final SyntaxNode creator) {
    return SyntaxWalk.descendants(creator).stream()
        .filter(node -> IDENTIFIER.equals(node.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static boolean keyIsEnum(final SyntaxNode creator, final UnitTypeFacts types) {
    return SyntaxWalk.descendants(creator).stream()
        .filter(node -> TYPE.equals(node.kind()))
        .findFirst()
        .filter(key -> TypedChecks.isEnum(key, types))
        .isPresent();
  }
}
