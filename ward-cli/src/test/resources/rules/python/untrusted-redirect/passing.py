from urllib.parse import urlencode, urlparse

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


def referer_query(request):
    parsed = urlparse(request.headers.get("Referer", ""))
    if parsed.query:
        return "?" + parsed.query
    return ""


def go_with_query(request, token):
    url = reverse("checks", args=[token])
    url += referer_query(request)
    return redirect(url)


def go_with_literal_query():
    tail = request.args.get("tail")
    return redirect("/search?q=" + tail)


def go_with_f_query():
    tail = request.args.get("tail")
    return redirect(f"/search?q={tail}&page=2")


def go_with_chained_query():
    tail = request.args.get("tail")
    more = request.args.get("more")
    return redirect("/search?" + tail + "&" + more)
