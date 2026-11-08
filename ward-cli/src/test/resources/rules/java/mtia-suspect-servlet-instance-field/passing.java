import javax.servlet.http.HttpServlet;

final class T extends HttpServlet {
  private final int fixed = 1;
  private static int shared;
}

final class U {
  private int hits;
}
