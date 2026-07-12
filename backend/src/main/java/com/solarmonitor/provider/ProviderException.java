package com.solarmonitor.provider;

/**
 * Falha de coleta em um provider (comunicação, autenticação, protocolo ou
 * resposta malformada). Sempre carrega contexto suficiente para diagnóstico
 * no log — o scheduler nunca deixa essa exceção derrubar o ciclo de polling.
 */
public class ProviderException extends Exception {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
