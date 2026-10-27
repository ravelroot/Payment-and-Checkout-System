package dev.velo.shipping;

import java.util.Optional;

/**
 * Consulta de CEP. Devolve vazio quando o CEP não existe e lança {@link RuntimeException} quando o serviço não responde.
 */
public interface CepLookup {

    Optional<CepAddress> find(String digits);
}
