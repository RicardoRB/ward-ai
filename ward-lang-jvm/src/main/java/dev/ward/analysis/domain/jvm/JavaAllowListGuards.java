package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the guards {@code if (!ALLOWED.contains(value)) { ...; throw or return }}, where {@code
 * ALLOWED} is a final collection field with an initializer. Past such a guard {@code value} is one
 * of the constants of the collection, so the lowering gives it a clean value there.
 *
 * <p>shortcut: only the negated {@code contains} form with a bare field name and a bare local
 * variable, and a then-branch that ends in {@code throw} or {@code return}; upgrade when a project
 * needs {@code containsKey} or a positive test with an else branch.
 */
final class JavaAllowListGuards {
  /** The name of the site that marks a guard, for the callers of the method that holds it. */
  static final String NAME = "<allow-listed>";

  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String STATEMENT = "statement";
  private static final Set<String> EXITS = Set.of("throw", "return");
  private static final Pattern COLLECTION =
      Pattern.compile(
          "^(?:Set|List|Collection|SortedSet|NavigableSet|EnumSet|HashSet|LinkedHashSet|TreeSet"
              + "|ArrayList|ImmutableSet|ImmutableList)(?:<.*>)?$"
      );
  private static final Pattern GUARD =
      Pattern.compile("^!([A-Za-z_$][\\w$]*)\\.contains\\(([A-Za-z_$][\\w$]*)\\)$");
  private static final int COLLECTION_GROUP = 1;
  private static final int VALUE_GROUP = 2;

  private final Set<String> allowLists = new HashSet<>();

  /**
   * Creates the finder of one body.
   *
   * @param fields the fields of the classes around the body
   */
  JavaAllowListGuards(final List<JavaClassFields.Field> fields) {
    for (final JavaClassFields.Field field : fields) {
      if (field.isFinal()
          && field.initializer() != null
          && COLLECTION.matcher(field.type()).matches()) {
        allowLists.add(field.name());
      }
    }
  }

  /**
   * Returns the variable that an {@code if} statement without an else branch leaves clean on the
   * path past it.
   *
   * @param condition the condition of the statement
   * @param then the statement of the then-branch
   * @param local tells whether a name is a local variable or a parameter of the body
   * @return the name of the guarded variable, if the statement is an allow-list guard
   */
  Optional<String> guarded(
      final SyntaxNode condition,
      final SyntaxNode then,
      final Predicate<String> local
  ) {
    final Matcher guard = GUARD.matcher(JavaFlowSyntax.text(JavaFlowSyntax.unwrap(condition)));
    if (!guard.matches()
        || !allowLists.contains(guard.group(COLLECTION_GROUP))
        || local.test(guard.group(COLLECTION_GROUP))
        || !local.test(guard.group(VALUE_GROUP))
        || !exits(then)) {
      return Optional.empty();
    }
    return Optional.of(guard.group(VALUE_GROUP));
  }

  /**
   * Records the guard of {@code name}: a clean write of it and a site that marks the guard, so a
   * caller of the method can rely on it.
   *
   * @param flow the builder of the body
   * @param clean the clean write and read of the variable, empty when it is not a local
   * @param name the guarded variable
   * @param span the statement of the guard
   */
  static void record(
      final ControlFlowBuilder flow,
      final List<VariableAccess> clean,
      final String name,
      final Span span
  ) {
    final List<FlowSite> mark =
        clean.isEmpty()
            ? List.of()
            : List.of(
                new FlowSite(
                    FlowSiteKind.CALL,
                    NAME,
                    name,
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    span
                )
            );
    flow.record(clean, mark);
  }

  /** A statement exits when it is a throw or return, or a block whose last statement does. */
  private static boolean exits(final SyntaxNode statement) {
    final SyntaxNode first = JavaFlowSyntax.parts(statement).get(0);
    if (!BLOCK.equals(first.kind())) {
      return EXITS.contains(JavaFlowSyntax.text(first));
    }
    final List<SyntaxNode> statements =
        JavaFlowSyntax.allOf(JavaFlowSyntax.parts(first), BLOCK_STATEMENT);
    if (statements.isEmpty()) {
      return false;
    }
    final SyntaxNode last = JavaFlowSyntax.parts(statements.get(statements.size() - 1)).get(0);
    return STATEMENT.equals(last.kind()) && exits(last);
  }
}
