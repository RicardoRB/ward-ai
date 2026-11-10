import java.time.DayOfWeek;
import java.time.temporal.ChronoField;

final class Violating {
  int run(DayOfWeek day) {
    return day.get(ChronoField.CLOCK_HOUR_OF_DAY);
  }
}
