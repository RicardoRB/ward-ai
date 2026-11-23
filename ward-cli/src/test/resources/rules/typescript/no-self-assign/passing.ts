function f(o) {
  o.total = o.total + 1;
}

class Session {
  count = 0;
  next(session: Session) {
    session.count = session.count! - 1;
  }
}
