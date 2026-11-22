use std::fmt::Write;

pub fn hello(out: &mut String) {
    let _ = writeln!(out, "hello");
}
