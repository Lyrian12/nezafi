package com.sagimo.nezafi.export;

import org.openpdf.text.Font;

import java.awt.Color;

/**
 * Charte graphique commune à tous les documents PDF générés par l'admin (contrat
 * individuel, rapport des contrats, recensement des emplacements par palier) —
 * inspirée de la fiche de recensement papier déjà utilisée par SAGIMO SAS / Groupe
 * Nezafi Capital (en-tête société, tableaux sobres, ligne de total, pied de page).
 */
final class PdfStyle {

    static final Color GRIS_FONCE = new Color(60, 60, 60);
    static final Color GRIS = new Color(120, 120, 120);
    static final Color GRIS_CLAIR = new Color(240, 240, 240);
    static final Color ACCENT = new Color(46, 111, 64); // vert, écho du lime Nezafi en version imprimable/N&B

    static final Font ENTETE_SOCIETE = new Font(Font.HELVETICA, 9, Font.BOLD, GRIS_FONCE);
    static final Font TITRE_DOCUMENT = new Font(Font.HELVETICA, 15, Font.BOLD, Color.BLACK);
    static final Font META = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS);
    static final Font SECTION = new Font(Font.HELVETICA, 10, Font.BOLD, ACCENT);
    static final Font LABEL = new Font(Font.HELVETICA, 7, Font.BOLD, GRIS);
    static final Font VALEUR = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    static final Font TABLE_ENTETE = new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE);
    static final Font TABLE_CELLULE = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.BLACK);
    static final Font TOTAL = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    static final Font PIED_DE_PAGE = new Font(Font.HELVETICA, 6, Font.ITALIC, GRIS);

    private PdfStyle() {
    }
}
