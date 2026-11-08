import javax.servlet.http.HttpServlet;

final class T extends HttpServlet {
  private static List<String> shared = new ArrayList<>();
  private int hits;
}

final class U {
  private List<String> seen = new ArrayList<>();
}
