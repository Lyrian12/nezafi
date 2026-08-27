package com.sagimo.nezafi.auth;

import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("user", new User());
        return "signup";
    }

    @PostMapping("/signup")
    public String registerUser(
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String email,
            @RequestParam String telephone,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Les mots de passe ne correspondent pas");
            return "signup";
        }

        if (password.length() < 8) {
            model.addAttribute("error", "Le mot de passe doit contenir au moins 8 caractères");
            return "signup";
        }

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            model.addAttribute("error", "Cet email est déjà enregistré");
            return "signup";
        }

        if (userRepository.findByTelephone(telephone).isPresent()) {
            model.addAttribute("error", "Ce numéro de téléphone est déjà enregistré");
            return "signup";
        }

        User user = new User();
        user.setNom(nom);
        user.setPrenom(prenom);
        user.setEmail(email);
        user.setTelephone(telephone);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ROLE_LOCATAIRE);

        userRepository.save(user);

        model.addAttribute("success", "Compte créé avec succès ! Veuillez vous connecter.");
        return "redirect:/signin";
    }

    @GetMapping("/signin")
    public String signin(
            @RequestParam(required = false) String error,
            Model model) {
        if (error != null) {
            model.addAttribute("error", "Identifiant ou mot de passe invalide");
        }
        return "signin";
    }
}
