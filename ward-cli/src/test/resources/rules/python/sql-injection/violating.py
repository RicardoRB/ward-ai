import sqlite3
from flask import Flask, request

app = Flask(__name__)


@app.route("/user")
def by_name(conn):
    name = request.args.get("name")
    query = "SELECT * FROM users WHERE name = '" + name + "'"
    return conn.cursor().execute(query).fetchall()


@app.route("/order")
def by_order(conn):
    column = request.form["order"]
    cursor = conn.cursor()
    cursor.execute(f"SELECT * FROM orders ORDER BY {column}")
    return cursor.fetchall()


def direct(cursor):
    cursor.execute("SELECT * FROM t WHERE id = " + request.args["id"])


def django_cursor(connection):
    with connection.cursor() as cursor:
        cursor.execute("SELECT * FROM t WHERE x = '%s'" % request.args["x"])


class Store:
    def __init__(self, db):
        self.db = db

    def run_script(self):
        self.db.executescript(request.form["script"])


def manager(User):
    return User.objects.raw("SELECT * FROM users WHERE name = '" + request.args["name"] + "'")
