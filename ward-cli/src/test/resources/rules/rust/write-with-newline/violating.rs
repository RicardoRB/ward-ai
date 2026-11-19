use std::fmt::Write;

pub fn hello(out: &mut String) {
    let _ = write!(out, "hello\n");
}
