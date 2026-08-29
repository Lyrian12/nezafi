package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chaque compte du personnel (ADMIN, SECRETARIAT, COMPTABLE) peut modifier ses propres
 * informations, y compris son mot de passe — distinct de {@link StaffController} qui reste
 * réservé à ADMIN pour agir sur le compte d'un AUTRE membre du personnel. Volontairement
 * discret dans l'UI (petite icône, pas un bouton mis en avant) : accessible mais pas au premier
 * plan.
 */
@Controller
@RequestMapping("/admin/mon-compte")
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIAT','COMPTABLE')")
public class MonCompteController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public MonCompteController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    private User utilisateurCourant(Authentication authentication) {
        String identifiant = authentication.getName();
        return userRepository.findByEmail(identifiant)
                .or(() -> userRepository.findByTelephone(identifiant))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nom", user.getNom());
        snapshot.put("prenom", user.getPrenom());
        snapshot.put("telephone", user.getTelephone());
        snapshot.put("email", user.getEmail());
        // Le mot de passe n'est jamais inclus dans l'instantané du journal d'audit.
        return snapshot;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        model.addAttribute("membre", utilisateurCourant(authentication));
        return "admin-mon-compte";
    }

    @PostMapping
    public String modifier(
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String telephone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            Authentication authentication,
            Model model) {

        User membre = utilisateurCourant(authentication);
        Map<String, Object> avant = snapshot(membre);

        String trimmedTelephone = telephone.trim();
        String normalizedEmail = (email == null || email.isBlank()) ? null : email.trim();

        boolean telephonePris = userRepository.findByTelephone(trimmedTelephone)
                .filter(autre -> !autre.getId().equals(membre.getId())).isPresent();
        if (telephonePris) {
            model.addAttribute("error", "Ce numéro de téléphone est déjà utilisé par un autre compte.");
            model.addAttribute("membre", membre);
            return "admin-mon-compte";
        }
        boolean emailPris = normalizedEmail != null && userRepository.findByEmail(normalizedEmail)
                .filter(autre -> !autre.getId().equals(membre.getId())).isPresent();
        if (emailPris) {
            model.addAttribute("error", "Cet email est déjà utilisé par un autre compte.");
            model.addAttribute("membre", membre);
            return "admin-mon-compte";
        }
        if (password != null && !password.isBlank() && password.length() < 8) {
            model.addAttribute("error", "Le mot de passe doit contenir au moins 8 caractères.");
            model.addAttribute("membre", membre);
            return "admin-mon-compte";
        }

        membre.setNom(nom);
        membre.setPrenom(prenom);
        membre.setTelephone(trimmedTelephone);
        membre.setEmail(normalizedEmail);
        boolean motDePasseReinitialise = password != null && !password.isBlank();
        if (motDePasseReinitialise) {
            membre.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(membre);

        // Le nom d'utilisateur Spring Security est l'email (ou le téléphone à défaut, cf.
        // SecurityConfig.userDetailsService) : le modifier ici casserait sinon la session en
        // cours dès la requête suivante (authentication.getName() resterait sur l'ancienne
        // valeur, qui ne correspond plus à personne en base) — réauthentification silencieuse
        // avec la nouvelle identité pour que la session survive à son propre changement.
        String nouveauNomUtilisateur = membre.getEmail() != null ? membre.getEmail() : membre.getTelephone();
        Authentication nouvelleAuthentification = new UsernamePasswordAuthenticationToken(
                nouveauNomUtilisateur, authentication.getCredentials(), authentication.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(nouvelleAuthentification);

        // Reste tracé même si seul le mot de passe change (aucun autre champ modifié), sans
        // jamais faire apparaître le mot de passe lui-même dans le journal — même principe que
        // StaffController pour une réinitialisation faite par un admin sur un autre compte.
        avant.put("motDePasse", "inchangé");
        Map<String, Object> apres = snapshot(membre);
        apres.put("motDePasse", motDePasseReinitialise ? "réinitialisé" : "inchangé");
        if (!avant.equals(apres)) {
            auditService.enregistrer(membre, TypeActionAudit.MODIFICATION, "MonCompte", membre.getId(), avant, apres);
        }

        model.addAttribute("membre", membre);
        model.addAttribute("succes", "Vos informations ont été mises à jour.");
        return "admin-mon-compte";
    }
}
