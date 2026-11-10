package dev.ward.analysis.domain.format;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Lays out a {@link Document} with the Wadler fit-or-break rule: a group prints flat when it fits
 * on the rest of the current line, and breaks every line inside it otherwise. Instances hold only
 * their style and are safe to reuse.
 */
public final class DocPrinter {

  private static final int ROOT_LEVEL = 0;
  private static final String LF = "\n";
  private static final String CRLF = "\r\n";

  private final PrintStyle style;

  /**
   * Creates a printer for one style.
   *
   * @param style the layout settings
   * @throws NullPointerException if the style is missing
   */
  public DocPrinter(final PrintStyle style) {
    this.style = Objects.requireNonNull(style, "style");
  }

  /**
   * Prints a document.
   *
   * @param document the document; its text must already use {@code \n} line endings
   * @return the printed text, with every break written as the style's line ending
   * @throws NullPointerException if the document is missing
   */
  public String print(final Document document) {
    Objects.requireNonNull(document, "document");
    final Layout layout = new Layout();
    layout.run(document);
    final String text = layout.text();
    return style.endOfLine() == EndOfLine.CRLF ? text.replace(LF, CRLF) : text;
  }

  private static boolean forcesBreak(final Document document) {
    if (document instanceof Document.Line line) {
      return line.forced();
    }
    if (document instanceof Document.Indent indent) {
      return forcesBreak(indent.body());
    }
    if (document instanceof Document.Group group) {
      return forcesBreak(group.body());
    }
    if (document instanceof Document.IfBreak ifBreak) {
      return forcesBreak(ifBreak.broken()) || forcesBreak(ifBreak.flat());
    }
    return document instanceof Document.Concat concat
        && concat.parts().stream().anyMatch(DocPrinter::forcesBreak);
  }

  private static void pushParts(
      final Deque<Command> target, final Command parent, final List<Document> parts) {
    for (int i = parts.size() - 1; i >= 0; i--) {
      target.push(new Command(parent.level(), parent.flat(), parts.get(i)));
    }
  }

  /**
   * Returns the width a command adds to its line before any break, and queues the parts of a
   * container so that {@link #fits} measures them next.
   */
  private static int measure(final Command next, final Deque<Command> expanding) {
    final Document document = next.document();
    if (document instanceof Document.Text text) {
      final int newline = text.text().indexOf('\n');
      return newline < 0 ? text.text().length() : newline;
    }
    if (document instanceof Document.Line line) {
      return line.forced() || !next.flat() ? 0 : line.flat().length();
    }
    expand(next, expanding);
    return 0;
  }

  /** Queues the parts of a container, so that the measure reads them next. */
  private static void expand(final Command next, final Deque<Command> expanding) {
    final Document document = next.document();
    if (document instanceof Document.Concat concat) {
      pushParts(expanding, next, concat.parts());
    } else if (document instanceof Document.Indent indent) {
      expanding.push(new Command(next.level() + 1, next.flat(), indent.body()));
    } else if (document instanceof Document.Group group) {
      final boolean flat = next.flat() && !forcesBreak(group.body());
      expanding.push(new Command(next.level(), flat, group.body()));
    } else if (document instanceof Document.IfBreak ifBreak) {
      expanding.push(new Command(next.level(), next.flat(), chosen(next, ifBreak)));
    }
  }

  /** The alternative an if-break prints for the command: flat when the group is flat. */
  private static Document chosen(final Command command, final Document.IfBreak ifBreak) {
    return command.flat() ? ifBreak.flat() : ifBreak.broken();
  }

  /** Tells whether a command ends the line when measured: a break, or text with a newline. */
  private static boolean endsLine(final Command next) {
    final Document document = next.document();
    if (document instanceof Document.Line line) {
      return line.forced() || !next.flat();
    }
    return document instanceof Document.Text text && text.text().indexOf('\n') >= 0;
  }

  /** A document waiting to be placed, with its indentation level and whether it is flat. */
  private record Command(int level, boolean flat, Document document) {}

  /** The state of one print: the output so far, the pending commands and the column. */
  private final class Layout {

    private final List<String> out = new ArrayList<>();
    private final Deque<Command> pending = new ArrayDeque<>();
    private int column;

    void run(final Document root) {
      pending.push(new Command(ROOT_LEVEL, false, root));
      while (!pending.isEmpty()) {
        place(pending.pop());
      }
    }

    String text() {
      return String.join("", out);
    }

    private void place(final Command command) {
      final Document document = command.document();
      if (document instanceof Document.Line line) {
        placeLine(command, line);
      } else if (document instanceof Document.Group group) {
        placeGroup(command, group);
      } else {
        emit(command, document);
      }
    }

    private void emit(final Command command, final Document document) {
      if (document instanceof Document.Text text) {
        writeText(text.text());
      } else if (document instanceof Document.Concat concat) {
        pushParts(pending, command, concat.parts());
      } else if (document instanceof Document.Indent indent) {
        pending.push(new Command(command.level() + 1, command.flat(), indent.body()));
      } else if (document instanceof Document.IfBreak ifBreak) {
        pending.push(new Command(command.level(), command.flat(), chosen(command, ifBreak)));
      }
    }

    private void placeLine(final Command command, final Document.Line line) {
      if (command.flat() && !line.forced()) {
        writeText(line.flat());
      } else {
        breakLine(command.level());
      }
    }

    private void placeGroup(final Command command, final Document.Group group) {
      final Document body = group.body();
      final boolean flat =
          command.flat()
              || (!forcesBreak(body)
                  && fits(style.lineWidth() - column, new Command(command.level(), true, body)));
      pending.push(new Command(command.level(), flat, body));
    }

    /**
     * Tells whether the candidate, printed flat, fits before the line width is exceeded and before
     * the first break among the pending commands that follow it on the same line.
     */
    private boolean fits(final int width, final Command candidate) {
      final Deque<Command> expanding = new ArrayDeque<>();
      expanding.push(candidate);
      final Iterator<Command> rest = pending.iterator();
      int remaining = width;
      while (remaining >= 0) {
        if (expanding.isEmpty() && !rest.hasNext()) {
          return true;
        }
        final Command next = expanding.isEmpty() ? rest.next() : expanding.pop();
        remaining -= measure(next, expanding);
        if (endsLine(next)) {
          return remaining >= 0;
        }
      }
      return false;
    }

    private void breakLine(final int level) {
      out.add(LF);
      final int indentation = level * style.indentWidth();
      if (style.indentStyle() == IndentStyle.TAB) {
        out.add("\t".repeat(level));
      } else {
        out.add(" ".repeat(indentation));
      }
      column = indentation;
    }

    private void writeText(final String text) {
      out.add(text);
      final int newline = text.lastIndexOf('\n');
      column = newline < 0 ? column + text.length() : text.length() - newline - 1;
    }
  }
}
