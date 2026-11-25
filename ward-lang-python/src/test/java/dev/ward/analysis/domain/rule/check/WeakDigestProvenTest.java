package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.rule.RuleMatch;
import org.junit.jupiter.api.Test;

/** What the weak digest check says about where a digest goes. */
class WeakDigestProvenTest {
  @Test
  void aDigestThatIsNeverUsedIsProven() {
    WeakDigestRuns.assertConfidence("def f(d):\n    hashlib.md5(d)\n", Confidence.PROVEN);
    WeakDigestRuns.assertConfidence(
        "def f(d):\n    h = hashlib.sha1()\n    h.update(d)\n",
        Confidence.PROVEN
    );
  }

  @Test
  void aComparedDigestIsProven() {
    WeakDigestRuns.assertConfidence(
        "def f(p, s):\n    return hashlib.md5(p).hexdigest() == s\n",
        Confidence.PROVEN
    );
    WeakDigestRuns.assertConfidence(
        "def f(p, s):\n    h = hashlib.md5(p).hexdigest()\n    return h == s\n",
        Confidence.PROVEN
    );
  }

  @Test
  void anAuthenticationFunctionMakesItProven() {
    WeakDigestRuns.assertConfidence(
        "def f(p, s):\n    return hmac.compare_digest(hashlib.sha1(p).hexdigest(), s)\n",
        Confidence.PROVEN
    );
  }

  @Test
  void aReturnedDigestWithoutCallersIsProven() {
    WeakDigestRuns.assertConfidence(
        "def f(d):\n    return hashlib.md5(d).hexdigest()\n",
        Confidence.PROVEN
    );
  }

  @Test
  void aReturnedDigestThatACallerComparesIsProven() {
    WeakDigestRuns.assertConfidence(
        "def key(d):\n    return hashlib.sha1(d).hexdigest()\n"
            + "def check(d, s):\n    return key(d) == s\n",
        Confidence.PROVEN
    );
  }

  @Test
  void oneSecurityUseAmongIdentifiersKeepsTheFindingProven() {
    WeakDigestRuns.assertConfidence(
        "def f(d, s, root):\n    n = hashlib.md5(d).hexdigest()\n    open(n)\n    return n == s\n",
        Confidence.PROVEN
    );
  }

  @Test
  void twoDigestsAreJudgedApart() {
    assertThat(
        WeakDigestRuns.run(
            "import hashlib\ndef f(d, s):\n    a = hashlib.md5(d).hexdigest()\n"
                + "    b = hashlib.sha1(d).hexdigest()\n    open(a)\n    return b == s\n"
        )
    ).extracting(RuleMatch::confidence).containsExactly(Confidence.PROVEN);
  }

  @Test
  void aChunkedDigestThatIsReturnedAndNeverCalledIsProven() {
    WeakDigestRuns.assertConfidence(
        "def hash_file(fp):\n    h = hashlib.sha1()\n    h.update(fp.read())\n    return h.hexdigest()\n",
        Confidence.PROVEN
    );
  }
}
