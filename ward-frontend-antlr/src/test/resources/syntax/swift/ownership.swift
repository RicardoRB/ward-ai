struct Buffer {}

func read(_ buffer: borrowing Buffer) {}

func take(_ buffer: consuming Buffer) {
  let moved = consume buffer
  read(moved)
}
