package dev.ward.domain.project;

/** The build tool that defines a project. */
public enum BuildTool {
  MAVEN("Maven", Toolchain.JVM, "mvn"),
  GRADLE("Gradle", Toolchain.JVM, "gradle"),
  NPM("npm", Toolchain.NODE, "npm"),
  PNPM("pnpm", Toolchain.NODE, "pnpm"),
  YARN("Yarn", Toolchain.NODE, "yarn"),
  BUN("Bun", Toolchain.NODE, "bun"),
  UV("uv", Toolchain.PYTHON, "uv"),
  POETRY("Poetry", Toolchain.PYTHON, "poetry"),
  PDM("PDM", Toolchain.PYTHON),
  HATCH("Hatch", Toolchain.PYTHON),
  PIP("pip", Toolchain.PYTHON),
  GO("Go", Toolchain.GO),
  CARGO("Cargo", Toolchain.RUST),
  DOTNET("dotnet", Toolchain.DOTNET),
  SWIFTPM("SwiftPM", Toolchain.SWIFT, "swift"),
  XCODE("Xcode", Toolchain.SWIFT, "xcodebuild"),
  TUIST("Tuist", Toolchain.SWIFT, "xcodebuild"),
  XCODEGEN("XcodeGen", Toolchain.SWIFT, "xcodebuild");

  private final String displayName;
  private final Toolchain toolchain;
  private final String executable;

  BuildTool(final String displayName, final Toolchain toolchain) {
    this(displayName, toolchain, displayName);
  }

  BuildTool(final String displayName, final Toolchain toolchain, final String executable) {
    this.displayName = displayName;
    this.toolchain = toolchain;
    this.executable = executable;
  }

  /** Returns the name shown to users. */
  public String displayName() {
    return displayName;
  }

  /** Returns the executable on PATH that runs this build tool. */
  public String executable() {
    return executable;
  }

  /** Returns the toolchain this build tool belongs to. */
  public Toolchain toolchain() {
    return toolchain;
  }
}
