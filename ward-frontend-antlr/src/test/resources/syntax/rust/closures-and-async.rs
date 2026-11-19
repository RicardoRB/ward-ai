use std::future::Future;
use std::pin::Pin;

async fn fetch(id: u64) -> Result<String, std::io::Error> {
    let body = read(id).await?;
    Ok(body)
}

async fn read(_id: u64) -> Result<String, std::io::Error> {
    Ok(String::new())
}

fn run() {
    let double = |x: i32| -> i32 { x * 2 };
    let sum = |a, b| a + b;
    let owned = move || println!("{}", double(2) + sum(1, 2));
    owned();

    let future = async {
        let first = fetch(1).await;
        first.map(|text| text.len())
    };
    let moved = async move { fetch(2).await };
    let _ = (future, moved);

    let closure_future = async |value: u8| value + 1;
    let _ = closure_future;

    let boxed: Pin<Box<dyn Future<Output = u8>>> = Box::pin(async { 7 });
    let _ = boxed;

    let unsafe_block = unsafe { std::mem::zeroed::<u8>() };
    let _ = unsafe_block;
}

fn callbacks() {
    let mut handlers: Vec<Box<dyn FnMut(u8)>> = Vec::new();
    let mut total = 0;
    handlers.push(Box::new(move |x| total += x));
    let _ = handlers.iter().map(|_| 1).collect::<Vec<_>>();
}
