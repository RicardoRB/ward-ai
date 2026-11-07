class Flows {
  boolean run(@Null Object never, @NonNull Object always, @Nullable Object maybe) {
    return never == maybe || always == maybe || always == always;
  }
}
