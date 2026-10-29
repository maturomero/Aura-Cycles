"""Pruebas de punta a punta contra el sistema levantado con docker compose.

Correr: docker compose up -d --build  y despues  pytest e2e
"""
import os
import re
import subprocess
import time
import urllib.request

import pytest
from playwright.sync_api import sync_playwright

APP = os.environ.get("APP_URL", "https://localhost:8443/repuestos")
MP = os.environ.get("MP_MOCK_URL", "http://localhost:8089")
SOAP = os.environ.get("SOAP_MOCK_URL", "http://localhost:8090")
PROYECTO = os.environ.get("COMPOSE_PROJECT", "")
CLAVE = "Clave1234"

_sin_proxy = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def compose(*args):
    cmd = ["docker", "compose"] + (["-p", PROYECTO] if PROYECTO else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True)


def sql(consulta):
    r = compose("exec", "-T", "mysql", "mysql", "-uaura", "-paura-local", "-N", "-B", "repuestos_bici", "-e", consulta)
    return r.stdout.strip()


def modo_mp(modo):
    _sin_proxy.open(f"{MP}/__modo?valor={modo}").read()


def modo_soap(modo):
    _sin_proxy.open(f"{SOAP}/__modo?valor={modo}").read()


def esperar(condicion, segundos=30, cada=1.0):
    fin = time.time() + segundos
    while time.time() < fin:
        if condicion():
            return True
        time.sleep(cada)
    return False


@pytest.fixture(scope="session")
def navegador():
    with sync_playwright() as p:
        b = p.chromium.launch()
        yield b
        b.close()


@pytest.fixture
def abrir(navegador):
    """Devuelve una funcion que abre una sesion nueva logueada como el usuario pedido."""
    contextos = []

    def _abrir(email=None):
        ctx = navegador.new_context(ignore_https_errors=True)
        contextos.append(ctx)
        page = ctx.new_page()
        if email:
            login(page, email)
        return page

    yield _abrir
    for c in contextos:
        c.close()


@pytest.fixture(autouse=True)
def externos_normales():
    modo_mp("normal")
    modo_soap("normal")
    yield
    modo_mp("normal")
    modo_soap("normal")


def login(page, email, clave=CLAVE):
    page.goto(f"{APP}/login.xhtml")
    page.fill("input[id$='email']", email)
    page.fill("input[id$='pass']", clave)
    page.click("input[value='Ingresar']")
    page.wait_for_load_state()


def comprar(page, producto_id):
    page.goto(f"{APP}/producto.xhtml?productoId={producto_id}")
    page.click("input[value='Comprar']")
    page.wait_for_load_state()


def ultima_venta(usuario_id):
    fila = sql(f"select id, estado, ifnull(mp_pago_id,'-') from venta where usuario_id={usuario_id} order by id desc limit 1")
    return fila.split("\t")


def stock(producto_id):
    return int(sql(f"select stock from producto where id={producto_id}"))


def pedir_devolucion(page, venta_id, motivo, comentario="se rompio al primer uso"):
    page.goto(f"{APP}/devolucion.xhtml?ventaId={venta_id}")
    page.select_option("select[id$='motivo']", motivo)
    page.fill("textarea[id$='coment']", comentario)
    page.click("input[value='Enviar solicitud']")
    page.wait_for_load_state()
    m = re.search(r"DEV-\d{6}", page.url)
    return m.group(0) if m else None


def aprobar(page, codigo):
    page.goto(f"{APP}/moderacion.xhtml")
    page.click("input[value^='Devoluciones pendientes']")
    page.wait_for_load_state()
    page.locator("tr", has_text=codigo).locator("input[value='Aprobar']").click()
    page.wait_for_load_state()
