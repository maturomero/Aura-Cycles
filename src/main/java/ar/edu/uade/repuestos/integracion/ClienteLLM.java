package ar.edu.uade.repuestos.integracion;

// El modelo de lenguaje detras del AIGateway. Cualquier falla sale como RuntimeException
public interface ClienteLLM {

    String completar(String instrucciones, String datos);
}
