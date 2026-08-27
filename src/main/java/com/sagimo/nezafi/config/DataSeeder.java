package com.sagimo.nezafi.config;

import com.sagimo.nezafi.boutique.Boutique;
import com.sagimo.nezafi.boutique.BoutiqueRepository;
import com.sagimo.nezafi.boutique.CategorieBoutique;
import com.sagimo.nezafi.boutique.Palier;
import com.sagimo.nezafi.boutique.StatutBoutique;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BoutiqueRepository boutiqueRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, BoutiqueRepository boutiqueRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.boutiqueRepository = boutiqueRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("admin@nezafi.com").isEmpty()) {
            User admin = new User();
            admin.setNom("Nezafi");
            admin.setPrenom("Admin");
            admin.setTelephone("+212600000000");
            admin.setEmail("admin@nezafi.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ROLE_ADMIN);
            userRepository.save(admin);
        }

        if (boutiqueRepository.count() == 0) {
            Boutique boutique = new Boutique();
            boutique.setName("Boutique Demo");
            boutique.setImageUrl("https://images.unsplash.com/photo-1521572267360-ee0c2909d518");
            boutique.setStatut(StatutBoutique.DISPONIBLE);
            boutique.setPalier(Palier.PALIER_1);
            boutique.setSuperficie(new BigDecimal("25.00"));
            boutique.setPrix(new BigDecimal("150000.00"));
            boutique.setCategorie(CategorieBoutique.BOUTIQUE);
            boutiqueRepository.save(boutique);
        }
    }
}
