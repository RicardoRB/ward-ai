pub fn second(s: &str) -> Option<u8> {
    s.bytes().nth(1)
}
