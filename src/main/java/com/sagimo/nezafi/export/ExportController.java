package com.sagimo.nezafi.export;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.openpdf.text.DocumentException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

/**
 * Exports admin (CSV et PDF) des contrats et des emplacements. Toujours générés à la demande,
 * requête base de données comprise — rien n'est mis en cache ni pré-calculé : chaque appel
 * reflète l'état exact de la base au moment de la demande.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    private final ContratRepository contratRepository;
    private final ContratExportService contratExportService;
    private final EmplacementExportService emplacementExportService;

    public ExportController(ContratRepository contratRepository, ContratExportService contratExportService,
                             EmplacementExportService emplacementExportService) {
        this.contratRepository = contratRepository;
        this.contratExportService = contratExportService;
        this.emplacementExportService = emplacementExportService;
    }

    private void interdireLaMiseEnCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }

    // --- Contrats --------------------------------------------------------------------------

    @GetMapping("/contracts/export")
    public void exporterContratsCsv(HttpServletResponse response) throws IOException {
        interdireLaMiseEnCache(response);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"contrats_actifs.csv\"");
        response.getOutputStream().write(contratExportService.contratsActifsCsv());
    }

    @GetMapping("/contracts/export/pdf")
    public void exporterContratsPdf(HttpServletResponse response) throws IOException {
        interdireLaMiseEnCache(response);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"contrats_actifs.pdf\"");
        try {
            response.getOutputStream().write(contratExportService.contratsActifsPdf());
        } catch (DocumentException e) {
            throw new IOException("Impossible de générer le rapport PDF des contrats", e);
        }
    }

    @GetMapping("/contracts/{id}/pdf")
    public void exporterContratPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        interdireLaMiseEnCache(response);
        response.setContentType("application/pdf");
        // Inline : le PDF s'ouvre dans l'onglet pour relecture avant sauvegarde/impression manuelle.
        response.setHeader("Content-Disposition", "inline; filename=\"contrat_" + id + ".pdf\"");
        try {
            response.getOutputStream().write(contratExportService.contratPdf(contrat));
        } catch (DocumentException e) {
            throw new IOException("Impossible de générer la fiche PDF du contrat", e);
        }
    }

    // --- Emplacements ----------------------------------------------------------------------

    @GetMapping("/stores/export")
    public void exporterEmplacementsCsv(HttpServletResponse response) throws IOException {
        interdireLaMiseEnCache(response);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"emplacements_par_palier.csv\"");
        response.getOutputStream().write(emplacementExportService.emplacementsCsv());
    }

    @GetMapping("/stores/export/pdf")
    public void exporterEmplacementsPdf(HttpServletResponse response) throws IOException {
        interdireLaMiseEnCache(response);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"emplacements_par_palier.pdf\"");
        try {
            response.getOutputStream().write(emplacementExportService.emplacementsPdf());
        } catch (DocumentException e) {
            throw new IOException("Impossible de générer le rapport PDF des emplacements", e);
        }
    }
}
