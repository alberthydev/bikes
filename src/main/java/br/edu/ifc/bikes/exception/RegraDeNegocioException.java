package br.edu.ifc.bikes.exception;

/**
 * Violacao de uma regra de negocio: conflito com o estado atual de um registro.
 * Vira HTTP 409 com a mensagem enviada.
 */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String message) {
        super(message);
    }
}
