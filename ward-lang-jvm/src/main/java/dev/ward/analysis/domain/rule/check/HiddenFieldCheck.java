package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each local variable and method or constructor parameter that has the name of a field of
 * an enclosing class, as Checkstyle's HiddenField does. In a static context only static fields are
 * hidden. With the user options, constructor parameters or setter parameters are not reported.
 * Lambda parameters and catch parameters are not checked.
 */
public final class HiddenFieldCheck implements UnitCheck {

  // shortcut: lambda and catch parameters are not checked, Checkstyle's LAMBDA token is not
  // reproduced; upgrade when a fixture needs them.
  private static final Set<String> LOCAL_OWNERS = Set.of("localVariableDeclaration");
  private static final String PARAMETER = "formalParameter";
  private static final String CONSTRUCTOR = "constructorDeclaration";

  private final boolean ignoreConstructorParameter;
  private final boolean ignoreSetter;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoreConstructorParameter whether constructor parameters are not reported
   * @param ignoreSetter whether the parameter of a setter for its field is not reported
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public HiddenFieldCheck(
      final boolean ignoreConstructorParameter, final boolean ignoreSetter, final String message) {
    this.ignoreConstructorParameter = ignoreConstructorParameter;
    this.ignoreSetter = ignoreSetter;
    this.message = Objects.requireNonNull(message, "message");
  }

  /** A field and the class body that declares it. */
  private record Field(String name, boolean isStatic, SyntaxNode container) {}

  /** A local or parameter that may hide a field. */
  private record Candidate(
      SyntaxNode name,
      boolean parameter,
      boolean staticContext,
      SyntaxNode callable,
      List<SyntaxNode> ancestors) {}

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<Field> fields = fields(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final Candidate candidate : candidates(root)) {
      final String name = SyntaxWalk.text(candidate.name());
      if (exempt(candidate, name) || !hidesField(candidate, name, fields)) {
        continue;
      }
      matches.add(CheckSupport.at(candidate.name().span(), CheckSupport.fill(message, name, "")));
    }
    return List.copyOf(matches);
  }

  private List<Field> fields(final SyntaxNode root) {
    final List<Field> fields = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("fieldDeclaration"))) {
      final SyntaxNode container = FieldScopes.container(visit.ancestors());
      final boolean isStatic = FieldScopes.staticContext(visit.ancestors());
      for (final SyntaxNode declarator : FieldScopes.declarators(visit.node())) {
        fields.add(
            new Field(SyntaxWalk.text(FieldScopes.firstLeaf(declarator)), isStatic, container));
      }
    }
    return fields;
  }

  private List<Candidate> candidates(final SyntaxNode root) {
    final List<Candidate> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("variableDeclarator", PARAMETER))) {
      final List<SyntaxNode> ancestors = visit.ancestors();
      final boolean parameter = PARAMETER.equals(visit.node().kind());
      if (!parameter && !isLocal(ancestors)) {
        continue;
      }
      final SyntaxNode name =
          parameter
              ? SyntaxWalk.firstDescendant(visit.node(), "variableDeclaratorId", Set.of())
                  .map(FieldScopes::firstLeaf)
                  .orElse(visit.node())
              : FieldScopes.firstLeaf(visit.node());
      found.add(
          new Candidate(
              name,
              parameter,
              FieldScopes.staticContext(ancestors),
              Callables.nearest(ancestors).orElse(null),
              ancestors));
    }
    return found;
  }

  private boolean exempt(final Candidate candidate, final String name) {
    final SyntaxNode callable = candidate.callable();
    return candidate.parameter()
        && callable != null
        && (ignoreConstructorParameter && CONSTRUCTOR.equals(callable.kind())
            || ignoreSetter && FieldScopes.isSetterOf(callable, name));
  }

  private static boolean hidesField(
      final Candidate candidate, final String name, final List<Field> fields) {
    return fields.stream()
        .filter(field -> field.name().equals(name))
        .filter(field -> !candidate.staticContext() || field.isStatic())
        .anyMatch(field -> candidate.ancestors().contains(field.container()));
  }

  private static boolean isLocal(final List<SyntaxNode> ancestors) {
    return ancestors.stream().anyMatch(node -> LOCAL_OWNERS.contains(node.kind()));
  }
}
