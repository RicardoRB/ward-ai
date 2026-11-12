package dev.ward.analysis.domain.rule.check;

/** A view of a text that charges every character the regex reads to a {@link WorkBudget}. */
final class BudgetedText implements CharSequence {

  private final String text;
  private final int start;
  private final int end;
  private final WorkBudget budget;

  /** Views the whole {@code text}. */
  BudgetedText(final String text, final WorkBudget budget) {
    this(text, 0, text.length(), budget);
  }

  private BudgetedText(final String text, final int start, final int end, final WorkBudget budget) {
    this.text = text;
    this.start = start;
    this.end = end;
    this.budget = budget;
  }

  @Override
  public int length() {
    return end - start;
  }

  @Override
  public char charAt(final int index) {
    budget.spend();
    return text.charAt(start + index);
  }

  @Override
  public CharSequence subSequence(final int from, final int to) {
    return new BudgetedText(text, start + from, start + to, budget);
  }

  @Override
  public String toString() {
    return text.substring(start, end);
  }
}
