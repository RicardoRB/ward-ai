package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a JSX element names React's {@code Fragment}, as Biome 2.5.15's {@code
 * noUselessFragments} does: {@code <Fragment>} is one when it is imported as Fragment from a React
 * module, or when nothing binds it and it is spelled Fragment; {@code <React.Fragment>} is one when
 * React is imported from a React module, or when nothing binds it and it is spelled React.
 */
final class ReactFragmentNames {

  private static final String OPENING = JsxAttributes.OPENING;
  private static final String NAME = "jsxElementName";
  private static final String KEY = "key";
  private static final String FRAGMENT_NAME = "Fragment";
  private static final String REACT_GLOBAL = "React";
  private static final String MEMBER = ".";
  private static final Set<String> REACT_MODULES =
      Set.of("react", "preact/compat", "preact/hooks", "@rbxts/react", "@rbxts-js/react");

  private ReactFragmentNames() {}

  /** The references keyed by where they start, so a name is found at its element's start. */
  static Map<Integer, NameReference> referencesByStart(final UnitScopes scopes) {
    final Map<Integer, NameReference> byStart = new HashMap<>();
    for (final NameReference reference : scopes.references()) {
      byStart.put(reference.span().startOffset(), reference);
    }
    return byStart;
  }

  /** Whether the element is React's Fragment and carries no key, which Biome keeps. */
  static boolean isFragmentElement(
      final SyntaxNode element, final Map<Integer, NameReference> byStart) {
    return openingName(element).filter(name -> namesReactFragment(name, byStart)).isPresent()
        && !hasKey(element);
  }

  private static Optional<SyntaxNode> openingName(final SyntaxNode element) {
    return element.children().stream()
        .filter(child -> OPENING.equals(child.kind()))
        .findFirst()
        .flatMap(
            opening ->
                opening.children().stream().filter(child -> NAME.equals(child.kind())).findFirst());
  }

  private static boolean namesReactFragment(
      final SyntaxNode name, final Map<Integer, NameReference> byStart) {
    final NameReference reference = byStart.get(name.span().startOffset());
    final Optional<Binding> binding = reference == null ? Optional.empty() : reference.resolved();
    final String text = SyntaxWalk.text(name);
    final int dot = text.indexOf(MEMBER);
    if (dot < 0) {
      return binding
          .map(ReactFragmentNames::isNamedReactFragment)
          .orElse(FRAGMENT_NAME.equals(text));
    }
    return FRAGMENT_NAME.equals(text.substring(dot + MEMBER.length()))
        && binding
            .map(ReactFragmentNames::isReactModule)
            .orElse(REACT_GLOBAL.equals(text.substring(0, dot)));
  }

  /** A named import whose imported name is Fragment, from a React module. */
  private static boolean isNamedReactFragment(final Binding binding) {
    return isReactModule(binding)
        && binding.origin().map(origin -> FRAGMENT_NAME.equals(origin.member())).orElse(false);
  }

  private static boolean isReactModule(final Binding binding) {
    return binding.kind() == BindingKind.IMPORT
        && binding.origin().map(origin -> REACT_MODULES.contains(origin.specifier())).orElse(false);
  }

  private static boolean hasKey(final SyntaxNode element) {
    return element.children().stream()
        .filter(child -> OPENING.equals(child.kind()))
        .flatMap(opening -> JsxAttributes.of(opening).stream())
        .anyMatch(attribute -> KEY.equals(JsxAttributes.name(attribute)));
  }
}
