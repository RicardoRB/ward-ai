import sqlite3
from flask import Flask, request

app = Flask(__name__)


@app.route("/user")
def by_name(conn):
    name = request.args.get("name")
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE name = ?", (name,))
    return cursor.fetchall()


@app.route("/item")
def by_id(conn):
    item = int(request.args.get("id"))
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM items WHERE id = " + str(item))
    return cursor.fetchall()


def constant(cursor):
    cursor.execute("SELECT 1")
