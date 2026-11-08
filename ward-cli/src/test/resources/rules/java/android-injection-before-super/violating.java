final class T extends Activity {
  @Override
  void onCreate(Bundle state) {
    super.onCreate(state);
    AndroidInjection.inject(this);
  }
}
