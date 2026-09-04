package com.sagimo.nezafi.export;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.StatutContrat;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Exports "contrats" — CSV comptable et rapport PDF équivalent (mêmes données), plus le PDF
 * individuel d'un contrat. Toujours interrogés en base au moment de la demande : aucune donnée
 * mise en cache ou pré-générée.
 *
 * Style des PDF inspiré de la fiche de recensement papier SAGIMO SAS / Groupe Nezafi Capital
 * (en-tête société, tableau sobre, ligne de total, pied de page).
 */
@Service
public class ContratExportService {

    private static final DateTimeFormatter DATE_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContratRepository contratRepository;

    public ContratExportService(ContratRepository contratRepository) {
        this.contratRepository = contratRepository;
    }

    /** Contrats VALIDER et non expirés — vérifie la dateFin en plus du statut : un contrat
     *  VALIDER simplement périmé (recalcul paresseux pas encore passé, cf.
     *  ContratStatusService.verifierExpiration) ne doit pas apparaître dans un rapport de
     *  contrats "actifs". */
    private List<Contrat> contratsVraimentActifs() {
        LocalDate aujourdHui = LocalDate.now();
        return contratRepository.findByStatut(StatutContrat.VALIDER).stream()
                .filter(c -> c.getDateFin() == null || !c.getDateFin().isBefore(aujourdHui))
                .toList();
    }

    // --- CSV -----------------------------------------------------------------------------

    /** CSV des contrats VALIDER, interrogé en base à l'instant de l'appel. */
    public byte[] contratsActifsCsv() throws IOException {
        List<Contrat> contratsActifs = contratsVraimentActifs();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8);
        writer.write(0xFEFF); // BOM UTF-8 pour un affichage correct des accents dans Excel
        writer.println("Emplacement;Locataire;Enseigne;Loyer (FCFA);Date de début;Date de fin");
        for (Contrat contrat : contratsActifs) {
            writer.println(String.join(";",
                    csvField(contrat.getEmplacement().getName()),
                    csvField(contrat.getLocataire().getPrenom() + " " + contrat.getLocataire().getNom()),
                    csvField(contrat.getNomEnseigne()),
                    contrat.getMontantLoyer() != null ? contrat.getMontantLoyer().toPlainString() : "",
                    csvDate(contrat.getDateDebut()),
                    csvDate(contrat.getDateFin())));
        }
        writer.flush();
        return out.toByteArray();
    }

    /** Date de bail facultative (cf. Contrat.dateDebut / dateFin) : champ vide plutôt qu'une NPE. */
    private String csvDate(LocalDate valeur) {
        return valeur == null ? "" : valeur.format(DATE_CSV);
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // --- PDF : rapport liste ---------------------------------------------------------------

    /** Rapport PDF des contrats VALIDER — mêmes données que {@link #contratsActifsCsv()}. */
    public byte[] contratsActifsPdf() throws DocumentException {
        List<Contrat> contratsActifs = contratsVraimentActifs().stream()
                .sorted(Comparator.comparing(c -> c.getEmplacement().getName()))
                .toList();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = PdfDocumentFactory.nouveauDocument(out, "Rapport des contrats actifs", PageSize.A4.rotate());

        PdfPTable table = new PdfPTable(new float[]{22, 22, 18, 14, 12, 12});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String entete : new String[]{"Emplacement", "Locataire", "Enseigne", "Loyer (FCFA)", "Date de début", "Date de fin"}) {
            table.addCell(celluleEntete(entete));
        }

        BigDecimal totalLoyers = BigDecimal.ZERO;
        for (Contrat contrat : contratsActifs) {
            table.addCell(cellule(contrat.getEmplacement().getName()));
            table.addCell(cellule(contrat.getLocataire().getPrenom() + " " + contrat.getLocataire().getNom()));
            table.addCell(cellule(PdfFormat.texte(contrat.getNomEnseigne())));
            table.addCell(cellule(PdfFormat.montant(contrat.getMontantLoyer())));
            table.addCell(cellule(PdfFormat.date(contrat.getDateDebut())));
            table.addCell(cellule(PdfFormat.date(contrat.getDateFin())));
            if (contrat.getMontantLoyer() != null) {
                totalLoyers = totalLoyers.add(contrat.getMontantLoyer());
            }
        }
        document.add(table);

        Paragraph total = new Paragraph(
                contratsActifs.size() + " contrat(s) actif(s)  —  TOTAL LOYERS MENSUELS : " + PdfFormat.montant(totalLoyers),
                PdfStyle.TOTAL);
        total.setSpacingBefore(10);
        document.add(total);

        document.close();
        return out.toByteArray();
    }

    // --- PDF : contrat individuel ------------------------------------------------------------

    /** Fiche PDF d'un contrat individuel : identification, enseigne/activité, durée, finances. */
    public byte[] contratPdf(Contrat contrat) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = PdfDocumentFactory.nouveauDocument(out,
                "Fiche contrat n°" + contrat.getId() + " — " + contrat.getEmplacement().getName());

        ajouterSection(document, "Emplacement");
        PdfPTable emplacement = tableauCles(2);
        ligne(emplacement, "Nom", contrat.getEmplacement().getName());
        ligne(emplacement, "Palier", contrat.getEmplacement().getPalier().getLibelle());
        ligne(emplacement, "Catégorie", contrat.getEmplacement().getCategorie().name());
        ligne(emplacement, "Superficie", PdfFormat.montant(contrat.getEmplacement().getSuperficie()).replace(" FCFA", " m²"));
        document.add(emplacement);

        ajouterSection(document, "Locataire");
        PdfPTable locataire = tableauCles(2);
        ligne(locataire, "Nom", contrat.getLocataire().getPrenom() + " " + contrat.getLocataire().getNom());
        ligne(locataire, "Téléphone", contrat.getLocataire().getTelephone());
        ligne(locataire, "Email", PdfFormat.texte(contrat.getLocataire().getEmail()));
        document.add(locataire);

        ajouterSection(document, "Enseigne & activité");
        PdfPTable enseigne = tableauCles(2);
        ligne(enseigne, "Nom d'enseigne", PdfFormat.texte(contrat.getNomEnseigne()));
        ligne(enseigne, "Activité", PdfFormat.texte(contrat.getActivite()));
        document.add(enseigne);

        ajouterSection(document, "Durée & statut");
        PdfPTable duree = tableauCles(2);
        ligne(duree, "Date de début", PdfFormat.date(contrat.getDateDebut()));
        ligne(duree, "Date de fin", PdfFormat.date(contrat.getDateFin()));
        ligne(duree, "Statut", contrat.getStatut().name());
        document.add(duree);

        ajouterSection(document, "Loyer & caution");
        PdfPTable finances = tableauCles(2);
        ligne(finances, "Montant du loyer", PdfFormat.montant(contrat.getMontantLoyer())
                + " (" + contrat.getDureeLoyerMois() + " mois couverts)");
        ligne(finances, "Montant de la caution", contrat.getMontantCaution() == null
                ? "Non renseignée"
                : PdfFormat.montant(contrat.getMontantCaution()) + " (" + contrat.getDureeCautionMois() + " mois couverts)");
        document.add(finances);

        document.close();
        return out.toByteArray();
    }

    // --- Petits constructeurs de cellules/lignes partagés -----------------------------------

    private void ajouterSection(Document document, String titre) throws DocumentException {
        Paragraph section = new Paragraph(titre, PdfStyle.SECTION);
        section.setSpacingBefore(10);
        section.setSpacingAfter(4);
        document.add(section);
    }

    private PdfPTable tableauCles(int colonnes) {
        PdfPTable table = new PdfPTable(colonnes);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{28, 72});
        } catch (DocumentException ignored) {
            // Largeurs par défaut si jamais le nombre de colonnes ne correspond pas.
        }
        return table;
    }

    private void ligne(PdfPTable table, String label, String valeur) {
        PdfPCell celluleLabel = new PdfPCell(new Phrase(label, PdfStyle.LABEL));
        celluleLabel.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        celluleLabel.setPaddingBottom(6);
        table.addCell(celluleLabel);

        PdfPCell celluleValeur = new PdfPCell(new Phrase(valeur, PdfStyle.VALEUR));
        celluleValeur.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        celluleValeur.setPaddingBottom(6);
        table.addCell(celluleValeur);
    }

    private PdfPCell celluleEntete(String texte) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, PdfStyle.TABLE_ENTETE));
        cellule.setBackgroundColor(PdfStyle.ACCENT);
        cellule.setPadding(5);
        cellule.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cellule;
    }

    private PdfPCell cellule(String texte) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, PdfStyle.TABLE_CELLULE));
        cellule.setPadding(4);
        return cellule;
    }
}
