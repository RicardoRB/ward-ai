pub fn copy(v: &[String]) -> Vec<String> {
    v.iter().map(|s| s.clone()).collect()
}
