










class Main extends android.app.Service {
  void onDestroy() {
    int x = 1;
    super.onDestroy();
  }

  void helper() {}
}

class Plain {
  void onDestroy() {}
}
