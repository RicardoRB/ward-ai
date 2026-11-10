package dev.ward.domain.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Where an {@code xcodebuild-test} check runs its tests, as written in {@code ward.toml}. */
public enum XcodePlatform {
  IOS_SIMULATOR("iOS Simulator"),
  MACOS("macOS");

  private final String label;

  XcodePlatform(final String label) {
    this.label = label;
  }

  /** Returns the value written in {@code ward.toml}. */
  public String label() {
    return label;
  }

  /** Returns the platform written as {@code label}, or empty for any other value. */
  public static Optional<XcodePlatform> fromLabel(final String label) {
    return Arrays.stream(values()).filter(platform -> platform.label.equals(label)).findFirst();
  }

  /** Returns every value {@code ward.toml} accepts, in declaration order. */
  public static List<String> labels() {
    return Arrays.stream(values()).map(XcodePlatform::label).toList();
  }
}
