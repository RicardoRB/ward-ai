package dev.ward.analysis.domain.rule.check;

import org.junit.jupiter.api.Test;

/** What the weak digest check says about where a digest goes. */
class WeakDigestIdentifierTest {
  @Test
  void usedForSecurityFalseIsNotReported() {
    WeakDigestRuns.assertNone(
        "def f(d):\n    return hashlib.md5(d, usedforsecurity=False).hexdigest()\n"
    );
  }

  @Test
  void stemOfAFileNameIsAnIdentifier() {
    WeakDigestRuns.assertNone("def f(d):\n    n = hashlib.md5(d).hexdigest() + '.bin'\n");
  }

  @Test
  void pathAndKeyUsesAreIdentifiers() {
    WeakDigestRuns.assertNone(
        "def f(d, root):\n    p = os.path.join(root, hashlib.md5(d).hexdigest())\n"
    );
    WeakDigestRuns.assertNone("def f(d, c):\n    c[hashlib.sha1(d).hexdigest()] = 1\n");
    WeakDigestRuns.assertNone(
        "def f(d):\n    h = hashlib.sha1()\n    h.update(d)\n    n = h.hexdigest()\n    open(n)\n"
    );
  }
}
