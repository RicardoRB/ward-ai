pub fn run(x: i32) -> i32 {
    x
}

pub fn early(x: i32) {
    if x > 1 {
        return;
    }
    loop {
        if x > 2 {
            break;
        }
        continue;
    }
}

pub fn jumps(x: i32) -> i32 {
    let found = x;
    if x > 1 {
        return found;
    }
    loop {
        break;
    }
    found
}
