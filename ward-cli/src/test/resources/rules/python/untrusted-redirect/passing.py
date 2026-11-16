from urllib.parse import urlencode

from django.urls import reverse
from flask import redirect, request, url_for


def go():
    return redirect(url_for("home"))


def go_known():
    page = request.args.get("page")
    return redirect("/pages/" + str(int(page)))


def go_to_partner():
    back = request.args.get("back")
    return redirect("https://partner.example/start?" + urlencode({"back": back}))


def go_to_step():
    step = request.args.get("step")
    return redirect(reverse("wizard", query={"step": step}))
