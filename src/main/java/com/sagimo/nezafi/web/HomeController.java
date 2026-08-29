package com.sagimo.nezafi.web;

import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.ContratStatusService;
import com.sagimo.nezafi.storage.DocumentJointService;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final UserRepository userRepository;
    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final ContratStatusService contratStatusService;
    private final DocumentJointService documentJointService;

    public HomeController(UserRepository userRepository,
                         EmplacementRepository emplacementRepository,
                         ContratRepository contratRepository,
                         ContratStatusService contratStatusService,
                         DocumentJointService documentJointService) {
        this.userRepository = userRepository;
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.contratStatusService = contratStatusService;
        this.documentJointService = documentJointService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String identifier = authentication.getName();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElse(null);

        if (user == null) {
            return "redirect:/signin";
        }

        String role = user.getRole().name();

        if (role.equals("ROLE_ADMIN") || role.equals("ROLE_SECRETARIAT") || role.equals("ROLE_COMPTABLE")) {
            // SECRETARIAT et COMPTABLE voient le même tableau de bord qu'ADMIN, à l'exception
            // du bloc journal d'audit et de la gestion du personnel (masqués côté template/
            // contrôleur, cf. AdminDashboardController).
            return "redirect:/admin/dashboard";
        }

        // ROLE_LOCATAIRE - User dashboard
        model.addAttribute("username", user.getPrenom() + " " + user.getNom());
        List<Emplacement> shops = emplacementRepository.findAll();
        // Rattrape les emplacements dont le contrat a simplement expiré sans qu'on y touche,
        // pour qu'un locataire ne voie jamais un emplacement DISPONIBLE affiché à tort comme
        // occupé (cf. ContratStatusService.rafraichirStatut).
        contratStatusService.rafraichirStatuts(shops);
        model.addAttribute("shops", shops);

        // Vignette de couverture = photo la plus récente de chaque emplacement (peut être
        // absente : la galerie est facultative, cf. com.sagimo.nezafi.storage.DocumentJoint).
        Map<Long, String> couvertureParEmplacementId = new HashMap<>();
        for (Emplacement shop : shops) {
            documentJointService.premier("Emplacement", shop.getId())
                    .ifPresent(photo -> couvertureParEmplacementId.put(shop.getId(), "/files/documents/" + photo.getId()));
        }
        model.addAttribute("couvertureParEmplacementId", couvertureParEmplacementId);

        return "emplacements";
    }
}
