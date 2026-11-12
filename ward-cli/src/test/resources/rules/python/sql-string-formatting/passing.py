def bound(cursor, name):
    cursor.execute("select * from users where name = %s", (name,))


def constant(cursor):
    cursor.execute("select count(*) from users")


def plain(cursor):
    cursor.execute(f"select count(*) from users")
