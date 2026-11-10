package dev.ward.analysis.domain.node;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The globals of the test frameworks, which a test file may use without importing them. A file is a
 * test file by its path alone, using the patterns that ESLint configs commonly use: a {@code .test}
 * or {@code .spec} file, a file under {@code __tests__}, or a file under a top-level {@code test}
 * or {@code tests} folder. Declaring a global can only remove a finding, so a test file that does
 * not use a framework is only missed by no-undef, never reported wrongly.
 *
 * <p>Sources: the {@code globals} package, version 14.0.0, for its {@code jest} and {@code mocha}
 * sets, and the globals that Vitest documents for its {@code globals} option (vitest.dev, config
 * reference). The three sets are unioned.
 */
final class NodeTestGlobals {

  /** The {@code jest} set of the {@code globals} package. */
  private static final Set<String> JEST =
      Set.of(
          "afterAll",
          "afterEach",
          "beforeAll",
          "beforeEach",
          "describe",
          "expect",
          "fdescribe",
          "fit",
          "it",
          "jest",
          "pit",
          "require",
          "test",
          "xdescribe",
          "xit",
          "xtest");

  /** The {@code mocha} set of the {@code globals} package. */
  private static final Set<String> MOCHA =
      Set.of(
          "after",
          "afterEach",
          "before",
          "beforeEach",
          "context",
          "describe",
          "it",
          "mocha",
          "run",
          "setup",
          "specify",
          "suite",
          "suiteSetup",
          "suiteTeardown",
          "teardown",
          "test",
          "xcontext",
          "xdescribe",
          "xit",
          "xspecify");

  /** The globals Vitest documents for its {@code globals} option. */
  private static final Set<String> VITEST =
      Set.of(
          "afterAll",
          "afterEach",
          "assertType",
          "bench",
          "beforeAll",
          "beforeEach",
          "describe",
          "expect",
          "expectTypeOf",
          "it",
          "suite",
          "test",
          "vi");

  /** A file whose name ends in {@code .test} or {@code .spec} before a script extension. */
  private static final Pattern TEST_FILE =
      Pattern.compile("(^|/)[^/]*\\.(test|spec)\\.(js|jsx|mjs|cjs|ts|tsx|mts|cts)$");

  /** A file under a {@code __tests__} folder at any depth, or under a top-level test folder. */
  private static final Pattern TEST_FOLDER = Pattern.compile("(^|/)__tests__/|^(test|tests)/");

  private NodeTestGlobals() {}

  /** The test-framework globals of a file, by its project-relative path; none for other files. */
  static Set<String> of(final String path) {
    if (!TEST_FILE.matcher(path).find() && !TEST_FOLDER.matcher(path).find()) {
      return Set.of();
    }
    return Set.copyOf(union());
  }

  private static Set<String> union() {
    final Set<String> names = new HashSet<>(JEST);
    names.addAll(MOCHA);
    names.addAll(VITEST);
    return names;
  }
}
