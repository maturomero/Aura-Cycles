package ar.edu.uade.repuestos.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.uade.repuestos.datos.DevolucionRepository;
import ar.edu.uade.repuestos.datos.PedidoIdempotenteRepository;
import ar.edu.uade.repuestos.modelo.Devolucion;
import ar.edu.uade.repuestos.modelo.EstadoDevolucion;
import ar.edu.uade.repuestos.modelo.EstadoVenta;
import ar.edu.uade.repuestos.modelo.MotivoDevolucion;
import ar.edu.uade.repuestos.modelo.PedidoIdempotente;
import ar.edu.uade.repuestos.negocio.ServicioDeSolicitudes.Solicitud;
import ar.edu.uade.repuestos.util.ClaveRepetidaException;
import ar.edu.uade.repuestos.util.NoExisteException;
import ar.edu.uade.repuestos.util.ReglaNegocioException;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// Lo que usa la API REST: crear una sola vez aunque el cliente reintente (Idempotency-Key) y no mostrar lo ajeno (BOLA)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServicioDeSolicitudesTest {

    private static final String CLAVE = "pedido-0001";

    @Mock DevolucionRepository devoluciones;
    @Mock PedidoIdempotenteRepository pedidos;
    @Mock Instance<ServicioDeDevoluciones> borradores;
    @Mock ServicioDeDevoluciones borrador;
    @InjectMocks ServicioDeSolicitudes servicio;

    private Devolucion devolucion;

    @BeforeEach
    void preparar() {
        devolucion = Datos.devolucion(12L, Datos.venta(9L, Datos.ana(), 1, EstadoVenta.PAGADA), MotivoDevolucion.MEDIDA_INCORRECTA);
        devolucion.garantiaValidada("RMA-2026-00012");
        when(borradores.get()).thenReturn(borrador);
        when(borrador.confirmar()).thenReturn(devolucion);
        when(pedidos.porClave(anyString(), anyString())).thenReturn(Optional.empty());
        when(devoluciones.porId(12L)).thenReturn(Optional.of(devolucion));
    }

    @Test
    void laPrimeraVezCreaLaDevolucionYGuardaLaClave() {
        Solicitud s = servicio.solicitar("ana@mail.com", CLAVE, 9L, MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica");

        assertSame(devolucion, s.devolucion());
        assertEquals(201, s.codigoHttp());
        assertFalse(s.repetida());

        // Los mismos tres pasos que la pantalla, con el email del token
        InOrder pasos = inOrder(borrador, pedidos, borradores);
        pasos.verify(borrador).seleccionarCompraDe("ana@mail.com", 9L);
        pasos.verify(borrador).cargarMotivo(MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica");
        pasos.verify(borrador).confirmar();
        ArgumentCaptor<PedidoIdempotente> guardado = ArgumentCaptor.forClass(PedidoIdempotente.class);
        pasos.verify(pedidos).guardar(guardado.capture());
        pasos.verify(borradores).destroy(borrador);

        assertEquals("ana@mail.com", guardado.getValue().getEmail());
        assertEquals(CLAVE, guardado.getValue().getClave());
        assertEquals(12L, guardado.getValue().getDevolucionId());
        assertEquals(201, guardado.getValue().getCodigoHttp());
        assertEquals(64, guardado.getValue().getHuella().length());
    }

    // El proveedor no contesto: queda en la cola de validaciones y la API responde 202
    @Test
    void siQuedaEsperandoAlProveedorEs202() {
        Devolucion enCola = Datos.devolucion(13L, devolucion.getVenta(), MotivoDevolucion.MEDIDA_INCORRECTA);
        when(borrador.confirmar()).thenReturn(enCola);
        Solicitud s = servicio.solicitar("ana@mail.com", CLAVE, 9L, MotivoDevolucion.MEDIDA_INCORRECTA, null);
        assertEquals(EstadoDevolucion.PENDIENTE_VALIDACION, s.devolucion().getEstado());
        assertEquals(202, s.codigoHttp());
    }

    // El cliente no recibio la respuesta y reintenta: misma devolucion, mismo codigo, nada nuevo
    @Test
    void elReintentoConLaMismaClaveNoCreaOtra() {
        String huella = ServicioDeSolicitudes.huella(9L, MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica");
        when(pedidos.porClave("ana@mail.com", CLAVE)).thenReturn(Optional.of(new PedidoIdempotente("ana@mail.com", CLAVE, huella, 12L, 202)));

        Solicitud s = servicio.solicitar("ana@mail.com", CLAVE, 9L, MotivoDevolucion.MEDIDA_INCORRECTA, " Me quedo chica ");

        assertSame(devolucion, s.devolucion());
        assertEquals(202, s.codigoHttp());
        assertTrue(s.repetida());
        verifyNoInteractions(borradores, borrador);
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void laMismaClaveConOtroContenidoEsUnConflicto() {
        String huella = ServicioDeSolicitudes.huella(9L, MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica");
        when(pedidos.porClave("ana@mail.com", CLAVE)).thenReturn(Optional.of(new PedidoIdempotente("ana@mail.com", CLAVE, huella, 12L, 201)));

        assertThrows(ClaveRepetidaException.class,
                () -> servicio.solicitar("ana@mail.com", CLAVE, 9L, MotivoDevolucion.DEFECTUOSO, "Me quedo chica"));
        assertThrows(ClaveRepetidaException.class,
                () -> servicio.solicitar("ana@mail.com", CLAVE, 10L, MotivoDevolucion.MEDIDA_INCORRECTA, "Me quedo chica"));
        verifyNoInteractions(borradores);
    }

    // La clave es por usuario: la de Ana no le sirve ni le molesta a Carlos
    @Test
    void laClaveSeBuscaPorUsuario() {
        servicio.solicitar("carlos@mail.com", CLAVE, 9L, MotivoDevolucion.ARREPENTIMIENTO, null);
        verify(pedidos).porClave("carlos@mail.com", CLAVE);
    }

    // Si una regla falla no queda la clave guardada y el borrador se descarta igual
    @Test
    void siLaReglaFallaNoSeGuardaLaClave() {
        doThrow(new NoExisteException("La compra no existe.")).when(borrador).seleccionarCompraDe("carlos@mail.com", 9L);
        assertThrows(NoExisteException.class, () -> servicio.solicitar("carlos@mail.com", CLAVE, 9L, MotivoDevolucion.ARREPENTIMIENTO, null));
        doThrow(new ReglaNegocioException("Elegi un motivo.")).when(borrador).cargarMotivo(any(), any());
        assertThrows(ReglaNegocioException.class, () -> servicio.solicitar("ana@mail.com", CLAVE, 9L, null, null));
        verify(pedidos, never()).guardar(any());
        verify(borrador, never()).confirmar();
        verify(borradores, org.mockito.Mockito.times(2)).destroy(borrador);
    }

    @Test
    void laHuellaDependeDeLaCompraElMotivoYElComentario() {
        String base = ServicioDeSolicitudes.huella(9L, MotivoDevolucion.DEFECTUOSO, "Vino rota");
        assertEquals(base, ServicioDeSolicitudes.huella(9L, MotivoDevolucion.DEFECTUOSO, "  Vino rota "));
        assertNotEquals(base, ServicioDeSolicitudes.huella(10L, MotivoDevolucion.DEFECTUOSO, "Vino rota"));
        assertNotEquals(base, ServicioDeSolicitudes.huella(9L, MotivoDevolucion.ARREPENTIMIENTO, "Vino rota"));
        assertNotEquals(base, ServicioDeSolicitudes.huella(9L, MotivoDevolucion.DEFECTUOSO, "Vino rota."));
        assertEquals(ServicioDeSolicitudes.huella(9L, MotivoDevolucion.DEFECTUOSO, null), ServicioDeSolicitudes.huella(9L, MotivoDevolucion.DEFECTUOSO, " "));
        assertTrue(base.matches("[0-9a-f]{64}"));
    }

    // ---------- BOLA ----------

    @Test
    void elDuenioVeSuDevolucion() {
        assertSame(devolucion, servicio.ver(12L, "ana@mail.com", false));
    }

    @Test
    void unaAjenaDaElMismoErrorQueUnaQueNoExiste() {
        when(devoluciones.porId(99L)).thenReturn(Optional.empty());
        String ajena = assertThrows(NoExisteException.class, () -> servicio.ver(12L, "carlos@mail.com", false)).getMessage();
        String inexistente = assertThrows(NoExisteException.class, () -> servicio.ver(99L, "carlos@mail.com", false)).getMessage();
        assertEquals(inexistente, ajena);
    }

    @Test
    void elModeradorVeCualquieraPeroNoLasQueNoExisten() {
        when(devoluciones.porId(99L)).thenReturn(Optional.empty());
        assertSame(devolucion, servicio.ver(12L, "mod@mail.com", true));
        assertThrows(NoExisteException.class, () -> servicio.ver(99L, "mod@mail.com", true));
    }

    @Test
    void elClienteListaSoloLasSuyasYPuedeFiltrarPorEstado() {
        Devolucion rechazada = Datos.devolucion(14L, devolucion.getVenta(), MotivoDevolucion.ARREPENTIMIENTO);
        rechazada.garantiaRechazada();
        when(devoluciones.porUsuario(1L)).thenReturn(List.of(devolucion, rechazada));

        assertEquals(List.of(devolucion, rechazada), servicio.delCliente(1L, null));
        assertEquals(List.of(rechazada), servicio.delCliente(1L, EstadoDevolucion.RECHAZADA));
        assertEquals(List.of(), servicio.delCliente(1L, EstadoDevolucion.REEMBOLSADA));
        verify(devoluciones, never()).porEstado(any());
    }

    @Test
    void laColaDelModeradorSonLasPendientesSiNoPideOtroEstado() {
        when(devoluciones.porEstado(EstadoDevolucion.PENDIENTE)).thenReturn(List.of(devolucion));
        when(devoluciones.porEstado(EstadoDevolucion.APROBADA)).thenReturn(List.of());
        assertEquals(List.of(devolucion), servicio.paraModerar(null));
        assertEquals(List.of(), servicio.paraModerar(EstadoDevolucion.APROBADA));
        verify(devoluciones, never()).porUsuario(anyLong());
    }

    @Test
    void siLaDevolucionDeUnaClaveVistaYaNoEstaEsNoExiste() {
        String huella = ServicioDeSolicitudes.huella(9L, MotivoDevolucion.MEDIDA_INCORRECTA, null);
        when(pedidos.porClave("ana@mail.com", CLAVE)).thenReturn(Optional.of(new PedidoIdempotente("ana@mail.com", CLAVE, huella, 77L, 201)));
        when(devoluciones.porId(77L)).thenReturn(Optional.empty());
        assertThrows(NoExisteException.class, () -> servicio.solicitar("ana@mail.com", CLAVE, 9L, MotivoDevolucion.MEDIDA_INCORRECTA, null));
    }
}
