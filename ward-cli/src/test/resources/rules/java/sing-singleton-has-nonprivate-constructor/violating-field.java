class Calendars {
  public static final Calendars INSTANCE = getInstance();

  public Calendars() {
  }

  public static Calendars getInstance() {
    return new Calendars();
  }
}
