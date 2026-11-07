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
  private static final Set<String> CONTAINERS = Set.of("classBody", "enumBodyDeclarations");
  private static final Set<String> NOT_ENTERED = Set.of("classBody", "block");
  private static final Set<String> LOCAL_OWNERS = Set.of("localVariableDeclaration");
  private static final Set<String> OPENERS = Set.of("(", "{", "=");
  private static final String STATIC = "static";
  private static final String VOID = "void";
  private static final String SETTER = "set";

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
      final SyntaxNode container = container(visit.ancestors());
      final boolean isStatic = staticContext(visit.ancestors());
      for (final SyntaxNode declarator : declarators(visit.node())) {
        fields.add(new Field(SyntaxWalk.text(firstLeaf(declarator)), isStatic, container));
      }
    }
    return fields;
  }

  private List<Candidate> candidates(final SyntaxNode root) {
    final List<Candidate> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("variableDeclarator", "formalParameter"))) {
      final List<SyntaxNode> ancestors = visit.ancestors();
      final boolean parameter = "formalParameter".equals(visit.node().kind());
      if (!parameter && !isLocal(ancestors)) {
        continue;
      }
      final SyntaxNode name =
          parameter
              ? SyntaxWalk.firstDescendant(visit.node(), "variableDeclaratorId", Set.of())
                  .map(HiddenFieldCheck::firstLeaf)
                  .orElse(visit.node())
              : firstLeaf(visit.node());
      found.add(
          new Candidate(
              name,
              parameter,
              staticContext(ancestors),
              Callables.nearest(ancestors).orElse(null),
              ancestors));
    }
    return found;
  }

  private boolean exempt(final Candidate candidate, final String name) {
    if (!candidate.parameter() || candidate.callable() == null) {
      return false;
    }
    if (ignoreConstructorParameter
        && "constructorDeclaration".equals(candidate.callable().kind())) {
      return true;
    }
    return ignoreSetter && isSetterOf(candidate.callable(), name);
  }

  private static boolean hidesField(
      final Candidate candidate, final String name, final List<Field> fields) {
    return fields.stream()
        .filter(field -> field.name().equals(name))
        .filter(field -> !candidate.staticContext() || field.isStatic())
        .anyMatch(field -> candidate.ancestors().contains(field.container()));
  }

  private static boolean isSetterOf(final SyntaxNode method, final String parameter) {
    if (!"methodDeclaration".equals(method.kind())) {
      return false;
    }
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(method);
    final int open = indexOf(leaves, "(");
    if (open < 1) {
      return false;
    }
    final String methodName = SyntaxWalk.text(leaves.get(open - 1));
    final boolean returnsVoid =
        leaves.subList(0, open - 1).stream().anyMatch(leaf -> VOID.equals(SyntaxWalk.text(leaf)));
    final String expected =
        SETTER + Character.toUpperCase(parameter.charAt(0)) + parameter.substring(1);
    return returnsVoid && methodName.equals(expected);
  }

  private static int indexOf(final List<SyntaxNode> leaves, final String text) {
    for (int index = 0; index < leaves.size(); index++) {
      if (text.equals(SyntaxWalk.text(leaves.get(index)))) {
        return index;
      }
    }
    return -1;
  }

  private static boolean isLocal(final List<SyntaxNode> ancestors) {
    return ancestors.stream().anyMatch(node -> LOCAL_OWNERS.contains(node.kind()));
  }

  private static SyntaxNode container(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CONTAINERS.contains(ancestors.get(index).kind())) {
        return ancestors.get(index);
      }
    }
    return null;
  }

  /** Whether the innermost class member around these ancestors is static. */
  private static boolean staticContext(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if ("classBodyDeclaration".equals(ancestors.get(index).kind())) {
        for (final SyntaxNode leaf : SyntaxWalk.leaves(ancestors.get(index))) {
          final String text = SyntaxWalk.text(leaf);
          if (OPENERS.contains(text)) {
            return false;
          }
          if (STATIC.equals(text)) {
            return true;
          }
        }
        return false;
      }
    }
    return false;
  }

  private static List<SyntaxNode> declarators(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if ("variableDeclarator".equals(child.kind())) {
        found.add(child);
      } else if (!NOT_ENTERED.contains(child.kind())) {
        found.addAll(declarators(child));
      }
    }
    return found;
  }

  private static SyntaxNode firstLeaf(final SyntaxNode node) {
    return SyntaxWalk.leaves(node).get(0);
  }
}
