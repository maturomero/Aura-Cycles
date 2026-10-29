package ar.edu.uade.repuestos.presentacion;

import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

// Vista vencida (sesion vencida o ViewState invalido): vuelve a mostrar la pagina con un aviso en vez de un 500
public class ManejadorDeVistaVencida extends ExceptionHandlerWrapper {

    public ManejadorDeVistaVencida(ExceptionHandler original) {
        super(original);
    }

    @Override
    public void handle() {
        Iterator<ExceptionQueuedEvent> eventos = getUnhandledExceptionQueuedEvents().iterator();
        while (eventos.hasNext()) {
            ViewExpiredException vencida = buscar(eventos.next().getContext().getException());
            if (vencida != null) {
                eventos.remove();
                volverA(Objects.requireNonNullElse(vencida.getViewId(), "/login.xhtml"));
            }
        }
        getWrapped().handle();
    }

    private static ViewExpiredException buscar(Throwable t) {
        while (t != null && !(t instanceof ViewExpiredException)) {
            t = t.getCause();
        }
        return (ViewExpiredException) t;
    }

    private static void volverA(String vista) {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        Mensajes.sobrevivirAlRedirect();
        Mensajes.error("La página venció por inactividad. Volvé a intentar.");
        try {
            ec.redirect(ec.getRequestContextPath() + vista);
        } catch (IOException e) {
            throw new FacesException(e);
        }
        fc.responseComplete();
    }

    public static class Fabrica extends ExceptionHandlerFactory {

        public Fabrica(ExceptionHandlerFactory original) {
            super(original);
        }

        @Override
        public ExceptionHandler getExceptionHandler() {
            return new ManejadorDeVistaVencida(getWrapped().getExceptionHandler());
        }
    }
}
