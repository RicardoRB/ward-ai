package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reports a name that a scope declares more than once, like ESLint {@code no-redeclare}: each
 * declaration after the first is reported at its name. A parameter and a {@code var} of the same
 * name in one function count as two declarations. With {@code ignoreDeclarationMerge}, TypeScript
 * declarations that merge are not reported, as typescript-eslint does: interfaces, namespaces, a
 * class with interfaces or namespaces, a function with namespaces, and an enum with namespaces.
 */
public final class NodeRedeclareCheck implements RuleCheck {

  private static final int FIRST_DECLARATION = 1;
  private static final String BUILTIN_NAME = "{value}";
  private static final String CLASS = "classDeclaration";
  private static final String INTERFACE = "interfaceDeclaration";
  private static final String NAMESPACE = "namespaceDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final String ENUM = "enumDeclaration";
  private static final Set<String> CLASS_MERGE = Set.of(CLASS, INTERFACE, NAMESPACE);
  private static final Set<String> FUNCTION_MERGE = Set.of(FUNCTION, NAMESPACE);
  private static final Set<String> ENUM_MERGE = Set.of(ENUM, NAMESPACE);

  private final boolean builtinGlobals;
  private final boolean ignoreDeclarationMerge;
  private final Set<String> environments;
  private final String message;
  private final String builtinMessage;

  /**
   * Creates the check.
   *
   * @param builtinGlobals whether a global declaration of a built-in name is reported
   * @param ignoreDeclarationMerge whether TypeScript declarations that merge are not reported
   * @param environments the environments whose built-in names are defined, such as {@code browser}
   * @param message the message of each finding, with {@code {value}} as the name
   * @param builtinMessage the message of a declaration of a built-in name
   */
  public NodeRedeclareCheck(
      final boolean builtinGlobals,
      final boolean ignoreDeclarationMerge,
      final Set<String> environments,
      final String message,
      final String builtinMessage) {
    this.builtinGlobals = builtinGlobals;
    this.ignoreDeclarationMerge = ignoreDeclarationMerge;
    this.environments = Set.copyOf(environments);
    this.message = Objects.requireNonNull(message, "message");
    this.builtinMessage = Objects.requireNonNull(builtinMessage, "builtinMessage");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redeclare check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(environments);
    final Set<String> builtins = builtinNames(scopes);
    final boolean script = !SourceKind.isModule(root);
    final Map<Integer, String> kinds =
        ignoreDeclarationMerge ? DeclarationKinds.of(root) : Map.of();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (builtinGlobals
          && script
          && !environments.isEmpty()
          && scope.id() == UnitScopes.MODULE_ID) {
        builtinRedeclarations(scope.bindings(), builtins, kinds, matches);
      } else {
        redeclarations(scope.bindings(), kinds, matches);
      }
    }
    return List.copyOf(matches);
  }

  /** The names the built-in scope declares. */
  private static Set<String> builtinNames(final UnitScopes scopes) {
    final Set<String> names = new HashSet<>();
    for (final Scope scope : scopes.scopes()) {
      if (scope.id() == UnitScopes.BUILTIN_ID) {
        scope.bindings().forEach(binding -> names.add(binding.name()));
      }
    }
    return names;
  }

  /** A global declaration of a built-in name is reported at each of its declarations. */
  private void builtinRedeclarations(
      final List<Binding> bindings,
      final Set<String> builtins,
      final Map<Integer, String> kinds,
      final List<RuleMatch> matches) {
    for (final Binding binding : bindings) {
      if (builtins.contains(binding.name())) {
        matches.add(
            new RuleMatch(
                binding.declaration(), builtinMessage.replace(BUILTIN_NAME, binding.name())));
      }
    }
    redeclarations(
        bindings.stream().filter(binding -> !builtins.contains(binding.name())).toList(),
        kinds,
        matches);
  }

  /** The bindings of a scope grouped by name; each reported binding is a redeclaration. */
  private void redeclarations(
      final List<Binding> bindings,
      final Map<Integer, String> kinds,
      final List<RuleMatch> matches) {
    final Map<String, List<Binding>> byName =
        bindings.stream()
            .collect(Collectors.groupingBy(Binding::name, LinkedHashMap::new, Collectors.toList()));
    matches.addAll(
        byName.values().stream()
            .flatMap(group -> reportedOf(group, kinds).stream())
            .map(this::match)
            .toList());
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace("{value}", binding.name()));
  }

  /**
   * The declarations of one name that are reported: every one after the first, unless the
   * declarations merge, when the merge rules of typescript-eslint decide.
   */
  private List<Binding> reportedOf(final List<Binding> group, final Map<Integer, String> kinds) {
    if (!ignoreDeclarationMerge || group.size() <= FIRST_DECLARATION) {
      return group.subList(Math.min(FIRST_DECLARATION, group.size()), group.size());
    }
    final List<String> found =
        group.stream()
            .map(binding -> kinds.getOrDefault(binding.declaration().startOffset(), ""))
            .toList();
    if (allIn(found, CLASS_MERGE)) {
      return onlyWhenRepeated(group, found, CLASS);
    }
    if (allIn(found, FUNCTION_MERGE)) {
      return onlyWhenRepeated(group, found, FUNCTION);
    }
    if (allIn(found, ENUM_MERGE)) {
      return onlyWhenRepeated(group, found, ENUM);
    }
    return group.subList(FIRST_DECLARATION, group.size());
  }

  private static boolean allIn(final List<String> found, final Set<String> kinds) {
    return found.stream().allMatch(kinds::contains);
  }

  /** The declarations of {@code kind} when there are several; a single one merges safely. */
  private static List<Binding> onlyWhenRepeated(
      final List<Binding> group, final List<String> found, final String kind) {
    final List<Binding> same =
        IntStream.range(0, group.size())
            .filter(index -> kind.equals(found.get(index)))
            .mapToObj(group::get)
            .toList();
    return same.size() > FIRST_DECLARATION ? same : List.of();
  }
}
