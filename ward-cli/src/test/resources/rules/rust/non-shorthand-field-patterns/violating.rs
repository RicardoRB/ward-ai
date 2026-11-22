pub struct P {
    pub a: i32,
}

pub fn first(p: P) -> i32 {
    let P { a: a } = p;
    a
}
