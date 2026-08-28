package com.sagimo.nezafi.config;

import com.sagimo.nezafi.emplacement.CategorieEmplacement;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.Palier;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
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
    private final EmplacementRepository emplacementRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, EmplacementRepository emplacementRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emplacementRepository = emplacementRepository;
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

        if (emplacementRepository.count() == 0) {
            Emplacement emplacement = new Emplacement();
            emplacement.setName("Emplacement Demo");
            emplacement.setImageUrl("https://images.unsplash.com/photo-1521572267360-ee0c2909d518");
            emplacement.setStatut(StatutEmplacement.DISPONIBLE);
            emplacement.setPalier(Palier.PALIER_1);
            emplacement.setSuperficie(new BigDecimal("25.00"));
            emplacement.setPrix(new BigDecimal("150000.00"));
            emplacement.setCategorie(CategorieEmplacement.BOUTIQUE);
            emplacementRepository.save(emplacement);
        }
    }
}
