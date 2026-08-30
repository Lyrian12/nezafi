package com.sagimo.nezafi.config;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Injecte des indicateurs de rôle (estAdmin, estSecretariat, estComptable, peutModifier) dans
 * le modèle de chaque page — confort d'affichage pour que les templates masquent les actions
 * d'écriture (COMPTABLE ne voit aucun bouton d'écriture, SECRETARIAT ne voit pas "Supprimer").
 * Le contrôle d'accès réel reste porté par @PreAuthorize et SecurityConfig : retirer un bouton
 * du HTML n'empêche jamais d'appeler la route directement, ce n'est qu'un confort, pas une
 * frontière de sécurité.
 */
@ControllerAdvice
public class RoleModelAdvice {

    @ModelAttribute
    public void ajouterIndicateursDeRole(Authentication authentication, Model model) {
        Set<String> autorites = authentication == null
                ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.toSet());

        boolean estAdmin = autorites.contains("ROLE_ADMIN");
        boolean estSecretariat = autorites.contains("ROLE_SECRETARIAT");
        boolean estComptable = autorites.contains("ROLE_COMPTABLE");

        model.addAttribute("estAdmin", estAdmin);
        model.addAttribute("estSecretariat", estSecretariat);
        model.addAttribute("estComptable", estComptable);
        // Peut créer/modifier (pas les suppressions, réservées à ADMIN seul).
        model.addAttribute("peutModifier", estAdmin || estSecretariat);
    }
}
