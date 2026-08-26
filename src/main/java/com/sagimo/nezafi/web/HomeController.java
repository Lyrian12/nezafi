package com.sagimo.nezafi.web;

import com.sagimo.nezafi.boutique.BoutiqueRepository;
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
    private final BoutiqueRepository boutiqueRepository;
    private final ContratRepository contratRepository;

    public HomeController(UserRepository userRepository,
                         BoutiqueRepository boutiqueRepository,
                         ContratRepository contratRepository) {
        this.userRepository = userRepository;
        this.boutiqueRepository = boutiqueRepository;
        this.contratRepository = contratRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return "redirect:/signin";
        }

        String role = user.getRole().name();

        if (role.equals("ROLE_ADMIN")) {
            return "redirect:/admin/stores";
        }

        // ROLE_LOCATAIRE - User dashboard
        model.addAttribute("username", user.getName());
        model.addAttribute("shops", boutiqueRepository.findAll());
        model.addAttribute("pendingRequestsCount", 0);

        return "boutique";
    }
}
