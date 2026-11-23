pub fn run() -> i32 {
    let mut total = 1;
    total = 2;
    total
}

pub fn dead_in_closure() -> impl Fn() -> u32 {
    || {
        let mut n = 1;
        n = 2;
        n
    }
}

pub fn dead_in_async() {
    let task = async {
        let mut m = 1;
        m = 2;
        m
    };
    spawn(task);
}

pub fn dead_in_nested(outer: u32) -> impl Fn() -> u32 {
    move || {
        let inner = || {
            let mut k = outer;
            k = 0;
            k
        };
        inner()
    }
}
