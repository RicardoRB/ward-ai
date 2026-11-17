package dev.ward.infrastructure.rule.catalog;

/**
 * A pinned corpus: what it is, the release and archive hash it is pinned to, and the committed
 * files and system property of its smoke test.
 *
 * @param title the name of the application
 * @param tag the release the committed report measures
 * @param sha256 the SHA-256 of the source archive of that tag
 * @param description the licence and the framework, for the report header
 * @param report the committed report, below {@code benchmark/}
 * @param labels the committed labels, below {@code benchmark/}
 * @param cost the hand-written cost file, below {@code benchmark/}
 * @param testClass the smoke test class that runs it
 * @param property the system property that names the extracted archive
 */
record PythonCorpusSpec(
    String title,
    String tag,
    String sha256,
    String description,
    String report,
    String labels,
    String cost,
    String testClass,
    String property
) {}
