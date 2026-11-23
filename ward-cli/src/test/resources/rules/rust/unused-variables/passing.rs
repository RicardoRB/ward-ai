pub fn run() -> i32 {
    let count = 1;
    count
}

pub fn named(mode: &str) -> Result<u32, String> {
    match mode {
        "a" => Ok(1),
        unk => anyhow::bail!("choice '{unk}' is unrecognized"),
    }
}

pub fn gated() -> usize {
    #[cfg(not(miri))]
    const ITER: usize = 1024;
    #[cfg(miri)]
    const ITER: usize = 32;
    ITER
}

pub fn shadowed(n: u32) -> u32 {
    #[cfg(unix)]
    let s = n + 1;
    #[cfg(not(unix))]
    let s = n + 2;
    s
}

pub trait Rotate {
    type Slots<const COUNT: u32>: Default;
}

pub fn raw(flag: Option<u8>) -> String {
    let mut out = String::new();
    if let Some(byte) = flag {
        let name = char::from(byte);
        write!(out, r"-{name}");
    }
    out
}

pub fn deferred(limit: usize) -> usize {
    let mut remaining = limit;
    macro_rules! take {
        () => {
            remaining -= 1
        };
    }
    take!();
    0
}

pub fn allowed() {
    #[allow(unused_variables)]
    let guard = 1;
}
