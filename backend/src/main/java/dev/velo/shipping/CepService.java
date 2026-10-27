package dev.velo.shipping;

import dev.velo.common.ApiException;
import dev.velo.common.Texts;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CepService {

    static final int CACHE_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(CepService.class);

    private final CepLookup lookup;
    private final Map<String, CepAddress> cache = new ConcurrentHashMap<>();

    public CepService(CepLookup lookup) {
        this.lookup = lookup;
    }

    public CepAddress find(String cep) {
        String digits = digits(cep);
        CepAddress cached = cache.get(digits);
        if (cached != null) {
            return cached;
        }
        Optional<CepAddress> found;
        try {
            found = lookup.find(digits);
        } catch (RuntimeException e) {
            log.warn("CEP lookup failed for {}: {}", digits, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CEP_UNAVAILABLE",
                    "Não foi possível consultar o CEP agora. Preencha o endereço manualmente.");
        }
        CepAddress address = found.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CEP_NOT_FOUND", "CEP não encontrado."));
        if (cache.size() >= CACHE_SIZE) {
            Iterator<String> it = cache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        cache.put(digits, address);
        return address;
    }

    /**
     * Os 8 dígitos do CEP; aceita "22240-003", "22240003" ou "22.240-003".
     */
    public static String digits(String cep) {
        String d = Texts.digits(cep);
        if (cep == null || !cep.trim().matches("[0-9.\\-\\s]+") || d.length() != 8) {
            throw ApiException.badRequest("CEP_INVALID", "Informe um CEP com 8 dígitos.");
        }
        return d;
    }

    public static String format(String digits) {
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }
}
