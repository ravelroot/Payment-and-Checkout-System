package dev.velo.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * CSV no formato que o Excel em português abre direto: UTF-8 com BOM, separador ";" e vírgula decimal.
 */
public final class Csv {

    public static final int MAX_ROWS = 5000;
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final MediaType MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final char SEPARATOR = ';';
    private static final String BOM = "﻿";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StringBuilder out = new StringBuilder(BOM);

    public Csv row(String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(SEPARATOR);
            }
            out.append(escape(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    public byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return out.toString();
    }

    /**
     * Texto livre (nomes, e-mails): neutraliza fórmulas para o Excel não executar conteúdo digitado por usuários.
     */
    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char c = value.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' ? "'" + value : value;
    }

    public static String money(BigDecimal value) {
        return value == null ? "" : Money.of(value).toPlainString().replace('.', ',');
    }

    public static String number(long value) {
        return Long.toString(value);
    }

    public static String dateTime(LocalDateTime utc) {
        return utc == null ? "" : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).format(DATE_TIME);
    }

    public static String yesNo(boolean value) {
        return value ? "Sim" : "Não";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.indexOf(SEPARATOR) >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return quote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    /**
     * Resposta de download: {@code attachment; filename="<prefixo>-AAAA-MM-DD.csv"}.
     */
    public ResponseEntity<byte[]> download(String prefix, Clock clock) {
        String filename = prefix + "-" + LocalDate.now(clock.withZone(ZONE)) + ".csv";
        return ResponseEntity.ok()
                .contentType(MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes());
    }
}
