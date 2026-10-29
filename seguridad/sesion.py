"""Imprime el JSESSIONID de una sesion recien logueada (para el escaneo de ZAP con sesion)."""
import os
import sys

from playwright.sync_api import sync_playwright

APP = os.environ.get("APP_URL", "https://localhost:8443/repuestos")

with sync_playwright() as p:
    ctx = p.chromium.launch().new_context(ignore_https_errors=True)
    page = ctx.new_page()
    page.goto(f"{APP}/login.xhtml")
    page.fill("input[id$='email']", sys.argv[1])
    page.fill("input[id$='pass']", os.environ.get("CLAVE", "Clave1234"))
    page.click("input[value='Ingresar']")
    page.wait_for_load_state()
    if "login.xhtml" in page.url:
        sys.exit("no pudo loguearse " + sys.argv[1])
    print(next(c["value"] for c in ctx.cookies() if c["name"] == "JSESSIONID"))
