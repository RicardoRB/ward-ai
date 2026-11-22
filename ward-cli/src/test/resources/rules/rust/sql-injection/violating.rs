fn find(conn: &Connection) {
    let id = std::env::args().nth(1).unwrap();
    let sql = format!("SELECT * FROM users WHERE id = {}", id);
    conn.execute(&sql, []).unwrap();
}

fn count(client: &mut Client) {
    let table = std::env::var("TABLE").unwrap();
    let text = format!("SELECT count(*) FROM {table}");
    client.query(&text, &[]).unwrap();
}
