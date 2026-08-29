package com.sagimo.nezafi.audit;

import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultation du journal d'audit — admin uniquement. Filtrable par entité, par
 * utilisateur et par plage de dates ; pas de pagination : le volume attendu reste
 * faible pour une appli à usage interne.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AuditAdminController {

    private final JournalAuditRepository journalAuditRepository;
    private final UserRepository userRepository;

    public AuditAdminController(JournalAuditRepository journalAuditRepository, UserRepository userRepository) {
        this.journalAuditRepository = journalAuditRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/audit")
    public String journal(
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            Model model) {
        List<JournalAudit> toutes = journalAuditRepository.findAllByOrderByDateActionDesc();

        LocalDate dateDebutParsed = (dateDebut == null || dateDebut.isBlank()) ? null : LocalDate.parse(dateDebut);
        LocalDate dateFinParsed = (dateFin == null || dateFin.isBlank()) ? null : LocalDate.parse(dateFin);

        List<JournalAudit> entrees = toutes.stream()
                .filter(e -> entite == null || entite.isBlank() || entite.equals(e.getNomEntite()))
                .filter(e -> utilisateurId == null || utilisateurId.equals(e.getUtilisateur().getId()))
                .filter(e -> dateDebutParsed == null || !e.getDateAction().toLocalDate().isBefore(dateDebutParsed))
                .filter(e -> dateFinParsed == null || !e.getDateAction().toLocalDate().isAfter(dateFinParsed))
                .toList();

        List<String> entites = toutes.stream().map(JournalAudit::getNomEntite).distinct().sorted().toList();
        // Les 3 rôles staff peuvent désormais déclencher des actions auditées (pas seulement
        // ADMIN) : le filtre doit pouvoir cibler n'importe lequel d'entre eux.
        List<User> admins = userRepository.findByRoleIn(List.of(Role.ROLE_ADMIN, Role.ROLE_SECRETARIAT, Role.ROLE_COMPTABLE));

        model.addAttribute("entrees", entrees);
        model.addAttribute("entites", entites);
        model.addAttribute("admins", admins);
        model.addAttribute("entiteFiltre", entite);
        model.addAttribute("utilisateurIdFiltre", utilisateurId);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        return "admin-audit";
    }
}
