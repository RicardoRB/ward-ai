package dev.ward.application.init.node;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import java.util.ArrayList;
import java.util.List;

/**
 * TypeScript's gates on a Node package manager. Ward's TypeScript rules, selected in {@code
 * ward-rules.toml}, do the linting.
 *
 * @param framework the project's framework, whose checker replaces {@code tsc} as the type-check
 *     gate
 * @param testGate the project's test runner gate, which both presets enable
 */
record TypeScriptGates(Framework framework, String testGate)
    implements SourceLanguageGates<NodeTools> {
  private static final String TSC = "tsc";
  private static final String VUE_TSC = "vue-tsc";
  private static final String SVELTE_CHECK = "svelte-check";
  private static final String ASTRO_CHECK = "astro-check";
  private static final String NGC = "ngc";

  @Override
  public List<InitGateOption> analysisGates(final NodeTools node) {
    final List<InitGateOption> gates = new ArrayList<>();
    gates.add(typeCheck(node));
    gates.add(node.tests());
    node.coverage().ifPresent(gates::add);
    if (!mutationUnavailable()) {
      gates.add(node.stryker());
    }
    return List.copyOf(gates);
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    final List<String> gates = new ArrayList<>(List.of(typeCheckId(), testGate));
    if (!NodeTools.NG_TEST.equals(testGate)) {
      gates.add(SupportedQualityGates.COVERAGE);
    }
    if (preset == QualityPreset.STRICT && !mutationUnavailable()) {
      gates.add("stryker");
    }
    return List.copyOf(gates);
  }

  /** Angular's test builder has no working Stryker runner, so Stryker is not offered for it. */
  private boolean mutationUnavailable() {
    return framework == Framework.ANGULAR;
  }

  /** Returns the framework's own checker, or {@code tsc} when it has none. */
  private InitGateOption typeCheck(final NodeTools node) {
    return switch (framework) {
      case VUE ->
          node.option(
              VUE_TSC,
              "Type-checks Vue components and TypeScript with vue-tsc.",
              node.command("vue-tsc", "--build"));
      case SVELTE ->
          node.option(
              SVELTE_CHECK,
              "Type-checks Svelte components and TypeScript with svelte-check.",
              node.svelteCheckCommand());
      case ASTRO ->
          node.option(
              ASTRO_CHECK,
              "Type-checks Astro components and TypeScript with astro check.",
              node.command("astro", "check"));
      case ANGULAR ->
          node.option(
              NGC,
              "Type-checks Angular components and their templates with ngc.",
              node.command("ngc", "-p", "tsconfig.app.json", "--noEmit"));
      default ->
          node.option(
              TSC,
              "Type-checks the project with the TypeScript compiler.",
              node.command(TSC, "--noEmit", "--pretty", "false"));
    };
  }

  /** Returns the id of the type-check gate {@link #typeCheck} offers for this framework. */
  private String typeCheckId() {
    return switch (framework) {
      case VUE -> VUE_TSC;
      case SVELTE -> SVELTE_CHECK;
      case ASTRO -> ASTRO_CHECK;
      case ANGULAR -> NGC;
      default -> TSC;
    };
  }
}
