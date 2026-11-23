pub fn run() -> i32 {
    return 1;
    let x = 2;
    x
}

pub fn serve() {
    spawn(async move {
        loop {
            step().await;
        }

        Ok::<(), String>(())
    });
}
