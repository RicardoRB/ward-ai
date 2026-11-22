pub fn second(s: &str) -> Option<u8> {
    s.as_bytes().get(1).copied()
}
