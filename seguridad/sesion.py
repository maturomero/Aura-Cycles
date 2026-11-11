"""Imprime el JSESSIONID de una sesion recien logueada (para el escaneo de ZAP con sesion)."""
import os
import sys
import time

from playwright.sync_api import sync_playwright

APP = os.environ.get("APP_URL", "https://localhost:8443/repuestos")

with sync_playwright() as p:
    ctx = p.chromium.launch().new_context(ignore_https_errors=True)
    page = ctx.new_page()
    for intento in range(3):
        page.goto(f"{APP}/login.xhtml")
        page.fill("input[id$='email']", sys.argv[1])
        page.fill("input[id$='pass']", os.environ.get("CLAVE", "Clave1234"))
        with page.expect_navigation(timeout=60_000):
            page.click("input[value='Ingresar']")
        if "login.xhtml" not in page.url:
            break
        time.sleep(5)
    else:
        sys.exit("no pudo loguearse " + sys.argv[1])
    print(next(c["value"] for c in ctx.cookies() if c["name"] == "JSESSIONID"))
