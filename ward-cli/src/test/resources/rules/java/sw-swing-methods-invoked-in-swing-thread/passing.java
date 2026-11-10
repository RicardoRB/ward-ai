import javax.swing.JFrame;
import javax.swing.SwingUtilities;

final class Check {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          JFrame frame = new JFrame("x");
          frame.pack();
          frame.setVisible(true);
        });
  }

  void show() {
    JFrame frame = new JFrame("x");
    frame.setVisible(true);
  }
}
