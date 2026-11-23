pub fn fetch_data(n: i32) -> i32 {
    let my_value = n;
    my_value
}

const LIMIT: u32 = 1;

pub fn r#async(n: u32) -> bool {
    match n {
        LIMIT => true,
        _ => false,
    }
}
