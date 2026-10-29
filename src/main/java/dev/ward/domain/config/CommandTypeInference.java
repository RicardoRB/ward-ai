package dev.ward.domain.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Infers a check's tool type from its command when {@code uses} is absent. Each rule is one row of
 * the table below; a command gets a type only when exactly one rule matches it, otherwise it is a
 * plain {@code command} check. Types that no command can imply are listed in {@link #USES_ONLY}.
 */
public final class CommandTypeInference {

  /** The type of a command Ward does not recognize. */
  public static final String COMMAND = "command";

  /** Types that init writes with {@code uses}, because no command shape identifies them. */
  public static final Set<String> USES_ONLY =
      Set.of(
          "archunit",
          "openspec-archived",
          "openspec-junit-traceability",
          "prettier",
          "ng-test",
          "api-compat",
          "license",
          "zap",
          "openapi-lint",
          "openapi-diff",
          "openapi-compat",
          COMMAND);

  private static final String MAVEN = "mvn";
  private static final String MAVEN_WRAPPER = "mvnw";
  private static final String GRADLE = "gradle";
  private static final String GRADLE_WRAPPER = "gradlew";
  private static final String TEST_FILTER_MAVEN = "-Dtest";
  private static final String TEST_FILTER_GRADLE = "--tests";

  private static final List<Rule> RULES =
      List.of(
          new Rule(
              "format",
              anyOf(
                  sequence("spotless:check"),
                  sequence("spotlessCheck"),
                  sequence("prettier", "--check"),
                  sequence("biome", "format"))),
          new Rule("checkstyle", anyOf(sequence("checkstyle:check"), sequence("checkstyle*"))),
          new Rule("pmd", anyOf(sequence("pmd:check"), sequence("pmd*"))),
          new Rule("spotbugs", anyOf(sequence("spotbugs:check"), sequence("spotbugs*"))),
          new Rule(
              "coverage",
              anyOf(sequence("jacoco:check"), sequence("jacocoTestCoverageVerification"))),
          new Rule("mutation", anyOf(sequence("pitest:mutationCoverage"), sequence("pitest"))),
          new Rule("stryker", sequence("stryker", "run")),
          new Rule("tests", CommandTypeInference::isUnfilteredBuildTest),
          new Rule("compile", buildWrapper(sequence("compile"))),
          new Rule("sonar", anyOf(sequence("sonar:sonar"), sequence("sonarqube"))),
          new Rule(
              "dependency-check",
              anyOf(sequence("dependency-check:check"), sequence("dependencyCheckAnalyze"))),
          new Rule("enforcer", sequence("enforcer:enforce")),
          new Rule("gitleaks", sequence("gitleaks")),
          new Rule("semgrep", sequence("semgrep")),
          new Rule("trivy", sequence("trivy")),
          new Rule("eslint", sequence("eslint")),
          new Rule("tsc", sequence("tsc")),
          new Rule("vue-tsc", sequence("vue-tsc")),
          new Rule("svelte-check", sequence("svelte-check")),
          new Rule("astro-check", sequence("astro", "check")),
          new Rule("ngc", sequence("ngc")),
          new Rule("biome", anyOf(sequence("biome", "check"), sequence("biome", "lint"))),
          new Rule("vitest", sequence("vitest")),
          new Rule("jest", sequence("jest")),
          new Rule("bun-test", sequence("bun", "test")),
          new Rule(
              "dependency-cruiser", anyOf(sequence("depcruise"), sequence("dependency-cruiser"))),
          new Rule("openspec", sequence("openspec", "validate")));

  /** One row: the type a command gets when {@code matches} accepts it. */
  private record Rule(String type, Predicate<List<String>> matches) {}

  private CommandTypeInference() {}

  /**
   * Returns the one type the command implies, or {@code command} when none or several match. The
   * executable's file name counts, so {@code /usr/local/bin/openspec validate} is an OpenSpec
   * check.
   */
  public static String infer(final List<String> command) {
    final List<String> tokens = new ArrayList<>(command);
    if (!tokens.isEmpty()) {
      tokens.set(0, fileName(tokens.getFirst()));
    }
    final Set<String> types = new LinkedHashSet<>();
    RULES.stream()
        .filter(rule -> rule.matches().test(tokens))
        .forEach(rule -> types.add(rule.type()));
    return types.size() == 1 ? types.iterator().next() : COMMAND;
  }

  private static String fileName(final String executable) {
    return executable.substring(executable.lastIndexOf('/') + 1);
  }

  /** Returns the types that some command row can infer. */
  public static Set<String> inferable() {
    return RULES.stream().map(Rule::type).collect(Collectors.toUnmodifiableSet());
  }

  /** A Maven or Gradle test run without a test filter; a filtered run is a plain command. */
  private static boolean isUnfilteredBuildTest(final List<String> command) {
    return buildWrapper(sequence("test")).test(command)
        && command.stream()
            .noneMatch(
                token ->
                    token.startsWith(TEST_FILTER_MAVEN) || token.startsWith(TEST_FILTER_GRADLE));
  }

  /** Accepts a command whose first token is a Maven or Gradle executable and which matches. */
  private static Predicate<List<String>> buildWrapper(final Predicate<List<String>> rule) {
    return command ->
        !command.isEmpty() && isBuildExecutable(command.getFirst()) && rule.test(command);
  }

  private static boolean isBuildExecutable(final String executable) {
    final String name = executable.substring(executable.lastIndexOf('/') + 1);
    return MAVEN.equals(name)
        || MAVEN_WRAPPER.equals(name)
        || GRADLE.equals(name)
        || GRADLE_WRAPPER.equals(name);
  }

  /** Matches the tokens as one contiguous run; a trailing {@code *} matches any suffix. */
  private static Predicate<List<String>> sequence(final String... tokens) {
    return command -> {
      for (int start = 0; start + tokens.length <= command.size(); start++) {
        if (runMatches(command, start, tokens)) {
          return true;
        }
      }
      return false;
    };
  }

  private static boolean runMatches(
      final List<String> command, final int start, final String... tokens) {
    for (int i = 0; i < tokens.length; i++) {
      if (!tokenMatches(command.get(start + i), tokens[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean tokenMatches(final String token, final String pattern) {
    if (pattern.endsWith("*")) {
      return token.startsWith(pattern.substring(0, pattern.length() - 1));
    }
    return token.equals(pattern);
  }

  @SafeVarargs
  private static Predicate<List<String>> anyOf(final Predicate<List<String>>... rules) {
    return command -> Arrays.stream(rules).anyMatch(rule -> rule.test(command));
  }
}
