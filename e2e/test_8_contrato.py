"""R4: el contrato docs/api/openapi.yaml contra la API real. Cada respuesta que da la app levantada
se valida contra el esquema del contrato (codigo, content type, campos obligatorios y tipos).
Si alguien cambia la API y no el contrato, o al reves, este test falla en el CI."""
import json
import pathlib
from urllib.parse import urlsplit

import pytest
from openapi_core import Config, OpenAPI
from openapi_core.testing import MockRequest, MockResponse

from conftest import compose, esperar
from test_7_api import API, pedir, token

# Los errores salen como application/problem+json (RFC 9457): es JSON, se lee igual
CONTRATO = OpenAPI.from_file_path(
    str(pathlib.Path(__file__).resolve().parent.parent / "docs" / "api" / "openapi.yaml"),
    config=Config(extra_media_type_deserializers={"application/problem+json": json.loads}))
_url = urlsplit(API)
HOST = f"{_url.scheme}://{_url.netloc}"


def validar(metodo, ruta, status, headers, cuerpo, pedido_headers=None, pedido_cuerpo=None):
    """Valida contra el contrato la respuesta que ya dio la app."""
    ruta_sola, _, query = ruta.partition("?")
    args = dict(p.split("=", 1) for p in query.split("&")) if query else {}
    pedido = MockRequest(HOST, metodo.lower(), _url.path + ruta_sola, args=args, headers=pedido_headers or {},
                         data=json.dumps(pedido_cuerpo) if pedido_cuerpo is not None else None)
    tipo = (headers.get("Content-Type") or "application/json").split(";")[0]
    respuesta = MockResponse(json.dumps(cuerpo).encode() if cuerpo is not None else b"", status_code=status,
                             headers=dict(headers.items()), content_type=tipo)
    CONTRATO.validate_response(pedido, respuesta)


def llamar(metodo, ruta, tok=None, cuerpo=None, **headers):
    status, resp_headers, datos = pedir(metodo, ruta, tok, cuerpo, **headers)
    pedido_headers = dict(headers)
    if tok:
        pedido_headers["Authorization"] = f"Bearer {tok}"
    validar(metodo, ruta, status, resp_headers, datos, pedido_headers, cuerpo)
    return status, datos


@pytest.fixture(scope="module")
def ana():
    return token("ana@mail.com")


@pytest.fixture(scope="module")
def moderador():
    return token("mod@mail.com")


def test_login_ok_y_login_fallido_cumplen_el_contrato():
    assert llamar("POST", "/auth/login", cuerpo={"email": "ana@mail.com", "password": "Clave1234"})[0] == 200
    assert llamar("POST", "/auth/login", cuerpo={"email": "ana@mail.com", "password": "otra-clave"})[0] == 401


def test_sin_token_el_401_cumple_el_contrato():
    assert llamar("GET", "/devoluciones")[0] == 401


def test_listado_y_detalle_de_devoluciones_cumplen_el_contrato(ana, moderador):
    assert llamar("GET", "/devoluciones", ana)[0] == 200
    status, lista = llamar("GET", "/devoluciones", moderador)
    assert status == 200
    if lista["items"]:
        assert llamar("GET", f"/devoluciones/{lista['items'][0]['id']}", moderador)[0] == 200


def test_filtro_por_estado_cumple_el_contrato(moderador):
    assert llamar("GET", "/devoluciones?estado=PENDIENTE", moderador)[0] == 200


def test_una_devolucion_que_no_existe_da_404_segun_el_contrato(moderador):
    assert llamar("GET", "/devoluciones/999999", moderador)[0] == 404


def test_resenas_de_un_repuesto_cumplen_el_contrato(ana):
    assert llamar("GET", "/productos/1/resenas", ana)[0] == 200


def test_salud_cumple_el_contrato():
    status, cuerpo = llamar("GET", "/salud")
    assert status == 200 and cuerpo == {"estado": "UP", "base": "UP", "broker": "UP"}


# Caso de error de /salud: con el broker apagado responde 503, dice cual componente falla y cumple el contrato.
# Al final se vuelve a prender y se espera a que la app lo vea de nuevo
def test_salud_con_el_broker_apagado_da_503_segun_el_contrato():
    compose("stop", "artemis")
    try:
        status, cuerpo = llamar("GET", "/salud")
        assert status == 503 and cuerpo == {"estado": "DOWN", "base": "UP", "broker": "DOWN"}
    finally:
        compose("start", "artemis")
    assert esperar(lambda: pedir("GET", "/salud")[0] == 200, segundos=90, cada=3)


# Si el contrato y la API se separan, el validador lo detecta: una respuesta inventada que no cumple tiene que fallar
def test_el_validador_detecta_una_respuesta_que_no_cumple():
    with pytest.raises(Exception):
        validar("GET", "/salud", 200, {"Content-Type": "application/json"}, {"estado": "MAS_O_MENOS"})
