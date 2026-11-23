use std::collections::HashMap;

pub fn make() -> HashMap<i32, i32> {
    HashMap::new()
}

pub enum Kind {
    Small,
    Large,
}

pub fn weight(kind: Kind) -> u32 {
    use self::Kind::*;
    match kind {
        Small => 1,
        Large => 2,
    }
}
