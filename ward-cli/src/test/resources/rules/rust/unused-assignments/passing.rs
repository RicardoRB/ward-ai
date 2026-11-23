pub fn run() -> i32 {
    let mut total = 1;
    total += 2;
    total
}

pub fn flagged() -> bool {
    let mut called = false;
    let mut call = || {
        called = true;
    };
    call();
    called
}

pub fn branches(n: u32) -> (u32, u32) {
    let action;
    if n > 1 {
        action = 1;
    } else {
        action = 2;
    }
    (action, n)
}

pub fn counted(items: &[u32]) -> Vec<String> {
    let mut line_number = 0;
    let mut out = Vec::new();
    for item in items {
        line_number += 1;
        out.push(anyhow::anyhow!("{line_number}: {item}").to_string());
    }
    out
}

pub fn in_closure(n: u32) -> impl Fn() -> u32 {
    move || {
        let action;
        if n > 1 {
            action = 1;
        } else {
            action = 2;
        }
        action
    }
}

pub fn cursor(buf: &[u8]) -> Result<(), String> {
    let mut at = 0;
    loop {
        let rest = &buf[at..];
        if rest.is_empty() {
            break;
        }
        match send(rest) {
            Ok(n) => {
                at += n;
            }
            Err(e) => {
                return Err(e);
            }
        }
    }
    Ok(())
}

pub fn handoff(n: u32) -> Result<(), String> {
    let mut server = make()?;
    let task = spawn(async move {
        for _ in 0..n {
            server.connect().await?;
            let inner = server;
            server = make()?;
            spawn(async move {
                inner.run();
            });
        }
        Ok::<_, String>(())
    });
    Ok(())
}

pub fn keep(flag: bool) {
    let _old;
    if flag {
        _old = make();
    }
}
