package com.sagimo.nezafi.emplacement;

import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API REST des emplacements. Corrige un trou de sécurité identifié à l'audit : ce contrôleur
 * n'avait auparavant aucune restriction de rôle propre — contrairement à {@link com.sagimo.nezafi.contrat.ContratController}
 * et {@link com.sagimo.nezafi.user.UserController} — si bien que n'importe quel utilisateur
 * authentifié, y compris COMPTABLE ou LOCATAIRE, pouvait créer/modifier/supprimer un emplacement.
 *
 * Règles (cohérentes avec les routes Thymeleaf équivalentes d'AdminController) : ADMIN et
 * SECRETARIAT ont l'accès complet. COMPTABLE est strictement lecture seule, sans aucune
 * exception. LOCATAIRE ne peut lire que les emplacements DISPONIBLE (cohérent avec ce qu'il
 * voit déjà côté portail /shops).
 *
 * Journalisation : même périmètre que côté Thymeleaf (AdminController) — seul un changement de
 * prix est audité, pas le reste des champs de l'emplacement. Avant cette correction, ce
 * contrôleur n'appelait jamais AuditService.
 */
@RestController
@RequestMapping("/api/emplacements")
public class EmplacementController {

    private static final Set<String> ROLES_STAFF_LECTURE = Set.of("ROLE_ADMIN", "ROLE_SECRETARIAT", "ROLE_COMPTABLE");

    private final EmplacementRepository emplacementRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public EmplacementController(EmplacementRepository emplacementRepository, UserRepository userRepository,
                                  AuditService auditService) {
        this.emplacementRepository = emplacementRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    private User utilisateurCourant(Authentication authentication) {
        String identifier = authentication.getName();
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElse(null);
    }

    private boolean aUnRole(Authentication authentication, Set<String> roles) {
        return authentication.getAuthorities().stream().anyMatch(a -> roles.contains(a.getAuthority()));
    }

    private boolean estStaffLecture(Authentication authentication) {
        return aUnRole(authentication, ROLES_STAFF_LECTURE);
    }

    /** Staff (ADMIN/SECRETARIAT/COMPTABLE) : lit tout. LOCATAIRE (ou tout autre cas) : jamais
     *  un emplacement qui ne serait pas DISPONIBLE. */
    private boolean peutLire(Authentication authentication, Emplacement emplacement) {
        return estStaffLecture(authentication) || emplacement.getStatut() == StatutEmplacement.DISPONIBLE;
    }

    @GetMapping
    public List<Emplacement> getAllEmplacements(Authentication authentication) {
        if (estStaffLecture(authentication)) {
            return emplacementRepository.findAll();
        }
        return emplacementRepository.findAll().stream()
                .filter(e -> e.getStatut() == StatutEmplacement.DISPONIBLE)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Emplacement> getEmplacementById(@PathVariable Long id, Authentication authentication) {
        return emplacementRepository.findById(id)
                .map(emplacement -> peutLire(authentication, emplacement)
                        ? ResponseEntity.ok(emplacement)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).<Emplacement>build())
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIAT')")
    public ResponseEntity<Emplacement> createEmplacement(@RequestBody Emplacement emplacement) {
        Emplacement saved = emplacementRepository.save(emplacement);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIAT')")
    public ResponseEntity<Emplacement> updateEmplacement(@PathVariable Long id, @RequestBody Emplacement emplacement,
                                                           Authentication authentication) {
        return emplacementRepository.findById(id)
                .map(existing -> {
                    BigDecimal ancienPrix = existing.getPrix();

                    existing.setName(emplacement.getName());
                    existing.setStatut(emplacement.getStatut());
                    existing.setPalier(emplacement.getPalier());
                    existing.setSuperficie(emplacement.getSuperficie());
                    existing.setPrix(emplacement.getPrix());
                    existing.setCategorie(emplacement.getCategorie());
                    if (emplacement.getAddedAt() != null) {
                        existing.setAddedAt(emplacement.getAddedAt());
                    }
                    Emplacement saved = emplacementRepository.save(existing);

                    // Seul le prix est audité (pas le reste des champs), même périmètre que
                    // AdminController.editStore.
                    BigDecimal nouveauPrix = saved.getPrix();
                    User demandeur = utilisateurCourant(authentication);
                    if (demandeur != null && (ancienPrix == null ? nouveauPrix != null : ancienPrix.compareTo(nouveauPrix) != 0)) {
                        auditService.enregistrer(demandeur, TypeActionAudit.MODIFICATION, "Emplacement", id,
                                Map.of("prix", ancienPrix == null ? "" : ancienPrix),
                                Map.of("prix", nouveauPrix));
                    }

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIAT')")
    public ResponseEntity<Void> deleteEmplacement(@PathVariable Long id) {
        if (!emplacementRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        emplacementRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
