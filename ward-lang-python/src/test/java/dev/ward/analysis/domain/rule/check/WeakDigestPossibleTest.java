package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Confidence;
import org.junit.jupiter.api.Test;

/** What the weak digest check says about where a digest goes. */
class WeakDigestPossibleTest {
  @Test
  void aReturnedDigestThatCallersOnlyNameFilesIsPossible() {
    WeakDigestRuns.assertConfidence(
        "def key(d):\n    h = hashlib.sha1()\n    h.update(d)\n    return h.hexdigest()\n"
            + "def save(d, root):\n    return os.path.join(root, key(d))\n",
        Confidence.POSSIBLE
    );
  }

  @Test
  void aDigestStoredInAnItemIsPossible() {
    WeakDigestRuns.assertConfidence(
        "def f(d, args):\n    s = hashlib.sha1(d).hexdigest()\n    args['sum'] = s\n",
        Confidence.POSSIBLE
    );
  }

  @Test
  void aDigestPassedToAnUnfollowedCallIsPossible() {
    WeakDigestRuns.assertConfidence(
        "def f(d):\n    send(hashlib.md5(d).hexdigest())\n",
        Confidence.POSSIBLE
    );
  }

  @Test
  void aChunkedDigestThatACallerStoresInAModelIsPossible() {
    WeakDigestRuns.assertConfidence(
        "def hash_file(fp, algo='sha1'):\n"
            + "    fp.seek(0)\n"
            + "    if algo == 'sha1':\n"
            + "        h = hashlib.sha1()\n"
            + "        while chunk := fp.read(1024):\n"
            + "            h.update(chunk)\n"
            + "        fp.seek(0)\n"
            + "        return h.hexdigest()\n"
            + "    raise NotImplementedError\n"
            + "def upload(file_obj, model):\n"
            + "    args = {'type': 'x'}\n"
            + "    sha1sum = hash_file(fp=file_obj)\n"
            + "    args['sha1sum'] = sha1sum\n"
            + "    return model(**args)\n",
        Confidence.POSSIBLE
    );
  }
}
