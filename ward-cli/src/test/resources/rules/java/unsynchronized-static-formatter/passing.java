import java.text.SimpleDateFormat;
class Dates {
  private static final String NAME = "yyyy";
  private SimpleDateFormat format;
  private static final FastDateFormat FAST = FastDateFormat.getInstance(NAME);
  static final SafeDecimalFormat SAFE = new SafeDecimalFormat();
}
