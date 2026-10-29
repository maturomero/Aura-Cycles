"""ZAP activo con sesion. Playwright recorre la app a traves de ZAP (asi ZAP conoce los formularios reales con su ViewState),
despues ZAP hace spider y escaneo activo con la cookie de esa sesion. Uso: zap_activo.py <email> <carpeta>"""
import json
import os
import ssl
import sys
import time
import urllib.parse
import urllib.request

from playwright.sync_api import sync_playwright

APP = os.environ.get("APP_URL", "https://localhost:8443/repuestos")
ZAP = os.environ.get("ZAP_URL", "http://127.0.0.1:8091")
MINUTOS = os.environ.get("ZAP_MINUTOS", "8")
email, nombre = sys.argv[1], sys.argv[1].split("@")[0]
_directo = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def api(ruta, **params):
    with _directo.open(f"{ZAP}/JSON/{ruta}/?{urllib.parse.urlencode(params)}", timeout=120) as r:
        return json.load(r)


def esperar(ruta, scan_id):
    while int(api(ruta, scanId=scan_id)["status"]) < 100:
        time.sleep(5)


def clic(page, selector):
    if page.locator(selector).count() and page.locator(selector).first.is_enabled():
        page.locator(selector).first.click()
        page.wait_for_load_state()


def recorrer_cliente(page):
    page.goto(f"{APP}/productos.xhtml")
    page.fill("input[type='text']", "cadena")
    clic(page, "input[value='Buscar']")
    page.goto(f"{APP}/producto.xhtml?productoId=3")
    clic(page, "input[value='Comprar']")
    page.goto(f"{APP}/compras.xhtml")
    page.goto(f"{APP}/devolucion.xhtml?ventaId=1")
    if page.locator("select[id$='motivo']").count():
        page.select_option("select[id$='motivo']", "NO_COMPATIBLE")
        page.fill("textarea[id$='coment']", "no entra en mi cuadro")
        clic(page, "input[value='Enviar solicitud']")
    page.goto(f"{APP}/resena.xhtml?productoId=1")
    if page.locator("input[id$='tit']").count():
        page.fill("input[id$='tit']", "Buena")
        page.fill("textarea[id$='txt']", "Anduvo bien en barro y asfalto")
        clic(page, "input[value='Enviar reseña']")
    page.goto(f"{APP}/perfil.xhtml")
    clic(page, "input[value='Guardar cambios']")
    page.fill("input[id$='actual']", "NoEsLaClave1")
    page.fill("input[id$='nueva']", "OtraClave123")
    page.fill("input[id$='rep']", "OtraClave123")
    clic(page, "input[value='Cambiar contraseña']")


def recorrer_moderador(page):
    page.goto(f"{APP}/moderacion.xhtml")
    clic(page, "input[value^='Reseñas pendientes']")
    clic(page, "input[value='Aprobar']")
    clic(page, "input[value^='Devoluciones pendientes']")
    clic(page, "input[value='Rechazar']")


def ingresar(p, proxy=None):
    navegador = p.chromium.launch(proxy={"server": ZAP, "bypass": "<-loopback>"} if proxy else None)
    ctx = navegador.new_context(ignore_https_errors=True)
    page = ctx.new_page()
    page.goto(f"{APP}/login.xhtml")
    page.fill("input[id$='email']", email)
    page.fill("input[id$='pass']", os.environ.get("CLAVE", "Clave1234"))
    clic(page, "input[value='Ingresar']")
    if "login.xhtml" in page.url:
        sys.exit("no pudo loguearse " + email)
    return navegador, ctx, page


def cookie(ctx):
    return next(c["value"] for c in ctx.cookies() if c["name"] == "JSESSIONID")


def viva(sesion):
    pedido = urllib.request.Request(f"{APP}/compras.xhtml", headers={"Cookie": f"JSESSIONID={sesion}"})
    sin_verificar = urllib.request.HTTPSHandler(context=ssl._create_unverified_context())
    with urllib.request.build_opener(urllib.request.ProxyHandler({}), sin_verificar).open(pedido) as r:
        return "Cerrar sesi" in r.read().decode("utf-8", "replace")


def usar_sesion(sesion):
    try:
        api("replacer/action/removeRule", description="sesion")
    except urllib.error.HTTPError:
        pass  # la primera vez no hay regla
    api("replacer/action/addRule", description="sesion", enabled="true", matchType="REQ_HEADER", matchRegex="false",
        matchString="Cookie", replacement=f"JSESSIONID={sesion}", initiators="")


with sync_playwright() as p:
    navegador, ctx, page = ingresar(p, proxy=True)
    (recorrer_moderador if nombre == "mod" else recorrer_cliente)(page)
    sesion = cookie(ctx)
    navegador.close()

# La cookie va en todos los pedidos de ZAP; login y recursos estaticos no se atacan (el login cierra la sesion actual)
usar_sesion(sesion)
for regex in [r".*login\.xhtml.*", r".*j_security_check.*", r".*jakarta\.faces\.resource.*"]:
    api("ascan/action/excludeFromScan", regex=regex)
    api("spider/action/excludeFromScan", regex=regex)
api("ascan/action/setOptionMaxScanDurationInMins", Integer=MINUTOS)
api("ascan/action/setOptionMaxRuleDurationInMins", Integer="1")
api("ascan/action/setOptionThreadPerHost", Integer="4")

inicio = time.time()
esperar("spider/view/status", api("spider/action/scan", url=f"{APP}/", recurse="true", subtreeOnly="true")["scan"])
if not viva(sesion):
    sys.exit("la sesion se cerro durante el spider")
# Si un ataque cierra la sesion, se vuelve a ingresar y ZAP sigue con la nueva; queda dicho que regla la corto
cortes = []
escaneo = api("ascan/action/scan", url=f"{APP}/", recurse="true")["scan"]
while int(api("ascan/view/status", scanId=escaneo)["status"]) < 100:
    time.sleep(10)
    if not viva(sesion):
        progreso = api("ascan/view/scanProgress", scanId=escaneo)["scanProgress"][1]["HostProcess"]
        cortes.append(next((r["Plugin"][0] for r in progreso if r["Plugin"][3] not in ("Complete", "Pending", "Skipped")), "?"))
        with sync_playwright() as p:
            navegador, ctx, _ = ingresar(p)
            sesion = cookie(ctx)
            navegador.close()
        usar_sesion(sesion)
while int(api("pscan/view/recordsToScan")["recordsToScan"]) > 0:
    time.sleep(2)
minutos = (time.time() - inicio) / 60

for plantilla, ext in [("traditional-json", "json"), ("traditional-html", "html")]:
    api("reports/action/generate", title=f"ZAP activo {nombre}", template=plantilla, reportDir="/zap/wrk",
        reportFileName=f"activo-{nombre}.{ext}", sites=APP.split("/repuestos")[0])
print(f"activo-{nombre}: {minutos:.1f} min, {len(api('core/view/urls')['urls'])} URLs, "
      f"sesion renovada {len(cortes)} veces" + (f" (se corto durante: {', '.join(cortes)})" if cortes else ""))
