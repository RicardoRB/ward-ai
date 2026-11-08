final class T extends Activity {
  @Override
  void onCreate(Bundle state) {
    AndroidInjection.inject(this);
    super.onCreate(state);
  }
}
