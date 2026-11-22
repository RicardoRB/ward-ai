pub fn has(o: Option<i32>) -> bool {
    if let Some(_) = o {
        return true;
    }
    false
}
