pub fn run() -> i32 {
    let x = 2;
    return x;
}

pub fn selects(mut n: u32) -> u32 {
    loop {
        select! {
            _ = n => break,
            else => n += 1,
        }
    }
    n
}
