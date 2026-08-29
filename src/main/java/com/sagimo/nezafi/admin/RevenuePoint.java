package com.sagimo.nezafi.admin;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Un point du graphique de tendance des encaissements sur le tableau de bord.
 * {@code x}/{@code y} sont déjà normalisés (repère SVG 0..100 / 0..40) et
 * {@code label} déjà formaté (ex: "Août") par {@link AdminDashboardController},
 * pour un rendu sans aucun calcul côté template.
 */
public record RevenuePoint(YearMonth mois, String label, BigDecimal total, double x, double y) {
}
