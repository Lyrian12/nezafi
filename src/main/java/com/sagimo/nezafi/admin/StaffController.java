package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion des comptes du personnel (SECRETARIAT, COMPTABLE) — réservée à ADMIN. Ne passe pas
 * par /signup (ouvert à tous, crée uniquement des comptes ROLE_LOCATAIRE) : ici, c'est un admin
 * qui choisit nom/prénom/mot de passe/rôle pour un membre de l'équipe. Un compte ADMIN ou
 * ROLE_LOCATAIRE n'est jamais créé ni modifié depuis cette page.
 */
@Controller
@RequestMapping("/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private static final List<Role> ROLES_PERSONNEL = List.of(Role.ROLE_SECRETARIAT, Role.ROLE_COMPTABLE);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public StaffController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    private User currentAdmin(Authentication authentication) {
        String identifier = authentication.getName();
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElseThrow(() -> new RuntimeException("Admin not found"));
    }

    /** Levée quand l'id demandé n'existe pas ou n'est pas un compte SECRETARIAT/COMPTABLE
     *  (ex. quelqu'un modifie l'URL à la main pour viser un compte ADMIN ou LOCATAIRE) — gérée
     *  par {@link #compteInvalide} pour rediriger proprement plutôt que de laisser passer une
     *  page d'erreur 500 avec trace technique. */
    private static class CompteInvalideException extends RuntimeException {
        CompteInvalideException(String message) {
            super(message);
        }
    }

    /** Un compte "personnel" au sens de cette page : uniquement SECRETARIAT ou COMPTABLE. */
    private User trouverCompteDuPersonnel(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CompteInvalideException("Compte introuvable."));
        if (!ROLES_PERSONNEL.contains(user.getRole())) {
            throw new CompteInvalideException("Ce compte n'est pas un compte du personnel (SECRETARIAT/COMPTABLE).");
        }
        return user;
    }

    @ExceptionHandler(CompteInvalideException.class)
    public String compteInvalide(CompteInvalideException exception, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", exception.getMessage());
        return "redirect:/admin/staff";
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nom", user.getNom());
        snapshot.put("prenom", user.getPrenom());
        snapshot.put("telephone", user.getTelephone());
        snapshot.put("email", user.getEmail());
        snapshot.put("role", user.getRole());
        // Le mot de passe n'est jamais inclus dans l'instantané du journal d'audit.
        return snapshot;
    }

    @GetMapping
    public String liste(Model model) {
        List<User> personnel = userRepository.findByRoleIn(ROLES_PERSONNEL).stream()
                .sorted(Comparator.comparing(User::getNom))
                .toList();
        model.addAttribute("personnel", personnel);
        return "admin-staff";
    }

    @GetMapping("/add")
    public String ajouterPage(Model model) {
        model.addAttribute("membre", new User());
        return "admin-staff-form";
    }

    @PostMapping("/add")
    public String ajouter(
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String telephone,
            @RequestParam(required = false) String email,
            @RequestParam String role,
            @RequestParam String password,
            Authentication authentication,
            Model model) {

        Role roleEnum = validerRolePersonnel(role);
        String trimmedTelephone = telephone.trim();
        String normalizedEmail = (email == null || email.isBlank()) ? null : email.trim();

        if (roleEnum == null) {
            return rejeter(model, "Rôle invalide : uniquement Secrétariat ou Comptable depuis cette page.",
                    nom, prenom, trimmedTelephone, normalizedEmail, role);
        }
        if (userRepository.findByTelephone(trimmedTelephone).isPresent()) {
            return rejeter(model, "Ce numéro de téléphone est déjà utilisé par un autre compte.",
                    nom, prenom, trimmedTelephone, normalizedEmail, role);
        }
        if (normalizedEmail != null && userRepository.findByEmail(normalizedEmail).isPresent()) {
            return rejeter(model, "Cet email est déjà utilisé par un autre compte.",
                    nom, prenom, trimmedTelephone, normalizedEmail, role);
        }
        if (password == null || password.length() < 8) {
            return rejeter(model, "Le mot de passe doit contenir au moins 8 caractères.",
                    nom, prenom, trimmedTelephone, normalizedEmail, role);
        }

        User membre = new User();
        membre.setNom(nom);
        membre.setPrenom(prenom);
        membre.setTelephone(trimmedTelephone);
        membre.setEmail(normalizedEmail);
        membre.setRole(roleEnum);
        membre.setPassword(passwordEncoder.encode(password));
        userRepository.save(membre);

        auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.CREATION, "CompteDuPersonnel",
                membre.getId(), null, snapshot(membre));

        return "redirect:/admin/staff";
    }

    private Role validerRolePersonnel(String role) {
        try {
            Role roleEnum = Role.valueOf(role);
            return ROLES_PERSONNEL.contains(roleEnum) ? roleEnum : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String rejeter(Model model, String erreur, String nom, String prenom, String telephone,
                            String email, String role) {
        model.addAttribute("error", erreur);
        User rejete = new User();
        rejete.setNom(nom);
        rejete.setPrenom(prenom);
        rejete.setTelephone(telephone);
        rejete.setEmail(email);
        try {
            rejete.setRole(Role.valueOf(role));
        } catch (IllegalArgumentException ignored) {
            // Rôle invalide déjà signalé par le message d'erreur ; le formulaire réaffiche vide.
        }
        model.addAttribute("membre", rejete);
        return "admin-staff-form";
    }

    @GetMapping("/edit/{id}")
    public String modifierPage(@PathVariable Long id, Model model) {
        model.addAttribute("membre", trouverCompteDuPersonnel(id));
        return "admin-staff-form";
    }

    @PostMapping("/edit/{id}")
    public String modifier(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String telephone,
            @RequestParam(required = false) String email,
            @RequestParam String role,
            @RequestParam(required = false) String password,
            Authentication authentication,
            Model model) {

        User membre = trouverCompteDuPersonnel(id);
        Map<String, Object> avant = snapshot(membre);

        Role roleEnum = validerRolePersonnel(role);
        String trimmedTelephone = telephone.trim();
        String normalizedEmail = (email == null || email.isBlank()) ? null : email.trim();

        if (roleEnum == null) {
            model.addAttribute("error", "Rôle invalide : uniquement Secrétariat ou Comptable depuis cette page.");
            model.addAttribute("membre", membre);
            return "admin-staff-form";
        }
        boolean telephonePris = userRepository.findByTelephone(trimmedTelephone)
                .filter(autre -> !autre.getId().equals(id)).isPresent();
        if (telephonePris) {
            model.addAttribute("error", "Ce numéro de téléphone est déjà utilisé par un autre compte.");
            model.addAttribute("membre", membre);
            return "admin-staff-form";
        }
        boolean emailPris = normalizedEmail != null && userRepository.findByEmail(normalizedEmail)
                .filter(autre -> !autre.getId().equals(id)).isPresent();
        if (emailPris) {
            model.addAttribute("error", "Cet email est déjà utilisé par un autre compte.");
            model.addAttribute("membre", membre);
            return "admin-staff-form";
        }
        if (password != null && !password.isBlank() && password.length() < 8) {
            model.addAttribute("error", "Le mot de passe doit contenir au moins 8 caractères.");
            model.addAttribute("membre", membre);
            return "admin-staff-form";
        }

        membre.setNom(nom);
        membre.setPrenom(prenom);
        membre.setTelephone(trimmedTelephone);
        membre.setEmail(normalizedEmail);
        membre.setRole(roleEnum);
        // Mot de passe laissé vide = inchangé ; sinon remplacé.
        boolean motDePasseReinitialise = password != null && !password.isBlank();
        if (motDePasseReinitialise) {
            membre.setPassword(passwordEncoder.encode(password));
        }
        userRepository.save(membre);

        // Un reset de mot de passe seul (aucun autre champ modifié) doit rester tracé — sans
        // jamais faire apparaître le mot de passe lui-même dans le journal d'audit.
        avant.put("motDePasse", "inchangé");
        Map<String, Object> apres = snapshot(membre);
        apres.put("motDePasse", motDePasseReinitialise ? "réinitialisé" : "inchangé");
        if (!avant.equals(apres)) {
            auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.MODIFICATION,
                    "CompteDuPersonnel", membre.getId(), avant, apres);
        }

        return "redirect:/admin/staff";
    }
}
