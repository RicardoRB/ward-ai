package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnusedCollectionModifiedCheckTest {
  private static final UnusedCollectionModifiedCheck CHECK =
      new UnusedCollectionModifiedCheck("message");

  private static List<String> run(final String body) {
    return new ParsedFacts(
        "import java.util.*;\nclass A {\n  void f(int x) {\n"
            + "    Set<Integer> seen = new HashSet<>();\n"
            + body
            + "\n  }\n}\n"
    )
        .run(CHECK);
  }

  @Test
  void aCollectionThatIsOnlyFilledInIsReported() {
    assertThat(run("    seen.add(x);")).containsExactly("4:5");
  }

  @Test
  void aCollectionWhoseAddResultIsUsedIsNotReported() {
    assertThat(run("    if (seen.add(x)) {\n      x++;\n    }")).isEmpty();
    assertThat(run("    if (!seen.add(x)) {\n      x++;\n    }")).isEmpty();
    assertThat(run("    boolean fresh = seen.add(x);")).isEmpty();
    assertThat(run("    while (seen.add(x)) {\n      x++;\n    }")).isEmpty();
  }
}
