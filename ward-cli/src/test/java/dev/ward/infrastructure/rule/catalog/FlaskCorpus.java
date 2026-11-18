package dev.ward.infrastructure.rule.catalog;

/** CTFd, a Flask application (Apache-2.0): the second Python corpus, only measured. */
final class FlaskCorpus {
  /** The release the committed report measures. */
  static final String TAG = "3.8.9";

  /** The SHA-256 of the source archive of that tag. */
  static final String ARCHIVE_SHA256 =
      "239fd7ccc433eac9787208225b449983aee2fd90922d7a8baa26579b739f72ba";

  /** The pinned corpus. */
  static final PythonCorpusSpec SPEC =
      new PythonCorpusSpec(
          "CTFd",
          TAG,
          ARCHIVE_SHA256,
          "Apache-2.0, a Flask application",
          "flask-corpus-report.txt",
          "flask-corpus-labels.txt",
          "flask-corpus-cost.txt",
          "FlaskCorpusSmokeTest",
          "ward.benchmark.flask-corpus"
      );

  private FlaskCorpus() {}
}
