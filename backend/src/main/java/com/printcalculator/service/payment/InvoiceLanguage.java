package com.printcalculator.service.payment;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public record InvoiceLanguage(String code) {
    public static InvoiceLanguage resolve(String value) {
        String code = value == null ? "it" : value.trim().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return new InvoiceLanguage(switch (code) { case "de", "en", "fr" -> code; default -> "it"; });
    }
    public Locale locale() { return Locale.forLanguageTag(code + "-CH"); }
    public Map<String, String> labels() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.invoice", Locale.forLanguageTag(code));
        return bundle.keySet().stream().collect(Collectors.toUnmodifiableMap(key -> key, bundle::getString));
    }
    public String setting(String kind, String value) {
        return value == null ? null : labels().getOrDefault(kind + "." + value.toLowerCase(Locale.ROOT).replace(" ", "_"), value);
    }
    public String text(String key) { return labels().get(key); }
}
