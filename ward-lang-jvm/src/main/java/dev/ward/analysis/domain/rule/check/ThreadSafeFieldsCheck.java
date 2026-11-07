package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a field of a class annotated {@code @ThreadSafe} that can change without a guard: it is
 * neither final, nor volatile, nor annotated {@code @GuardedBy}.
 */
public final class ThreadSafeFieldsCheck implements RuleCheck {

  private static final Set<String> TYPES =
      Set.of("classDeclaration", "enumDeclaration", "recordDeclaration");
  private static final String THREAD_SAFE = "ThreadSafe";
  private static final String GUARDED_BY = "GuardedBy";
  private static final Set<String> SAFE_MODIFIERS = Set.of("final", "volatile");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field
   */
  public ThreadSafeFieldsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TYPES)) {
      if (isThreadSafe(visit)) {
        ClassMembers.fieldMembers(visit.node()).stream()
            .filter(ThreadSafeFieldsCheck::unguarded)
            .forEach(field -> matches.add(CheckSupport.at(field.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isThreadSafe(final SyntaxWalk.Visit visit) {
    return visit.ancestors().stream()
        .reduce((first, second) -> second)
        .map(AnnotationTexts::of)
        .orElse(List.of())
        .stream()
        .anyMatch(text -> THREAD_SAFE.equals(AnnotationTexts.simpleName(text)));
  }

  private static boolean unguarded(final SyntaxNode field) {
    final List<String> modifiers = MemberDeclarations.modifiers(field);
    return modifiers.stream().noneMatch(SAFE_MODIFIERS::contains)
        && AnnotationTexts.of(field).stream()
            .noneMatch(text -> GUARDED_BY.equals(AnnotationTexts.simpleName(text)));
  }
}
