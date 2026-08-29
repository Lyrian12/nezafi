package com.sagimo.nezafi.export;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatage partagé par les documents PDF — même style que les vues Thymeleaf (séparateur de
 *  milliers par espace, virgule décimale, dates JJ-MM-AAAA). */
final class PdfFormat {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final NumberFormat MONTANT;

    static {
        MONTANT = NumberFormat.getNumberInstance(Locale.FRANCE);
        MONTANT.setMinimumFractionDigits(2);
        MONTANT.setMaximumFractionDigits(2);
    }

    private PdfFormat() {
    }

    static String montant(BigDecimal valeur) {
        return valeur == null ? "—" : MONTANT.format(valeur) + " FCFA";
    }

    static String date(LocalDate valeur) {
        return valeur == null ? "—" : valeur.format(DATE);
    }

    static String texte(String valeur) {
        return (valeur == null || valeur.isBlank()) ? "—" : valeur;
    }
}
