package ar.edu.uade.repuestos.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

// La API queda en /repuestos/api/v1. La version va en la URL
@ApplicationPath("/api/v1")
public class ApiRest extends Application {
}
