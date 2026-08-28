package com.sagimo.nezafi.echeance;

import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.paiement.Paiement;
import com.sagimo.nezafi.paiement.PaiementRepository;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vues et actions admin pour les échéances et paiements. Volontairement séparé
 * d'{@link com.sagimo.nezafi.admin.AdminController} : module en cours de test,
 * plus simple à retirer proprement si la branche est abandonnée.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class EcheanceAdminController {

    private final EcheanceRepository echeanceRepository;
    private final PaiementRepository paiementRepository;
    private final ContratRepository contratRepository;
    private final UserRepository userRepository;
    private final EcheanceStatusService echeanceStatusService;
    private final AuditService auditService;

    public EcheanceAdminController(EcheanceRepository echeanceRepository, PaiementRepository paiementRepository,
                                    ContratRepository contratRepository, UserRepository userRepository,
                                    EcheanceStatusService echeanceStatusService, AuditService auditService) {
        this.echeanceRepository = echeanceRepository;
        this.paiementRepository = paiementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.echeanceStatusService = echeanceStatusService;
        this.auditService = auditService;
    }

    @GetMapping("/contracts/{id}")
    public String contractDetail(@PathVariable Long id, Model model) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        List<Echeance> echeances = echeanceRepository.findByContratId(id);
        echeanceStatusService.rafraichirStatuts(echeances);
        echeances.sort(Comparator.comparing(Echeance::getDateEcheance));

        Map<Long, List<Paiement>> paiementsParEcheance = new HashMap<>();
        Map<Long, BigDecimal> totalPayeParEcheance = new HashMap<>();
        for (Echeance echeance : echeances) {
            List<Paiement> paiements = paiementRepository.findByEcheanceId(echeance.getId());
            paiementsParEcheance.put(echeance.getId(), paiements);
            totalPayeParEcheance.put(echeance.getId(), echeanceStatusService.totalPaye(echeance.getId()));
        }

        model.addAttribute("contrat", contrat);
        model.addAttribute("echeances", echeances);
        model.addAttribute("paiementsParEcheance", paiementsParEcheance);
        model.addAttribute("totalPayeParEcheance", totalPayeParEcheance);
        return "admin-contract-detail";
    }

    @PostMapping("/echeances/{id}/paiements")
    public String enregistrerPaiement(
            @PathVariable Long id,
            @RequestParam BigDecimal montantPaye,
            @RequestParam String datePaiement,
            @RequestParam(required = false) String cheminRecu,
            Authentication authentication) {

        Echeance echeance = echeanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Echeance not found"));

        String identifier = authentication.getName();
        User admin = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Paiement paiement = new Paiement();
        paiement.setEcheance(echeance);
        paiement.setMontantPaye(montantPaye);
        paiement.setDatePaiement(LocalDate.parse(datePaiement));
        paiement.setCheminRecu((cheminRecu == null || cheminRecu.isBlank()) ? null : cheminRecu.trim());
        paiement.setAdminEnregistrant(admin);
        paiementRepository.save(paiement);

        echeanceStatusService.recalculerStatut(echeance);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("echeanceId", echeance.getId());
        snapshot.put("montantPaye", paiement.getMontantPaye());
        snapshot.put("datePaiement", paiement.getDatePaiement());
        auditService.enregistrer(admin, TypeActionAudit.CREATION, "Paiement", paiement.getId(), null, snapshot);

        return "redirect:/admin/contracts/" + echeance.getContrat().getId();
    }

    @GetMapping("/echeances/en-retard")
    public String echeancesEnRetard(Model model) {
        LocalDate today = LocalDate.now();

        // Recalcul paresseux : fait remonter en EN_RETARD les échéances EN_ATTENTE
        // dont la date est dépassée, sans tâche planifiée.
        List<Echeance> candidates = echeanceRepository.findByStatutAndDateEcheanceBefore(StatutEcheance.EN_ATTENTE, today);
        echeanceStatusService.rafraichirStatuts(candidates);

        List<Echeance> enRetard = echeanceRepository.findByStatut(StatutEcheance.EN_RETARD);
        enRetard.sort(Comparator.comparing(Echeance::getDateEcheance));

        Map<Long, BigDecimal> totalPayeParEcheance = new HashMap<>();
        for (Echeance echeance : enRetard) {
            totalPayeParEcheance.put(echeance.getId(), echeanceStatusService.totalPaye(echeance.getId()));
        }

        model.addAttribute("echeances", enRetard);
        model.addAttribute("totalPayeParEcheance", totalPayeParEcheance);
        return "admin-echeances-retard";
    }
}
