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


@app.route("/task")
def unrelated_execute(executor, pipeline):
    task = request.args.get("task")
    executor.execute(task)
    pipeline.execute(task)


class Job:
    def __init__(self, command):
        self.command = command

    def start(self):
        self.command.execute(request.args.get("step"))


def not_a_handle(Runner):
    handle = Runner()
    handle.execute(request.args["id"])


def page_by_route(route):
    return db.session.execute(PAGES.select().where(PAGES.c.route == route)).fetchone()


@app.route("/page/<route>")
def page(route):
    return page_by_route(route)


def known_state():
    state = request.args.get("state")
    return db.session.execute(PAGES.select().where(state in ("open", "closed"))).all()
