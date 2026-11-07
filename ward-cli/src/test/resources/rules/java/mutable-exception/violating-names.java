class ParseFailure extends Exception {
  int line;
}

class ParseException extends Exception {
  int column;
}
