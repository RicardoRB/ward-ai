def percent(cursor, name):
    cursor.execute("select * from users where name = '%s'" % name)


def concat(cursor, name):
    cursor.execute("select * from users where name = '" + name + "'")


def fstring(cursor, name):
    cursor.execute(f"select * from users where name = '{name}'")


def method(cursor, name):
    cursor.execute("select * from users where name = '{}'".format(name))
