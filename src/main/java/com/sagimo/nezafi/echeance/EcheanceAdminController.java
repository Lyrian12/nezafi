package com.sagimo.nezafi.echeance;

import com.sagimo.nezafi.admin.AdminAlertService;
import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.paiement.Paiement;
import com.sagimo.nezafi.paiement.PaiementRepository;
import com.sagimo.nezafi.storage.DocumentJoint;
import com.sagimo.nezafi.storage.DocumentJointService;
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
    private final AuditService auditService;
    private final AdminAlertService adminAlertService;
    private final DocumentJointService documentJointService;

    public EcheanceAdminController(EcheanceRepository echeanceRepository, PaiementRepository paiementRepository,
                                    ContratRepository contratRepository, UserRepository userRepository,
                                    EcheanceStatusService echeanceStatusService, AuditService auditService,
                                    AdminAlertService adminAlertService, DocumentJointService documentJointService) {
        this.echeanceRepository = echeanceRepository;
        this.paiementRepository = paiementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.echeanceStatusService = echeanceStatusService;
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
        documentJointService.premier("Contrat", id)
                .ifPresent(facture -> model.addAttribute("factureNomAffiche", documentJointService.nomOriginal(facture)));
        return "admin-contract-detail";
    }

    @PostMapping("/contracts/{id}/facture")
    @PreAuthorize(EDITION_STAFF)
    public String uploaderFacturePaiement(@PathVariable Long id, @RequestParam MultipartFile facture,
                                           Authentication authentication, RedirectAttributes redirectAttributes)
            throws IOException {
        contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (facture.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Aucun fichier sélectionné.");
            return "redirect:/admin/contracts/" + id;
        }
        String type = facture.getContentType();
        boolean typeAccepte = type != null && (type.equals(MediaType.APPLICATION_PDF_VALUE) || type.startsWith("image/"));
        if (!typeAccepte) {
            redirectAttributes.addFlashAttribute("error", "La facture doit être un PDF ou une image.");
            return "redirect:/admin/contracts/" + id;
        }

        boolean remplacement = !documentJointService.lister("Contrat", id).isEmpty();
        DocumentJoint document = documentJointService.remplacerUnique("Contrat", id, facture, "contrats");

        User admin = currentAdmin(authentication);
        auditService.enregistrer(admin, remplacement ? TypeActionAudit.MODIFICATION : TypeActionAudit.CREATION,
                "FactureContrat", id, null, Map.of("fichier", documentJointService.nomOriginal(document)));

        return "redirect:/admin/contracts/" + id;
    }

    @GetMapping("/contracts/{id}/facture")
    @PreAuthorize(LECTURE_STAFF)
    public void telechargerFacturePaiement(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Optional<DocumentJoint> facture = documentJointService.premier("Contrat", id);
        if (facture.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Resource ressource = documentJointService.charger(facture.get());
        String nomAffiche = documentJointService.nomOriginal(facture.get());
        MediaType type = MediaTypeFactory.getMediaType(ressource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        response.setContentType(type.toString());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
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
        // montant dû mérite d'être signalé à l'admin (trop-perçu, saisie erronée...).
        if (echeanceStatusService.totalPaye(echeance.getId()).compareTo(echeance.getMontantDu()) > 0) {
            redirectAttributes.addFlashAttribute("warning",
                    "Ce paiement porte le total payé au-delà du montant dû pour cette échéance.");
        }

        return "redirect:/admin/contracts/" + echeance.getContrat().getId();
    }

    @GetMapping("/paiements/{id}/recu")
    @PreAuthorize(LECTURE_STAFF)
    public void telechargerRecuPaiement(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Optional<DocumentJoint> recu = documentJointService.premier("Paiement", id);
        if (recu.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Resource ressource = documentJointService.charger(recu.get());
        String nomAffiche = documentJointService.nomOriginal(recu.get());
        MediaType type = MediaTypeFactory.getMediaType(ressource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        response.setContentType(type.toString());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(nomAffiche, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(ressource.getContentAsByteArray());
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

        List<Echeance> echeances = echeanceRepository.findAll().stream()
                .filter(e -> statut == null || e.getStatut() == statut)
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> dateDebutParsed == null || !e.getDateEcheance().isBefore(dateDebutParsed))
                .filter(e -> dateFinParsed == null || !e.getDateEcheance().isAfter(dateFinParsed))
                .sorted(Comparator.comparing(Echeance::getDateEcheance))
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
}
