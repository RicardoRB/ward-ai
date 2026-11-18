package dev.ward.infrastructure.rule.catalog;

import java.util.Map;

/**
 * The pairs of rules of one preset that report the same place on purpose, each with its reason.
 * A pair is named {@code <id> + <id>}, ids sorted. The other duplicates were resolved by leaving
 * one rule out of the presets: see {@code presets/duplicates-dropped.txt}.
 */
final class KeptPairs {
  /** Pair name to the reason it stays. */
  static final Map<String, String> ALL =
      Map.ofEntries(
          Map.entry(
              "ward:java/hidden-field + ward:java/mf-method-masks-field",
              "the strict preset sets options on hidden-field; mf reports a correctness mask, hidden-field a style one"
          ),
          Map.entry(
              "ward:java/clone-method-must-be-public + ward:java/clone-method-return-type-must-match-class-name",
              "clone() has three separate contracts: visibility, return type and the super call"
          ),
          Map.entry(
              "ward:java/clone-method-must-be-public + ward:java/cn-idiom-no-super-call",
              "clone() has three separate contracts: visibility, return type and the super call"
          ),
          Map.entry(
              "ward:java/clone-method-must-be-public + ward:java/proper-clone-implementation",
              "clone() has three separate contracts: visibility, return type and the super call"
          ),
          Map.entry(
              "ward:java/clone-method-return-type-must-match-class-name + ward:java/cn-idiom-no-super-call",
              "clone() has three separate contracts: visibility, return type and the super call"
          ),
          Map.entry(
              "ward:java/cn-idiom-no-super-call + ward:java/proper-clone-implementation",
              "clone() has three separate contracts: visibility, return type and the super call"
          ),
          Map.entry(
              "ward:java/control-statement-braces + ward:java/ucf-useless-control-flow-next-line",
              "braces and useless control flow are different fixes"
          ),
          Map.entry(
              "ward:java/declaration-order + ward:java/public-member-in-non-public-type",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:java/dmi-random-used-only-once + ward:java/insecure-random",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:java/empty-finalizer + ward:java/fi-nullify-super",
              "finalizer contracts are separate: emptiness, visibility and the super call"
          ),
          Map.entry(
              "ward:java/empty-finalizer + ward:java/finalize-should-be-protected",
              "finalizer contracts are separate: emptiness, visibility and the super call"
          ),
          Map.entry(
              "ward:java/fi-nullify-super + ward:java/finalize-should-be-protected",
              "finalizer contracts are separate: emptiness, visibility and the super call"
          ),
          Map.entry(
              "ward:java/final-class + ward:java/missing-static-method-in-non-instantiatable-class",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:java/illegal-import + ward:java/unsupported-jdk-api-usage",
              KeptPairs.OTHER
          ),
          Map.entry("ward:java/insecure-crypto-iv + ward:java/static-iv", KeptPairs.OTHER),
          Map.entry(
              "ward:java/interface-member-implied-modifier + ward:java/static-or-default-interface-method",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:javascript/constructor-super + ward:javascript/no-unreachable-super",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:javascript/cookie-missing-http-only + ward:javascript/cookie-missing-same-site",
              "a cookie can lack each flag independently; each flag is its own fix"
          ),
          Map.entry(
              "ward:javascript/cookie-missing-http-only + ward:javascript/cookie-missing-secure",
              "a cookie can lack each flag independently; each flag is its own fix"
          ),
          Map.entry(
              "ward:javascript/javadoc-method + ward:javascript/require-empty-line-before-block-tag-group",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:javascript/no-noninteractive-element-interactions + ward:javascript/no-static-element-interactions",
              "each accessibility rule asks for a different change to the element"
          ),
          Map.entry(
              "ward:javascript/no-noninteractive-element-interactions + ward:javascript/use-key-with-click-events",
              "each accessibility rule asks for a different change to the element"
          ),
          Map.entry(
              "ward:javascript/no-sequences + ward:javascript/no-unused-expressions",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:javascript/no-static-element-interactions + ward:javascript/use-key-with-click-events",
              "each accessibility rule asks for a different change to the element"
          ),
          Map.entry(
              "ward:javascript/prefer-const + ward:javascript/variable-declaration-usage-distance",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:javascript/use-anchor-content + ward:javascript/use-valid-anchor",
              "each accessibility rule asks for a different change to the element"
          ),
          Map.entry(
              "ward:javascript/use-aria-props-for-role + ward:javascript/use-semantic-elements",
              "each accessibility rule asks for a different change to the element"
          ),
          Map.entry(
              "ward:kotlin/filename + ward:kotlin/invalid-package-declaration",
              KeptPairs.OTHER
          ),
          Map.entry("ward:kotlin/invalid-javadoc-position + ward:kotlin/kdoc", KeptPairs.OTHER),
          Map.entry("ward:python/bad-builtin + ward:python/unnecessary-map", KeptPairs.OTHER),
          Map.entry(
              "ward:python/consider-using-with + ward:python/unspecified-encoding",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:python/global-statement + ward:python/global-variable-not-assigned",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:python/missing-class-docstring + ward:python/too-few-public-methods",
              "a missing docstring and a class with few methods are different problems"
          ),
          Map.entry(
              "ward:python/parameter-count + ward:python/too-many-positional-arguments",
              KeptPairs.OTHER
          ),
          Map.entry("ward:python/path-injection + ward:python/path-traversal", KeptPairs.OTHER),
          Map.entry(
              "ward:python/subprocess-run-check + ward:python/subprocess-without-shell-equals-true",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:python/undefined-local + ward:python/used-before-assignment",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:python/useless-comparison + ward:python/useless-expression",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:typescript/constructor-super + ward:typescript/no-unreachable-super",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:typescript/cookie-missing-http-only + ward:typescript/cookie-missing-same-site",
              "a cookie can lack each flag independently; each flag is its own fix"
          ),
          Map.entry(
              "ward:typescript/cookie-missing-http-only + ward:typescript/cookie-missing-secure",
              "a cookie can lack each flag independently; each flag is its own fix"
          ),
          Map.entry(
              "ward:typescript/javadoc-method + ward:typescript/require-empty-line-before-block-tag-group",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:typescript/no-implicit-any-let + ward:typescript/no-unassigned-vars",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:typescript/no-sequences + ward:typescript/no-unused-expressions",
              KeptPairs.OTHER
          ),
          Map.entry(
              "ward:typescript/prefer-const + ward:typescript/variable-declaration-usage-distance",
              KeptPairs.OTHER
          )
      );

  private static final String OTHER =
      "the two rules report different problems that happen to share a place";

  private KeptPairs() {}
}
