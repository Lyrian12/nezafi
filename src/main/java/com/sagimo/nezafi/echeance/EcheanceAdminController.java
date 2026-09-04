package com.sagimo.nezafi.echeance;

import com.sagimo.nezafi.admin.AdminAlertService;
import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.ContratStatusService;
import com.sagimo.nezafi.paiement.Paiement;
import com.sagimo.nezafi.paiement.PaiementRepository;
import com.sagimo.nezafi.storage.DocumentJoint;
import com.sagimo.nezafi.storage.DocumentJointService;
import com.sagimo.nezafi.storage.TypeDocumentJoint;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vues et actions admin pour les échéances et paiements. Volontairement séparé
 * d'{@link com.sagimo.nezafi.admin.AdminController} : module en cours de test,
 * plus simple à retirer proprement si la branche est abandonnée.
 */
@Controller
@RequestMapping("/admin")
public class EcheanceAdminController {

    // Même logique que AdminController : lecture ouverte à ADMIN/SECRETARIAT/COMPTABLE,
    // écriture réservée à ADMIN/SECRETARIAT (COMPTABLE ne modifie jamais rien).
    private static final String LECTURE_STAFF = "hasAnyRole('ADMIN','SECRETARIAT','COMPTABLE')";
    private static final String EDITION_STAFF = "hasAnyRole('ADMIN','SECRETARIAT')";

    private final EcheanceRepository echeanceRepository;
    private final PaiementRepository paiementRepository;
    private final ContratRepository contratRepository;
    private final UserRepository userRepository;
    private final EcheanceStatusService echeanceStatusService;
    private final ContratStatusService contratStatusService;
    private final AuditService auditService;
    private final AdminAlertService adminAlertService;
    private final DocumentJointService documentJointService;

    public EcheanceAdminController(EcheanceRepository echeanceRepository, PaiementRepository paiementRepository,
                                    ContratRepository contratRepository, UserRepository userRepository,
                                    EcheanceStatusService echeanceStatusService, ContratStatusService contratStatusService,
                                    AuditService auditService, AdminAlertService adminAlertService,
                                    DocumentJointService documentJointService) {
        this.echeanceRepository = echeanceRepository;
        this.paiementRepository = paiementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.echeanceStatusService = echeanceStatusService;
        this.contratStatusService = contratStatusService;
        this.auditService = auditService;
        this.adminAlertService = adminAlertService;
        this.documentJointService = documentJointService;
    }

    private User currentAdmin(Authentication authentication) {
        String identifier = authentication.getName();
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElseThrow(() -> new RuntimeException("Admin not found"));
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize(LECTURE_STAFF)
    public String contractDetail(@PathVariable Long id, Model model) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        // Rattrape un contrat VALIDER dont la dateFin est simplement dépassée, sans qu'on y
        // touche — le statut affiché en en-tête doit refléter EXPIRE, pas rester sur VALIDER.
        contratStatusService.verifierExpiration(contrat);

        List<Echeance> echeances = echeanceRepository.findByContratId(id);
        echeanceStatusService.rafraichirStatuts(echeances);
        echeances.sort(Comparator.comparing(Echeance::getDateEcheance));

        Map<Long, List<Paiement>> paiementsParEcheance = new HashMap<>();
        Map<Long, BigDecimal> totalPayeParEcheance = new HashMap<>();
        Map<Long, Boolean> recuParPaiement = new HashMap<>();
        for (Echeance echeance : echeances) {
            List<Paiement> paiements = paiementRepository.findByEcheanceId(echeance.getId());
            paiementsParEcheance.put(echeance.getId(), paiements);
            for (Paiement paiement : paiements) {
                recuParPaiement.put(paiement.getId(), !documentJointService.lister("Paiement", paiement.getId()).isEmpty());
            }
            totalPayeParEcheance.put(echeance.getId(), echeanceStatusService.totalPaye(echeance.getId()));
        }

        model.addAttribute("contrat", contrat);
        model.addAttribute("echeances", echeances);
        model.addAttribute("paiementsParEcheance", paiementsParEcheance);
        model.addAttribute("totalPayeParEcheance", totalPayeParEcheance);
        model.addAttribute("recuParPaiement", recuParPaiement);
        // Alerte non bloquante, contextuelle à cette fiche : écart significatif entre le
        // total des échéances LOYER et le montantLoyer déclaré sur le contrat.
        model.addAttribute("ecartLoyerSignificatif", adminAlertService.ecartLoyerSignificatif(contrat));
        documentJointService.premier("Contrat", id, TypeDocumentJoint.FACTURE.name())
                .ifPresent(facture -> model.addAttribute("factureNomAffiche", documentJointService.nomOriginal(facture)));
        documentJointService.premier("Contrat", id, TypeDocumentJoint.CONTRAT_SCANNE.name())
                .ifPresent(scan -> model.addAttribute("scanNomAffiche", documentJointService.nomOriginal(scan)));
        return "admin-contract-detail";
    }

    @PostMapping("/contracts/{id}/facture")
    @PreAuthorize(EDITION_STAFF)
    public String uploaderFacturePaiement(@PathVariable Long id, @RequestParam MultipartFile facture,
                                           Authentication authentication, RedirectAttributes redirectAttributes)
            throws IOException {
        return uploaderDocumentContrat(id, facture, TypeDocumentJoint.FACTURE, "La facture",
                authentication, redirectAttributes);
    }

    @GetMapping("/contracts/{id}/facture")
    @PreAuthorize(LECTURE_STAFF)
    public void telechargerFacturePaiement(@PathVariable Long id,
                                            @RequestParam(required = false, defaultValue = "false") boolean apercu,
                                            HttpServletResponse response) throws IOException {
        telechargerDocumentContrat(id, TypeDocumentJoint.FACTURE, apercu, response);
    }

    @PostMapping("/contracts/{id}/scan")
    @PreAuthorize(EDITION_STAFF)
    public String uploaderScanContrat(@PathVariable Long id, @RequestParam MultipartFile scan,
                                       Authentication authentication, RedirectAttributes redirectAttributes)
            throws IOException {
        return uploaderDocumentContrat(id, scan, TypeDocumentJoint.CONTRAT_SCANNE, "Le scan du contrat",
                authentication, redirectAttributes);
    }

    @GetMapping("/contracts/{id}/scan")
    @PreAuthorize(LECTURE_STAFF)
    public void telechargerScanContrat(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "false") boolean apercu,
                                        HttpServletResponse response) throws IOException {
        telechargerDocumentContrat(id, TypeDocumentJoint.CONTRAT_SCANNE, apercu, response);
    }

    /** Logique commune aux deux catégories de document de contrat (facture, scan) — seul le
     *  type et le libellé d'erreur changent selon l'appelant. */
    private String uploaderDocumentContrat(Long id, MultipartFile fichier, TypeDocumentJoint typeDocument,
                                            String libelle, Authentication authentication,
                                            RedirectAttributes redirectAttributes) throws IOException {
        contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (fichier.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Aucun fichier sélectionné.");
            return "redirect:/admin/contracts/" + id;
        }
        String type = fichier.getContentType();
        boolean typeAccepte = type != null && (type.equals(MediaType.APPLICATION_PDF_VALUE) || type.startsWith("image/"));
        if (!typeAccepte) {
            redirectAttributes.addFlashAttribute("error", libelle + " doit être un PDF ou une image.");
            return "redirect:/admin/contracts/" + id;
        }

        boolean remplacement = !documentJointService.lister("Contrat", id, typeDocument.name()).isEmpty();
        DocumentJoint document = documentJointService.remplacerUnique("Contrat", id, typeDocument.name(), fichier, "contrats");

        User admin = currentAdmin(authentication);
        auditService.enregistrer(admin, remplacement ? TypeActionAudit.MODIFICATION : TypeActionAudit.CREATION,
                typeDocument == TypeDocumentJoint.FACTURE ? "FactureContrat" : "ScanContrat", id, null,
                Map.of("fichier", documentJointService.nomOriginal(document)));

        return "redirect:/admin/contracts/" + id;
    }

    /** Sert un document de contrat en téléchargement (Content-Disposition: attachment, défaut —
     *  comportement historique, ne casse aucun lien "Télécharger" déjà en place) ou en aperçu
     *  (?apercu=true → Content-Disposition: inline, le navigateur affiche au lieu de forcer
     *  l'enregistrement). */
    private void telechargerDocumentContrat(Long id, TypeDocumentJoint typeDocument, boolean apercu,
                                             HttpServletResponse response) throws IOException {
        Optional<DocumentJoint> document = documentJointService.premier("Contrat", id, typeDocument.name());
        if (document.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        ecrireDocument(response, document.get(), apercu);
    }

    /** Écrit un DocumentJoint en réponse HTTP — factorisé pour que facture/scan/reçu servent
     *  le même en-tête Content-Disposition (inline vs attachment) de la même façon. */
    private void ecrireDocument(HttpServletResponse response, DocumentJoint document, boolean apercu) throws IOException {
        Resource ressource = documentJointService.charger(document);
        String nomAffiche = documentJointService.nomOriginal(document);
        MediaType type = MediaTypeFactory.getMediaType(ressource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        response.setContentType(type.toString());
        response.setHeader("Content-Disposition", (apercu ? "inline" : "attachment") + "; filename*=UTF-8''"
                + URLEncoder.encode(nomAffiche, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(ressource.getContentAsByteArray());
    }

    @PostMapping("/echeances/{id}/paiements")
    @PreAuthorize(EDITION_STAFF)
    public String enregistrerPaiement(
            @PathVariable Long id,
            @RequestParam BigDecimal montantPaye,
            @RequestParam String datePaiement,
            @RequestParam(required = false) MultipartFile recu,
            Authentication authentication,
            RedirectAttributes redirectAttributes) throws IOException {

        Echeance echeance = echeanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Echeance not found"));

        String identifier = authentication.getName();
        User admin = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        boolean recuFourni = recu != null && !recu.isEmpty();
        if (recuFourni) {
            String type = recu.getContentType();
            boolean typeAccepte = type != null && (type.equals(MediaType.APPLICATION_PDF_VALUE) || type.startsWith("image/"));
            if (!typeAccepte) {
                redirectAttributes.addFlashAttribute("error", "Le reçu doit être un PDF ou une image.");
                return "redirect:/admin/contracts/" + echeance.getContrat().getId();
            }
        }

        Paiement paiement = new Paiement();
        paiement.setEcheance(echeance);
        paiement.setMontantPaye(montantPaye);
        paiement.setDatePaiement(LocalDate.parse(datePaiement));
        paiement.setAdminEnregistrant(admin);
        paiementRepository.save(paiement);

        if (recuFourni) {
            documentJointService.attacher("Paiement", paiement.getId(), recu, "paiements");
        }

        echeanceStatusService.recalculerStatut(echeance);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("echeanceId", echeance.getId());
        snapshot.put("montantPaye", paiement.getMontantPaye());
        snapshot.put("datePaiement", paiement.getDatePaiement());
        auditService.enregistrer(admin, TypeActionAudit.CREATION, "Paiement", paiement.getId(), null, snapshot);

        // Alerte non bloquante : le paiement est bien enregistré, mais un dépassement du
        // montant dû mérite d'être signalé à l'admin (trop-perçu, saisie erronée...). Sans
        // montant dû renseigné (facultatif, cf. Echeance), rien à comparer : pas d'alerte.
        if (echeance.getMontantDu() != null
                && echeanceStatusService.totalPaye(echeance.getId()).compareTo(echeance.getMontantDu()) > 0) {
            redirectAttributes.addFlashAttribute("warning",
                    "Ce paiement porte le total payé au-delà du montant dû pour cette échéance.");
        }

        return "redirect:/admin/contracts/" + echeance.getContrat().getId();
    }

    @GetMapping("/paiements/{id}/recu")
    @PreAuthorize(LECTURE_STAFF)
    public void telechargerRecuPaiement(@PathVariable Long id,
                                         @RequestParam(required = false, defaultValue = "false") boolean apercu,
                                         HttpServletResponse response) throws IOException {
        Optional<DocumentJoint> recu = documentJointService.premier("Paiement", id);
        if (recu.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        ecrireDocument(response, recu.get(), apercu);
    }

    @GetMapping("/echeances")
    @PreAuthorize(LECTURE_STAFF)
    public String echeancesPage(
            @RequestParam(required = false) StatutEcheance statut,
            @RequestParam(required = false) TypeEcheance type,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            Model model) {
        LocalDate today = LocalDate.now();

        // Recalcul paresseux : fait remonter en EN_RETARD les échéances EN_COURS
        // dont la date est dépassée, sans tâche planifiée.
        List<Echeance> candidates = echeanceRepository.findByStatutAndDateEcheanceBefore(StatutEcheance.EN_COURS, today);
        echeanceStatusService.rafraichirStatuts(candidates);

        LocalDate dateDebutParsed = (dateDebut == null || dateDebut.isBlank()) ? null : LocalDate.parse(dateDebut);
        LocalDate dateFinParsed = (dateFin == null || dateFin.isBlank()) ? null : LocalDate.parse(dateFin);

        // Tri par défaut par statut (en retard d'abord, le plus actionnable, puis en cours, puis
        // payées en dernier) puis par date la plus ancienne en premier dans chaque groupe — même
        // ordre que le widget "Échéances" du tableau de bord (cf. AdminDashboardController.
        // prioriteStatutEcheance). Le filtre ?statut=... ci-dessus continue de s'appliquer
        // normalement par-dessus (il ne restreint qu'à un seul statut à la fois, rendant ce tri
        // sans effet visible tant qu'il est actif).
        List<Echeance> echeances = echeanceRepository.findAll().stream()
                .filter(e -> statut == null || e.getStatut() == statut)
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> dateDebutParsed == null || !e.getDateEcheance().isBefore(dateDebutParsed))
                .filter(e -> dateFinParsed == null || !e.getDateEcheance().isAfter(dateFinParsed))
                .sorted(Comparator.comparing((Echeance e) -> prioriteStatutEcheance(e.getStatut()))
                        .thenComparing(Echeance::getDateEcheance))
                .toList();

        Map<Long, BigDecimal> totalPayeParEcheance = new HashMap<>();
        for (Echeance echeance : echeances) {
            totalPayeParEcheance.put(echeance.getId(), echeanceStatusService.totalPaye(echeance.getId()));
        }

        model.addAttribute("echeances", echeances);
        model.addAttribute("totalPayeParEcheance", totalPayeParEcheance);
        model.addAttribute("statutFiltre", statut);
        model.addAttribute("typeFiltre", type);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("statuts", StatutEcheance.values());
        model.addAttribute("types", TypeEcheance.values());
        return "admin-echeances";
    }

    // Ancien lien "Échéances en retard" : conservé pour ne pas casser un signet existant,
    // redirige vers la vue générale pré-filtrée sur EN_RETARD.
    @GetMapping("/echeances/en-retard")
    @PreAuthorize(LECTURE_STAFF)
    public String echeancesEnRetard() {
        return "redirect:/admin/echeances?statut=EN_RETARD";
    }

    /** Ordre d'affichage par défaut du tableau des échéances : en retard d'abord (le plus
     *  actionnable), puis en cours, puis payées — même principe que
     *  AdminDashboardController.prioriteStatutEcheance pour le widget dashboard. */
    private int prioriteStatutEcheance(StatutEcheance statut) {
        return switch (statut) {
            case EN_RETARD -> 0;
            case EN_COURS -> 1;
            case PAYEE -> 2;
        };
    }
}
