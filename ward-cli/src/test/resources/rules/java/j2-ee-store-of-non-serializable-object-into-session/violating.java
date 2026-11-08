final class T {
  void run(HttpSession session) {
    session.setAttribute("k", new Thread());
  }
}
