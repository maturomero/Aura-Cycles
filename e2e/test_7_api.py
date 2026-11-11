"""API REST /api/v1: JWT y roles, 409 del State, Idempotency-Key, ETag y Problem Details. Todo contra el sistema levantado."""
import base64
import hashlib
import hmac
import json
import ssl
import urllib.error
import urllib.request
import uuid

from conftest import APP, CLAVE, comprar, esperar, modo_soap, sql, ultima_venta

API = f"{APP}/api/v1"
ANA, CARLOS = 1, 2

_ctx = ssl.create_default_context()
_ctx.check_hostname = False
_ctx.verify_mode = ssl.CERT_NONE
_http = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=_ctx))


def pedir(metodo, ruta, token=None, cuerpo=None, **headers):
    """Devuelve (status, headers, json o None)."""
    datos = None
    if cuerpo is not None:
        datos = cuerpo.encode() if isinstance(cuerpo, str) else json.dumps(cuerpo).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    pedido = urllib.request.Request(f"{API}{ruta}", data=datos, method=metodo, headers=headers)
    try:
        r = _http.open(pedido, timeout=20)
    except urllib.error.HTTPError as e:
        r = e
    texto = r.read().decode("utf-8", "replace")
    return r.status, r.headers, (json.loads(texto) if texto else None)


def token(email):
    st, _, cuerpo = pedir("POST", "/auth/login", cuerpo={"email": email, "password": CLAVE})
    assert st == 200, cuerpo
    return cuerpo["token"]


def es_problema(headers, cuerpo, status, clave):
    assert headers["Content-Type"].startswith("application/problem+json")
    assert cuerpo["status"] == status and cuerpo["type"] == f"urn:auracycles:problema:{clave}", cuerpo
    assert cuerpo["title"] and cuerpo["detail"] and cuerpo["instance"].startswith("/repuestos/api/v1/")


def _compra_pagada(page, usuario_id, producto_id):
    comprar(page, producto_id)
    page.click("text=Pagar (aprobado)")
    page.wait_for_load_state()
    return int(ultima_venta(usuario_id)[0])


def _b64(texto):
    datos = texto if isinstance(texto, bytes) else texto.encode()
    return base64.urlsafe_b64encode(datos).decode().rstrip("=")


# Un JWT HS256 valido en todo salvo en que la clave de firma no es la del servidor
def _firmado_con_otra_clave(datos):
    cuerpo = _b64(json.dumps({"alg": "HS256"})) + "." + _b64(json.dumps(datos))
    firma = hmac.new(uuid.uuid4().hex.encode(), cuerpo.encode(), hashlib.sha256).digest()
    return cuerpo + "." + _b64(firma)


# ---------- JWT ----------

def test_login_devuelve_un_jwt_y_con_el_se_entra():
    st, h, cuerpo = pedir("POST", "/auth/login", cuerpo={"email": "ana@mail.com", "password": CLAVE})
    assert st == 200 and cuerpo["tipo"] == "Bearer" and cuerpo["rol"] == "CLIENTE" and cuerpo["expiraEn"] == 900
    assert h["Cache-Control"] == "no-store"
    assert len(cuerpo["token"].split(".")) == 3
    st, _, lista = pedir("GET", "/devoluciones", cuerpo["token"])
    assert st == 200 and "items" in lista


def test_sin_token_o_con_un_jwt_invalido_es_401():
    bueno = token("ana@mail.com")
    cabecera, payload, firma = bueno.split(".")
    datos = json.loads(base64.urlsafe_b64decode(payload + "=="))
    datos["rol"] = "MODERADOR"
    invalidos = {
        "sin token": None,
        "basura": "esto-no-es-un-jwt",
        "firma cambiada": f"{cabecera}.{payload}.{firma[:-4]}AAAA",
        "payload adulterado (rol MODERADOR) con la firma vieja": f"{cabecera}.{_b64(json.dumps(datos))}.{firma}",
        "alg none": f"{_b64(json.dumps({'alg': 'none'}))}.{_b64(json.dumps(datos))}.",
        "bien armado pero firmado con otra clave": _firmado_con_otra_clave(datos),
    }
    for caso, t in invalidos.items():
        st, h, cuerpo = pedir("GET", "/devoluciones", t)
        assert st == 401, caso
        es_problema(h, cuerpo, 401, "no-autenticado")
        assert h["WWW-Authenticate"] == "Bearer", caso
    # y con otro esquema tampoco
    st, _, _ = pedir("GET", "/devoluciones", Authorization="Basic YW5hQG1haWwuY29tOkNsYXZlMTIzNA==")
    assert st == 401


def test_login_con_clave_mala_es_un_401_generico():
    st, h, cuerpo = pedir("POST", "/auth/login", cuerpo={"email": "javier@mail.com", "password": "no-es"})
    assert st == 401
    es_problema(h, cuerpo, 401, "credenciales")
    assert "token" not in cuerpo
    token("javier@mail.com")  # entrar bien limpia el contador de fallos de esta IP


# ---------- Roles (BFLA) ----------

def test_bfla_el_cliente_no_resuelve_y_el_moderador_no_solicita():
    st, h, cuerpo = pedir("PATCH", "/devoluciones/1", token("ana@mail.com"), {"estado": "APROBADA"}, **{"If-Match": '"x"'})
    assert st == 403
    es_problema(h, cuerpo, 403, "sin-permiso")
    st, h, cuerpo = pedir("POST", "/devoluciones", token("mod@mail.com"), {"ventaId": 1, "motivo": "ARREPENTIMIENTO"},
                          **{"Idempotency-Key": "bfla-" + uuid.uuid4().hex})
    assert st == 403


# ---------- Flujo completo: Idempotency-Key, ETag, BOLA y el 409 del State ----------

def test_flujo_completo_por_la_api(abrir):
    carlos_web = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos_web, CARLOS, 5)
    carlos, ana, mod = token("carlos@mail.com"), token("ana@mail.com"), token("mod@mail.com")
    clave = "e2e-" + uuid.uuid4().hex
    pedido = {"ventaId": venta, "motivo": "NO_COMPATIBLE", "comentario": "No entra en mi cuadro"}

    # Sin Idempotency-Key no se crea nada
    st, h, cuerpo = pedir("POST", "/devoluciones", carlos, pedido)
    assert st == 400
    es_problema(h, cuerpo, 400, "pedido-invalido")

    # BOLA al crear: Ana no puede pedir la devolucion de una compra de Carlos, y no se entera de que existe
    st, h, cuerpo = pedir("POST", "/devoluciones", ana, pedido, **{"Idempotency-Key": "ajena-" + uuid.uuid4().hex})
    assert st == 404
    es_problema(h, cuerpo, 404, "no-existe")

    # Crear: 201 con Location y ETag
    st, h, creada = pedir("POST", "/devoluciones", carlos, pedido, **{"Idempotency-Key": clave})
    assert st == 201, creada
    dev = creada["id"]
    assert h["Location"].endswith(f"/repuestos/api/v1/devoluciones/{dev}")
    assert creada["estado"] == "PENDIENTE" and creada["rma"].startswith("RMA-") and h["ETag"]

    # El mismo pedido con la misma clave: la misma devolucion, no otra
    st, h, repetida = pedir("POST", "/devoluciones", carlos, pedido, **{"Idempotency-Key": clave})
    assert st == 201 and repetida["id"] == dev and h["Idempotent-Replayed"] == "true"
    assert sql(f"select count(*) from devolucion where venta_id={venta}") == "1"

    # La misma clave con otro contenido: 409
    st, h, cuerpo = pedir("POST", "/devoluciones", carlos, {**pedido, "motivo": "DEFECTUOSO"}, **{"Idempotency-Key": clave})
    assert st == 409
    es_problema(h, cuerpo, 409, "clave-repetida")

    # GET con ETag; con If-None-Match vigente, 304
    st, h, vista = pedir("GET", f"/devoluciones/{dev}", carlos)
    etag = h["ETag"]
    assert st == 200 and vista["codigo"] == f"DEV-{dev:06d}"
    assert "carlos@mail.com" not in json.dumps(vista)
    st, _, sin_cuerpo = pedir("GET", f"/devoluciones/{dev}", carlos, **{"If-None-Match": etag})
    assert st == 304 and sin_cuerpo is None

    # BOLA al leer: para Ana esa devolucion no existe; el listado de Ana no la trae
    st, h, cuerpo = pedir("GET", f"/devoluciones/{dev}", ana)
    assert st == 404
    es_problema(h, cuerpo, 404, "no-existe")
    assert pedir("GET", "/devoluciones/99999999", ana)[2]["detail"] == cuerpo["detail"]
    assert dev not in [d["id"] for d in pedir("GET", "/devoluciones", ana)[2]["items"]]
    assert dev in [d["id"] for d in pedir("GET", "/devoluciones", carlos)[2]["items"]]
    assert dev in [d["id"] for d in pedir("GET", "/devoluciones?estado=PENDIENTE", mod)[2]["items"]]

    # PATCH sin If-Match: 428. Con un ETag viejo: 412
    st, h, cuerpo = pedir("PATCH", f"/devoluciones/{dev}", mod, {"estado": "APROBADA"})
    assert st == 428
    es_problema(h, cuerpo, 428, "falta-if-match")
    st, h, cuerpo = pedir("PATCH", f"/devoluciones/{dev}", mod, {"estado": "APROBADA"}, **{"If-Match": '"0000000000000000"'})
    assert st == 412 and h["ETag"] == etag
    es_problema(h, cuerpo, 412, "etag-viejo")
    assert sql(f"select estado from devolucion where id={dev}") == "PENDIENTE"

    # Aprobar con el ETag vigente: 200, ETag nuevo y el topico hace el resto
    st, h, aprobada = pedir("PATCH", f"/devoluciones/{dev}", mod, {"estado": "APROBADA"}, **{"If-Match": etag})
    assert st == 200 and aprobada["estado"] == "APROBADA" and h["ETag"] != etag
    assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA", 30)

    # El ETag de antes ya no sirve
    st, _, _ = pedir("PATCH", f"/devoluciones/{dev}", mod, {"estado": "RECHAZADA"}, **{"If-Match": h["ETag"]})
    assert st == 412

    # 409: el State no deja rechazar una devolucion que ya esta reembolsada
    etag_final = pedir("GET", f"/devoluciones/{dev}", mod)[1]["ETag"]
    for estado in ("RECHAZADA", "APROBADA"):
        st, h, cuerpo = pedir("PATCH", f"/devoluciones/{dev}", mod, {"estado": estado}, **{"If-Match": etag_final})
        assert st == 409, estado
        es_problema(h, cuerpo, 409, "transicion-invalida")
        assert "REEMBOLSADA" in cuerpo["detail"]
    assert sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA"


# Con el SOAP del proveedor caido la API no espera: 202 y la garantia queda en la cola
def test_con_el_proveedor_caido_la_api_responde_202(abrir):
    carlos_web = abrir("carlos@mail.com")
    venta = _compra_pagada(carlos_web, CARLOS, 8)
    modo_soap("error")
    st, h, cuerpo = pedir("POST", "/devoluciones", token("carlos@mail.com"), {"ventaId": venta, "motivo": "ARREPENTIMIENTO"},
                          **{"Idempotency-Key": "e2e-" + uuid.uuid4().hex})
    assert st == 202, cuerpo
    assert cuerpo["estado"] == "PENDIENTE_VALIDACION" and cuerpo["rma"] is None and h["Retry-After"] == "120"


# ---------- Entradas hostiles: siempre Problem Details, nunca un 500 ni un stack trace ----------

def test_inyecciones_y_pedidos_mal_armados_no_rompen_nada():
    ana = token("ana@mail.com")
    clave = lambda: {"Idempotency-Key": "iny-" + uuid.uuid4().hex}
    casos = [
        ("POST", "/devoluciones", '{"ventaId": "1 OR 1=1", "motivo": "ARREPENTIMIENTO"}', clave(), 400),
        ("POST", "/devoluciones", '{"ventaId": 1, "motivo": "ARREPENTIMIENTO\' OR \'1\'=\'1"}', clave(), 400),
        ("POST", "/devoluciones", '{"ventaId": 1, "motivo"', clave(), 400),
        ("POST", "/devoluciones", '<?xml version="1.0"?><!DOCTYPE a [<!ENTITY x SYSTEM "file:///etc/passwd">]><a>&x;</a>', clave(), 400),
        ("POST", "/devoluciones", '{"ventaId": 1, "motivo": "ARREPENTIMIENTO"}', {"Idempotency-Key": "x' OR '1'='1"}, 400),
        ("GET", "/devoluciones?estado=PENDIENTE'%20OR%20'1'='1", None, {}, 400),
        ("GET", "/devoluciones/1%20OR%201=1", None, {}, 404),
        ("GET", "/devoluciones/abc", None, {}, 404),
        ("GET", "/productos/1%27/resenas", None, {}, 404),
        ("POST", "/productos/1/resenas", '{"puntaje": 99, "texto": "x"}', {}, 400),
        ("DELETE", "/devoluciones/1", None, {}, 405),
    ]
    for metodo, ruta, cuerpo, headers, esperado in casos:
        st, h, respuesta = pedir(metodo, ruta, ana, cuerpo, **headers)
        assert st == esperado, (metodo, ruta, st, respuesta)
        assert h["Content-Type"].startswith("application/problem+json"), ruta
        texto = json.dumps(respuesta)
        assert "Exception" not in texto and "SQL" not in texto and "at org." not in texto and "root:" not in texto


# Un XSS guardado en una resena sale por la API como texto, escapado en el JSON, y queda pendiente de moderacion
def test_resenas_por_la_api():
    ana = token("ana@mail.com")
    st, _, lista = pedir("GET", "/productos/1/resenas", ana)
    assert st == 200 and lista["total"] >= 1 and all("estado" not in r for r in lista["items"])
    st, _, nueva = pedir("POST", "/productos/1/resenas", ana, {"puntaje": 4, "titulo": "<script>alert(1)</script>", "texto": "Agarra bien en tierra."})
    assert st == 201 and nueva["estado"] == "PENDIENTE" and nueva["autor"] == "Ana Torres"
    assert nueva["id"] not in [r["id"] for r in pedir("GET", "/productos/1/resenas", ana)[2]["items"]]
    # No compro el producto 5: regla de negocio, 422
    st, h, cuerpo = pedir("POST", "/productos/5/resenas", ana, {"puntaje": 5, "texto": "No lo compre"})
    assert st == 422
    es_problema(h, cuerpo, 422, "regla-de-negocio")
    st, h, cuerpo = pedir("GET", "/productos/9999/resenas", ana)
    assert st == 404
