package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recherche globale unique de la barre du haut de l'admin : cherche à la fois dans
 * les emplacements, les clients et les contrats, et renvoie de quoi construire des
 * liens directs vers la bonne fiche. Volume attendu faible (usage interne) : pas de
 * pagination, juste une limite par catégorie pour garder le menu déroulant lisible.
 */
@RestController
@RequestMapping("/admin/search")
@PreAuthorize("hasRole('ADMIN')")
public class GlobalSearchController {

    private static final int LIMITE_PAR_CATEGORIE = 5;

    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final UserRepository userRepository;

    public GlobalSearchController(EmplacementRepository emplacementRepository, ContratRepository contratRepository,
                                   UserRepository userRepository) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @ResponseBody
    public List<Map<String, String>> rechercher(@RequestParam(required = false) String q) {
        List<Map<String, String>> resultats = new ArrayList<>();
        if (q == null || q.trim().length() < 2) {
            return resultats;
        }
        String terme = q.trim().toLowerCase();

        emplacementRepository.findByNameContainingIgnoreCase(terme).stream()
                .limit(LIMITE_PAR_CATEGORIE)
                .forEach(e -> resultats.add(resultat("Emplacement", e.getName(), "/admin/stores/" + e.getId())));

        userRepository.searchByRoleAndNomOrPrenomOrTelephone(Role.ROLE_LOCATAIRE, terme).stream()
                .limit(LIMITE_PAR_CATEGORIE)
                .forEach(c -> resultats.add(resultat("Client", c.getPrenom() + " " + c.getNom(), "/admin/clients/" + c.getId())));

        contratRepository.findAll().stream()
                .filter(c -> correspond(c, terme))
                .limit(LIMITE_PAR_CATEGORIE)
                .forEach(c -> resultats.add(resultat("Contrat", libelleContrat(c), "/admin/contracts/" + c.getId())));

        return resultats;
    }

    private String libelleContrat(Contrat contrat) {
        String base = contrat.getEmplacement().getName() + " — " + contrat.getLocataire().getPrenom()
                + " " + contrat.getLocataire().getNom();
        return (contrat.getNomEnseigne() != null && !contrat.getNomEnseigne().isBlank())
                ? base + " (" + contrat.getNomEnseigne() + ")" : base;
    }

    private boolean correspond(Contrat contrat, String terme) {
        Emplacement emplacement = contrat.getEmplacement();
        User locataire = contrat.getLocataire();
        return (emplacement != null && emplacement.getName().toLowerCase().contains(terme))
                || (locataire != null && (locataire.getNom().toLowerCase().contains(terme)
                        || locataire.getPrenom().toLowerCase().contains(terme)))
                || (contrat.getNomEnseigne() != null && contrat.getNomEnseigne().toLowerCase().contains(terme))
                || (contrat.getActivite() != null && contrat.getActivite().toLowerCase().contains(terme));
    }

    private Map<String, String> resultat(String type, String label, String url) {
        Map<String, String> resultat = new LinkedHashMap<>();
        resultat.put("type", type);
        resultat.put("label", label);
        resultat.put("url", url);
        return resultat;
    }
}
