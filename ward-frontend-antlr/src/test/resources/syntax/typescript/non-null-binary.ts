class Session {
  count = 0
  next(session: Session) {
    session.count = session.count! - 1;
    const half = session.count! / 2;
    session.count = session.count! + 1;
  }
}
