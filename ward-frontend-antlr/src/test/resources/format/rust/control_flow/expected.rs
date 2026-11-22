fn run(x: Option<i32>) -> i32 {
    match x {
        Some(n) if n > 0 => n,
        Some(_) => 0,
        None => -1,
    }
}

fn looping() {
    'outer: loop {
        for i in 0..3 {
            while i < 2 {
                break 'outer;
            }
        }
    }
    if let Some(v) = run(None).checked_add(1) {
        let _ = v;
    }
    let Point { x, .. } = p;
}
