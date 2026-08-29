package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * API REST des contrats. Corrige une faille IDOR (identifiée à l'audit) : avant, n'importe quel
 * utilisateur authentifié — y compris un LOCATAIRE — pouvait lire, créer, modifier ou supprimer
 * le contrat de n'importe qui via ces routes, aucune vérification de propriétaire n'existait.
 *
 * Règles : ADMIN et SECRETARIAT ont l'accès complet (cohérent avec les routes Thymeleaf
 * équivalentes d'AdminController). COMPTABLE est strictement lecture seule. LOCATAIRE ne peut
 * lire/créer/modifier/supprimer que ses propres contrats (jamais ceux d'un autre locataire).
 */
@RestController
@RequestMapping("/api/contrats")
public class ContratController {

    private static final Set<String> ROLES_STAFF_LECTURE = Set.of("ROLE_ADMIN", "ROLE_SECRETARIAT", "ROLE_COMPTABLE");
    private static final Set<String> ROLES_STAFF_EDITION = Set.of("ROLE_ADMIN", "ROLE_SECRETARIAT");

    private final ContratRepository contratRepository;
    private final EmplacementRepository emplacementRepository;
    private final UserRepository userRepository;

    public ContratController(ContratRepository contratRepository,
                            EmplacementRepository emplacementRepository,
                            UserRepository userRepository) {
        this.contratRepository = contratRepository;
        this.emplacementRepository = emplacementRepository;
        this.userRepository = userRepository;
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

    private boolean estStaffEdition(Authentication authentication) {
        return aUnRole(authentication, ROLES_STAFF_EDITION);
    }

    /** Le contrat appartient-il au LOCATAIRE actuellement connecté ? */
    private boolean estProprietaire(Authentication authentication, Contrat contrat) {
        User user = utilisateurCourant(authentication);
        return user != null && contrat.getLocataire() != null && user.getId().equals(contrat.getLocataire().getId());
    }

    private boolean peutLire(Authentication authentication, Contrat contrat) {
        return estStaffLecture(authentication) || estProprietaire(authentication, contrat);
    }

    /** Écriture : ADMIN/SECRETARIAT sur tout, LOCATAIRE seulement sur son propre contrat,
     *  COMPTABLE jamais (ni staff-édition, ni locataire). */
    private boolean peutModifier(Authentication authentication, Contrat contrat) {
        return estStaffEdition(authentication) || estProprietaire(authentication, contrat);
    }

    @GetMapping
    public List<Contrat> getAllContrats(Authentication authentication) {
        if (estStaffLecture(authentication)) {
            return contratRepository.findAll();
        }
        // LOCATAIRE (ou tout autre cas) : jamais la liste de tout le monde, uniquement les siens.
        User user = utilisateurCourant(authentication);
        return user == null ? List.of() : contratRepository.findByLocataireId(user.getId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contrat> getContratById(@PathVariable Long id, Authentication authentication) {
        return contratRepository.findById(id)
                .map(contrat -> peutLire(authentication, contrat)
                        ? ResponseEntity.ok(contrat)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).<Contrat>build())
                .orElse(ResponseEntity.notFound().build());
    }

    // Requête de gestion (historique des contrats d'un emplacement, tous locataires confondus) :
    // pas d'usage légitime côté locataire, réservée au staff en lecture.
    @GetMapping("/emplacement/{emplacementId}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARIAT','COMPTABLE')")
    public List<Contrat> getContratsByEmplacement(@PathVariable Long emplacementId) {
        return contratRepository.findByEmplacementId(emplacementId);
    }

    @GetMapping("/locataire/{locataireId}")
    public ResponseEntity<List<Contrat>> getContratsByLocataire(@PathVariable Long locataireId, Authentication authentication) {
        if (estStaffLecture(authentication)) {
            return ResponseEntity.ok(contratRepository.findByLocataireId(locataireId));
        }
        User user = utilisateurCourant(authentication);
        if (user != null && user.getId().equals(locataireId)) {
            return ResponseEntity.ok(contratRepository.findByLocataireId(locataireId));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Contrat> createContrat(@RequestBody Contrat contrat, Authentication authentication) {
        if (contrat.getEmplacement() == null || contrat.getEmplacement().getId() == null
                || contrat.getLocataire() == null || contrat.getLocataire().getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        Emplacement emplacement = emplacementRepository.findById(contrat.getEmplacement().getId()).orElse(null);
        User locataire = userRepository.findById(contrat.getLocataire().getId()).orElse(null);
        if (emplacement == null || locataire == null) {
            return ResponseEntity.badRequest().build();
        }

        User demandeur = utilisateurCourant(authentication);
        boolean autorise = estStaffEdition(authentication)
                || (demandeur != null && demandeur.getId().equals(locataire.getId()));
        if (!autorise) {
            // Ni staff-édition, ni le locataire créant pour lui-même (donc aussi COMPTABLE,
            // toujours refusé en écriture).
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        Contrat saved = contratRepository.save(contrat);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contrat> updateContrat(@PathVariable Long id, @RequestBody Contrat contrat, Authentication authentication) {
        return contratRepository.findById(id)
                .map(existing -> {
                    if (!peutModifier(authentication, existing)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Contrat>build();
                    }

                    if (contrat.getEmplacement() != null && contrat.getEmplacement().getId() != null) {
                        Emplacement emplacement = emplacementRepository.findById(contrat.getEmplacement().getId()).orElse(null);
                        if (emplacement == null) {
                            return ResponseEntity.badRequest().<Contrat>build();
                        }
                        existing.setEmplacement(emplacement);
                    }

                    if (contrat.getLocataire() != null && contrat.getLocataire().getId() != null) {
                        User locataire = userRepository.findById(contrat.getLocataire().getId()).orElse(null);
                        if (locataire == null) {
                            return ResponseEntity.badRequest().<Contrat>build();
                        }
                        existing.setLocataire(locataire);
                    }

                    if (contrat.getDateDebut() != null) {
                        existing.setDateDebut(contrat.getDateDebut());
                    }
                    if (contrat.getDateFin() != null) {
                        existing.setDateFin(contrat.getDateFin());
                    }
                    if (contrat.getTermes() != null) {
                        existing.setTermes(contrat.getTermes());
                    }
                    if (contrat.getStatut() != null) {
                        existing.setStatut(contrat.getStatut());
                    }

                    return ResponseEntity.ok(contratRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContrat(@PathVariable Long id, Authentication authentication) {
        return contratRepository.findById(id)
                .map(existing -> {
                    if (!peutModifier(authentication, existing)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build();
                    }
                    contratRepository.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
