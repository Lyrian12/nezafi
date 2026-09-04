package com.sagimo.nezafi.emplacement;

public enum Palier {
    // Déclaré en premier : l'ordinal pilote le tri par défaut du tableau des emplacements
    // (AdminController.storesPage) et de l'export (EmplacementExportService) — Rez de chaussée
    // doit apparaître avant Palier 1, 2, 3, sans filtre nécessaire pour l'obtenir.
    REZ_DE_CHAUSSEE,
    PALIER_1,
    PALIER_2,
    PALIER_3;

    // Libellé affiché en français, accents compris — un simple replace('_',' ') + capitalize()
    // du nom de la constante (comme le faisaient les vues avant) ne peut pas produire l'accent
    // de "Rez de chaussée" (un nom de constante Java ne peut pas contenir "é"), d'où cette
    // méthode centralisée plutôt qu'une dérivation automatique depuis name().
    public String getLibelle() {
        return switch (this) {
            case REZ_DE_CHAUSSEE -> "Rez de chaussée";
            case PALIER_1 -> "Palier 1";
            case PALIER_2 -> "Palier 2";
            case PALIER_3 -> "Palier 3";
        };
    }
}
