package com.sagimo.nezafi.web;

import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final UserRepository userRepository;
    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;

    public HomeController(UserRepository userRepository,
                         EmplacementRepository emplacementRepository,
                         ContratRepository contratRepository) {
        this.userRepository = userRepository;
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
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

        if (role.equals("ROLE_ADMIN")) {
            return "redirect:/admin/dashboard";
        }

        // ROLE_LOCATAIRE - User dashboard
        model.addAttribute("username", user.getPrenom() + " " + user.getNom());
        model.addAttribute("shops", emplacementRepository.findAll());

        return "emplacements";
    }
}
