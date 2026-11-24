package dev.ward.analysis.domain.python;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Which tables of lambdas count as fixed for a lookup. */
class PythonLambdaTablesTest {
  @Test
  void aLambdaThatPassesConstantsToADefinitionIsAMember() {
    assertThat(
        PythonFixedLookupsTest.writes(
            "def one(admin=False):\n    return 1\n"
                + "TABLE = {'a': one, 'b': lambda: one(admin=True)}\n"
                + "def f(key):\n    picked = TABLE.get(key)\n    use(picked)\n"
        )
    )
        .isEqualTo(2);
  }

  @Test
  void aLambdaThatReadsAnotherNameIsNot() {
    assertThat(
        PythonFixedLookupsTest.writes(
            "def one(admin=False):\n    return 1\n"
                + "TABLE = {'a': one, 'b': lambda: one(admin=flag)}\n"
                + "def f(key):\n    picked = TABLE.get(key)\n    use(picked)\n"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aLambdaWithAParameterIsNot() {
    assertThat(
        PythonFixedLookupsTest.writes(
            "TABLE = {'a': lambda x: x}\n"
                + "def f(key):\n    picked = TABLE.get(key)\n    use(picked)\n"
        )
    )
        .isEqualTo(1);
  }
}
