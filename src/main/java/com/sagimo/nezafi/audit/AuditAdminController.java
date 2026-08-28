package com.sagimo.nezafi.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Consultation du journal d'audit — admin uniquement. Liste simple, sans filtre
 * ni pagination : le volume attendu reste faible pour une appli à usage interne.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AuditAdminController {

    private final JournalAuditRepository journalAuditRepository;

    public AuditAdminController(JournalAuditRepository journalAuditRepository) {
        this.journalAuditRepository = journalAuditRepository;
    }

    @GetMapping("/audit")
    public String journal(Model model) {
        model.addAttribute("entrees", journalAuditRepository.findAllByOrderByDateActionDesc());
        return "admin-audit";
    }
}
