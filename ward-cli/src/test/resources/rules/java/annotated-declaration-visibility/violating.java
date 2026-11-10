final class VisibilityViolations {
  @com.google.common.annotations.VisibleForTesting public void run() {}
  @VisibleForTesting private void hide() {}
  @VisibleForTesting void ok() {}
}
