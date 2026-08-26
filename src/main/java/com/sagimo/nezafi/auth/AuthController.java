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
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String telephone,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "signup";
        }

        if (password.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters long");
            return "signup";
        }

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            model.addAttribute("error", "Email already registered");
            return "signup";
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setTelephone(telephone);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ROLE_LOCATAIRE);

        userRepository.save(user);

        model.addAttribute("success", "Account created successfully! Please sign in.");
        return "redirect:/signin";
    }

    @GetMapping("/signin")
    public String signin(
            @RequestParam(required = false) String error,
            Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }
        return "signin";
    }
}
