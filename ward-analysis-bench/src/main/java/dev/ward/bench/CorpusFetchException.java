package dev.ward.bench;

/** A pinned corpus commit could not be fetched or did not resolve to the pinned SHA. */
final class CorpusFetchException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  CorpusFetchException(final String name, final String commit, final String reason) {
    super(
        "Corpus repository "
            + name
            + " at pinned commit "
            + commit
            + ": "
            + reason
            + ". The run does not fall back to another revision.");
  }
}
