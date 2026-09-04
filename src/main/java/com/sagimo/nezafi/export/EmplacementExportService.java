package com.sagimo.nezafi.export;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
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
import java.util.Optional;

/**
 * Export "emplacements" — CSV et rapport PDF équivalent, triés par palier puis par nom, dans
 * l'esprit de la fiche de recensement papier SAGIMO SAS / Groupe Nezafi Capital (une ligne par
 * espace, locataire/activité/loyer quand l'espace est occupé, ligne de total en pied de tableau).
 * Toujours interrogés en base au moment de la demande.
 */
@Service
public class EmplacementExportService {

    private static final DateTimeFormatter DATE_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;

    public EmplacementExportService(EmplacementRepository emplacementRepository, ContratRepository contratRepository) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
    }

    private List<Emplacement> emplacementsTriesParPalier() {
        return emplacementRepository.findAll().stream()
                .sorted(Comparator.<Emplacement>comparingInt(e -> e.getPalier().ordinal())
                        .thenComparing(Emplacement::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Contrat VALIDER et non expiré de l'emplacement, s'il y en a un (au plus un en pratique,
     *  cf. règle métier). Vérifie la dateFin en plus du statut, même raison que partout
     *  ailleurs : un contrat VALIDER simplement périmé ne compte plus comme actif. */
    private Optional<Contrat> contratActif(Emplacement emplacement) {
        LocalDate aujourdHui = LocalDate.now();
        return contratRepository.findByEmplacementId(emplacement.getId()).stream()
                .filter(c -> c.getStatut() == StatutContrat.VALIDER
                        && (c.getDateFin() == null || !c.getDateFin().isBefore(aujourdHui)))
                .findFirst();
    }

    // --- CSV -----------------------------------------------------------------------------

    public byte[] emplacementsCsv() throws IOException {
        List<Emplacement> emplacements = emplacementsTriesParPalier();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8);
        writer.write(0xFEFF);
        writer.println("Palier;Emplacement;Catégorie;Superficie (m²);Statut;Prix affiché (FCFA);"
                + "Locataire actuel;Activité exercée;Téléphone locataire;Loyer pratiqué (FCFA);"
                + "Date début bail;Date fin bail");

        for (Emplacement emplacement : emplacements) {
            Optional<Contrat> contrat = contratActif(emplacement);
            writer.println(String.join(";",
                    csvField(emplacement.getPalier().name()),
                    csvField(emplacement.getName()),
                    csvField(emplacement.getCategorie().name()),
                    emplacement.getSuperficie() != null ? emplacement.getSuperficie().toPlainString() : "",
                    csvField(emplacement.getStatut().name()),
                    emplacement.getPrix() != null ? emplacement.getPrix().toPlainString() : "",
                    csvField(contrat.map(c -> c.getLocataire().getPrenom() + " " + c.getLocataire().getNom()).orElse("")),
                    csvField(contrat.map(Contrat::getActivite).orElse("")),
                    csvField(contrat.map(c -> c.getLocataire().getTelephone()).orElse("")),
                    contrat.map(Contrat::getMontantLoyer).map(BigDecimal::toPlainString).orElse(""),
                    contrat.map(Contrat::getDateDebut).map(d -> d.format(DATE_CSV)).orElse(""),
                    contrat.map(Contrat::getDateFin).map(d -> d.format(DATE_CSV)).orElse("")));
        }
        writer.flush();
        return out.toByteArray();
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

    // --- PDF -----------------------------------------------------------------------------

    public byte[] emplacementsPdf() throws DocumentException {
        List<Emplacement> emplacements = emplacementsTriesParPalier();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = PdfDocumentFactory.nouveauDocument(out,
                "Recensement des emplacements par palier", PageSize.A4.rotate());

        PdfPTable table = new PdfPTable(new float[]{9, 16, 9, 8, 9, 9, 14, 12, 11, 10, 9, 9});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String entete : new String[]{"Palier", "Emplacement", "Catégorie", "Superficie", "Statut",
                "Prix affiché", "Locataire", "Activité", "Téléphone", "Loyer pratiqué", "Début bail", "Fin bail"}) {
            table.addCell(celluleEntete(entete));
        }

        int disponibles = 0;
        int nonDisponibles = 0;
        int enMaintenance = 0;
        BigDecimal totalLoyersPratiques = BigDecimal.ZERO;

        for (Emplacement emplacement : emplacements) {
            Optional<Contrat> contrat = contratActif(emplacement);

            table.addCell(cellule(emplacement.getPalier().getLibelle()));
            table.addCell(cellule(emplacement.getName()));
            table.addCell(cellule(emplacement.getCategorie().name()));
            table.addCell(cellule(emplacement.getSuperficie() != null ? emplacement.getSuperficie() + " m²" : "—"));
            table.addCell(cellule(libelle(emplacement.getStatut().name())));
            table.addCell(cellule(PdfFormat.montant(emplacement.getPrix())));
            table.addCell(cellule(contrat.map(c -> c.getLocataire().getPrenom() + " " + c.getLocataire().getNom()).orElse("—")));
            table.addCell(cellule(contrat.map(c -> PdfFormat.texte(c.getActivite())).orElse("—")));
            table.addCell(cellule(contrat.map(c -> c.getLocataire().getTelephone()).orElse("—")));
            table.addCell(cellule(contrat.map(c -> PdfFormat.montant(c.getMontantLoyer())).orElse("—")));
            table.addCell(cellule(contrat.map(c -> PdfFormat.date(c.getDateDebut())).orElse("—")));
            table.addCell(cellule(contrat.map(c -> PdfFormat.date(c.getDateFin())).orElse("—")));

            switch (emplacement.getStatut()) {
                case DISPONIBLE -> disponibles++;
                case NON_DISPONIBLE -> nonDisponibles++;
                case EN_MAINTENANCE -> enMaintenance++;
            }
            if (contrat.isPresent() && contrat.get().getMontantLoyer() != null) {
                totalLoyersPratiques = totalLoyersPratiques.add(contrat.get().getMontantLoyer());
            }
        }
        document.add(table);

        Paragraph total = new Paragraph(
                emplacements.size() + " emplacement(s) — " + disponibles + " disponible(s), "
                        + nonDisponibles + " non disponible(s), " + enMaintenance + " en maintenance  —  "
                        + "TOTAL LOYERS PRATIQUÉS : " + PdfFormat.montant(totalLoyersPratiques),
                PdfStyle.TOTAL);
        total.setSpacingBefore(10);
        document.add(total);

        document.close();
        return out.toByteArray();
    }

    private String libelle(String nomEnum) {
        String minuscule = nomEnum.replace("_", " ").toLowerCase();
        return minuscule.substring(0, 1).toUpperCase() + minuscule.substring(1);
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
