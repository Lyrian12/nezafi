package com.sagimo.nezafi.export;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Construit le squelette (en-tête société + titre + pied de page numéroté) commun à tous les
 * documents PDF admin, dans le style "maison" repris de la fiche de recensement papier
 * SAGIMO SAS / Groupe Nezafi Capital.
 */
final class PdfDocumentFactory {

    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private PdfDocumentFactory() {
    }

    /** Ouvre un document A4 portrait avec l'en-tête maison déjà posé ; reste à ajouter le contenu. */
    static Document nouveauDocument(ByteArrayOutputStream out, String titre) throws DocumentException {
        return nouveauDocument(out, titre, org.openpdf.text.PageSize.A4);
    }

    static Document nouveauDocument(ByteArrayOutputStream out, String titre, Rectangle taille) throws DocumentException {
        Document document = new Document(taille, 36, 36, 54, 46);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new PiedDePage());
        document.open();

        document.add(new Paragraph("SAGIMO SAS  —  GROUPE NEZAFI CAPITAL", PdfStyle.ENTETE_SOCIETE));

        Paragraph titreParagraphe = new Paragraph(titre, PdfStyle.TITRE_DOCUMENT);
        titreParagraphe.setSpacingBefore(4);
        document.add(titreParagraphe);

        Paragraph genere = new Paragraph("Généré le " + LocalDateTime.now().format(DATE_HEURE), PdfStyle.META);
        genere.setSpacingAfter(14);
        document.add(genere);

        return document;
    }

    /** Numérotation de page + mention de bas de page, répétée sur chaque page du document. */
    private static class PiedDePage extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte contenu = writer.getDirectContent();
            Phrase pied = new Phrase(
                    "Document généré automatiquement — SAGIMO SAS / Groupe Nezafi Capital — Yaoundé, Cameroun  |  Page "
                            + writer.getPageNumber(), PdfStyle.PIED_DE_PAGE);
            ColumnText.showTextAligned(contenu, Element.ALIGN_CENTER, pied,
                    document.getPageSize().getWidth() / 2, document.bottomMargin() - 18, 0);
        }
    }
}
