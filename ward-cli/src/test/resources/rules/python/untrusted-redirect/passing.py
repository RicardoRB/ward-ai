from flask import redirect, request, url_for


def go():
    return redirect(url_for("home"))


def go_known():
    page = request.args.get("page")
    return redirect("/pages/" + str(int(page)))
