enum ParseError: Error {
  case empty
}

func parse(_ text: String) throws(ParseError) -> Int {
  throw ParseError.empty
}

func anyFailure() throws(any Error) {}
