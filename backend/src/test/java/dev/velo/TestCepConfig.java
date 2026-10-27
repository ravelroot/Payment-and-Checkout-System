package dev.velo;

import dev.velo.shipping.CepAddress;
import dev.velo.shipping.CepLookup;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Substitui o ViaCEP nos testes: nenhum teste chama a rede.
 */
@TestConfiguration
public class TestCepConfig {

    public static final String KNOWN = "22240003";
    public static final String OFFLINE = "11111111";

    @Bean
    @Primary
    public CepLookup testCepLookup() {
        return digits -> switch (digits) {
            case KNOWN -> Optional.of(new CepAddress("22240-003", "Rua Pereira da Silva", "Laranjeiras", "Rio de Janeiro", "RJ"));
            case OFFLINE -> throw new IllegalStateException("ViaCEP fora do ar");
            default -> Optional.empty();
        };
    }
}
