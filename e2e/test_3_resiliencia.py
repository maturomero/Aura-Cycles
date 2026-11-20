"""Eje C: se apaga el broker durante la demo y se vuelve a levantar."""
from playwright.sync_api import Error as ErrorDelNavegador

from conftest import aprobar, comprar, compose, esperar, modo_mp, pedir_devolucion, sql, ultima_venta

JAVIER = 4


def _aprobar(page, codigo):
    # Al apagar o prender el broker Docker cambia la red y Chrome puede cortar la navegacion (ERR_NETWORK_CHANGED)
    try:
        aprobar(page, codigo)
    except ErrorDelNavegador:
        aprobar(page, codigo)


def test_broker_apagado_no_deja_nada_a_medias_y_se_recupera_solo(abrir):
    javier = abrir("javier@mail.com")
    comprar(javier, 8)  # sillin: tiene garantia (camaras y pastillas no)
    javier.click("#pagar")
    javier.wait_for_load_state()
    codigo = pedir_devolucion(javier, ultima_venta(JAVIER)[0], "MEDIDA_INCORRECTA", "")
    dev = int(codigo[4:])

    mod = abrir("mod@mail.com")
    compose("stop", "artemis")
    try:
        _aprobar(mod, codigo)
        assert "la mensajer" in mod.content()
        assert sql(f"select estado from devolucion where id={dev}") == "PENDIENTE"
    finally:
        compose("start", "artemis")

    def aprobada():
        if sql(f"select estado from devolucion where id={dev}") == "PENDIENTE":
            try:
                aprobar(mod, codigo)
            except ErrorDelNavegador:
                pass  # se vuelve a mirar la base y, si sigue pendiente, se reintenta en 5 s
        return sql(f"select estado from devolucion where id={dev}") != "PENDIENTE"

    assert esperar(aprobada, 60, 5)
    assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA", 40)


def test_un_mensaje_pendiente_sobrevive_al_reinicio_del_broker(abrir):
    javier = abrir("javier@mail.com")
    comprar(javier, 8)
    javier.click("#pagar")
    javier.wait_for_load_state()
    codigo = pedir_devolucion(javier, ultima_venta(JAVIER)[0], "MEDIDA_INCORRECTA", "")
    dev = int(codigo[4:])

    # Mercado Pago caido: la devolucion queda aprobada y el reembolso queda esperando en el broker
    mod = abrir("mod@mail.com")
    modo_mp("error")
    try:
        _aprobar(mod, codigo)
        assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "APROBADA", 20)
        compose("stop", "artemis")
    finally:
        modo_mp("normal")
        compose("start", "artemis")

    # El mensaje no se perdio con el broker apagado: al volver se reentrega y el reembolso sale una sola vez
    assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA", 120, 2)
    assert sql(f"select stock_repuesto, resolucion_avisada from devolucion where id={dev}") == "1\t1"
