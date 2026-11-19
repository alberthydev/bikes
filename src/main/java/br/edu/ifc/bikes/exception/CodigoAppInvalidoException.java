package br.edu.ifc.bikes.exception;

/**
 * Codigo do aplicativo invalido, adulterado ou expirado.
 */
public class CodigoAppInvalidoException extends RuntimeException {

    public CodigoAppInvalidoException(String message) {
        super(message);
    }
}
