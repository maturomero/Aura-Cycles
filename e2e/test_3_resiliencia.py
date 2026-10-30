"""Eje C: se apaga el broker durante la demo y se vuelve a levantar."""
from conftest import aprobar, comprar, compose, esperar, pedir_devolucion, sql, ultima_venta

JAVIER = 4


def test_broker_apagado_no_deja_nada_a_medias_y_se_recupera_solo(abrir):
    javier = abrir("javier@mail.com")
    comprar(javier, 8)  # sillin: tiene garantia (camaras y pastillas no)
    javier.click("text=Pagar (aprobado)")
    javier.wait_for_load_state()
    codigo = pedir_devolucion(javier, ultima_venta(JAVIER)[0], "MEDIDA_INCORRECTA", "")
    dev = int(codigo[4:])

    mod = abrir("mod@mail.com")
    compose("stop", "artemis")
    try:
        aprobar(mod, codigo)
        assert "la mensajer" in mod.content()
        assert sql(f"select estado from devolucion where id={dev}") == "PENDIENTE"
    finally:
        compose("start", "artemis")

    def aprobada():
        if sql(f"select estado from devolucion where id={dev}") == "PENDIENTE":
            aprobar(mod, codigo)
        return sql(f"select estado from devolucion where id={dev}") != "PENDIENTE"

    assert esperar(aprobada, 60, 5)
    assert esperar(lambda: sql(f"select estado from devolucion where id={dev}") == "REEMBOLSADA", 40)
