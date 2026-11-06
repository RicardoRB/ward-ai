package dev.ward.analysis.frontend.antlr.format.swift;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;

/**
 * One open region while the document is built: a block, a case body, a bracket list or the root.
 */
final class SwiftFrame {

  /** Marks a frame that has no closing token. */
  static final int NO_CLOSE = -1;

  /** What the region is, which decides how its contents are indented and broken. */
  enum Kind {
    ROOT,
    PLAIN,
    BLOCK,
    SWITCH,
    CASE_BODY,
    INDENT,
    GROUP
  }

  private final Kind kind;
  private final int closeIndex;
  private final String openText;
  private final String closeText;
  private final List<Document> parts = new ArrayList<>();
  private boolean switchPending;
  private boolean labelOpen;

  private SwiftFrame(
      final Kind kind, final int closeIndex, final String openText, final String closeText) {
    this.kind = kind;
    this.closeIndex = closeIndex;
    this.openText = openText;
    this.closeText = closeText;
  }

  /**
   * Creates a frame without bracket texts.
   *
   * @param kind the region kind
   * @param closeIndex the index of the closing token, or {@link #NO_CLOSE}
   * @return the frame
   */
  static SwiftFrame of(final Kind kind, final int closeIndex) {
    return new SwiftFrame(kind, closeIndex, "", "");
  }

  /**
   * Creates a frame for a bracket list that breaks one item per line when it does not fit.
   *
   * @param openText the opening bracket
   * @param closeText the closing bracket
   * @param closeIndex the index of the closing bracket
   * @return the group frame
   */
  static SwiftFrame group(final String openText, final String closeText, final int closeIndex) {
    return new SwiftFrame(Kind.GROUP, closeIndex, openText, closeText);
  }

  Kind getKind() {
    return kind;
  }

  int getCloseIndex() {
    return closeIndex;
  }

  List<Document> getParts() {
    return parts;
  }

  void add(final Document document) {
    parts.add(document);
  }

  void markSwitch() {
    switchPending = true;
  }

  /**
   * Reports whether the next brace opens the body of a pending {@code switch}, and clears the mark.
   *
   * @return true when a switch body is pending
   */
  boolean takeSwitch() {
    final boolean pending = switchPending;
    switchPending = false;
    return pending;
  }

  void openLabel() {
    labelOpen = true;
  }

  /**
   * Reports whether a case label is open, and closes it.
   *
   * @return true when a case label was open
   */
  boolean takeLabel() {
    final boolean open = labelOpen;
    labelOpen = false;
    return open;
  }

  /**
   * Wraps the frame's contents: a group breaks one item per line, a switch body is not indented,
   * and every other region is indented one level.
   *
   * @return the document of the closed region
   */
  Document document() {
    return switch (kind) {
      case GROUP ->
          Document.group(
              Document.concat(
                  Document.text(openText),
                  Document.indent(body()),
                  Document.softLine(),
                  Document.text(closeText)));
      case SWITCH -> body();
      default -> Document.indent(body());
    };
  }

  private Document body() {
    return new Document.Concat(parts);
  }
}
