use std::process::Command;

fn main() {
    let program = std::env::args().nth(1).unwrap();
    Command::new(program).status().unwrap();
    let arg = std::env::var("EXTRA").unwrap();
    let mut shell = Command::new("sh");
    shell.arg("-c").arg(arg);
    shell.status().unwrap();
}

fn forward(value: String) {
    spawn(value);
}

fn spawn(text: String) {
    Command::new("echo").args([text]).status().unwrap();
}

fn entry() {
    forward(std::env::args().nth(2).unwrap());
}
