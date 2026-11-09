package dev.ward.analysis.domain.python;

import static dev.ward.analysis.domain.python.SummaryFixtures.names;
import static dev.ward.analysis.domain.python.SummaryFixtures.summarize;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Symbol;
import org.junit.jupiter.api.Test;

/** Checks the methods, properties and attributes that the unused-member rules read. */
final class PythonMemberDefinitionsTest {

  @Test
  void declaresPlainStaticAndClassMethodsAsMethodsAndPropertiesAsProperties() {
    final FileSummary summary =
        summarize(
            "class C:\n    def m(self):\n        pass\n\n    @staticmethod\n    def s():\n"
                + "        pass\n\n    @property\n    def p(self):\n        return 1\n\n"
                + "    @functools.cached_property\n    def q(self):\n        return 2\n");

    assertThat(summary.declared())
        .extracting(Symbol::kind)
        .containsExactly("class", "method", "method", "property", "property");
    assertThat(names(summary.declared())).containsExactly("C", "m", "s", "p", "q");
  }

  @Test
  void leavesOutMethodsWhoseDecoratorMayRegisterThem() {
    final FileSummary summary =
        summarize(
            "class C:\n    @app.route('/')\n    def page(self):\n        pass\n\n"
                + "    @p.setter\n    def p(self, value):\n        pass\n");

    assertThat(names(summary.declared())).containsExactly("C");
  }

  @Test
  void declaresEveryStoredAttributeInAnyScopeAndNotAReadOfIt() {
    final FileSummary summary =
        summarize(
            "class C:\n    def __init__(self):\n        self.a = 1\n        self.a = 2\n"
                + "        self.b: int = 3\n\n    def run(self):\n        self.c += 1\n"
                + "        return self.a\n\nconfig.verbose = True\n");

    assertThat(summary.declared())
        .extracting(Symbol::kind)
        .containsExactly(
            "class",
            "method",
            "method",
            "attribute",
            "attribute",
            "attribute",
            "attribute",
            "attribute");
    assertThat(names(summary.declared()))
        .containsExactly("C", "__init__", "run", "a", "a", "b", "c", "verbose");
  }

  @Test
  void anAugmentedOrAssignedAttributeIsNotAUseOfTheAttributeName() {
    final FileSummary summary =
        summarize("class C:\n    def __init__(self):\n        self.a = 1\n        self.b += 1\n");

    assertThat(summary.usedNames()).doesNotContain("a", "b");
  }

  @Test
  void aGetattrOrHasattrStringNamesAnAttributeAsUsed() {
    final FileSummary summary =
        summarize(
            "def f(obj):\n    return getattr(obj, 'port') if hasattr(obj, \"debug\") else 0\n");

    assertThat(summary.usedNames()).contains("port", "debug");
  }

  @Test
  void importingUnittestUsesItsWhitelistedNames() {
    final FileSummary summary =
        summarize(
            "import unittest\n\nclass T(unittest.TestCase):\n    def setUp(self):\n        pass\n");

    assertThat(summary.usedNames()).contains("setUp", "tearDown", "setUpClass");
  }

  @Test
  void anAttributeReadInAnFStringIsUsed() {
    final FileSummary summary =
        summarize("class C:\n    def f(self):\n        return f\"{self.port}\"\n");

    assertThat(summary.usedNames()).contains("port");
  }
}
