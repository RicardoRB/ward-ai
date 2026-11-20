fn find(conn: &Connection) {
    let id = std::env::args().nth(1).unwrap().parse::<i64>().unwrap();
    conn.execute("SELECT * FROM users WHERE id = ?1", [id]).unwrap();
}

fn all(client: &mut Client) {
    let sql = format!("SELECT * FROM {}", "users");
    client.query(&sql, &[]).unwrap();
}
