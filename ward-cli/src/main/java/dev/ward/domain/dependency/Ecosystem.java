package dev.ward.domain.dependency;

/** The package ecosystem of an inventory entry, named as OSV and Package URLs name it. */
public enum Ecosystem {
  MAVEN("Maven", "maven"),
  NPM("npm", "npm"),
  PYPI("PyPI", "pypi"),
  SWIFT_URL("SwiftURL", "swift");

  private final String osvName;
  private final String purlType;

  Ecosystem(final String osvName, final String purlType) {
    this.osvName = osvName;
    this.purlType = purlType;
  }

  /** Returns the ecosystem name of the OSV schema, such as {@code Maven} or {@code SwiftURL}. */
  public String osvName() {
    return osvName;
  }

  /** Returns the Package URL type, such as {@code maven} or {@code swift}. */
  public String purlType() {
    return purlType;
  }
}
