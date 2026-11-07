class ParseFailure {
  int line;
}

class ParseException extends Exception {
  int column;
}
