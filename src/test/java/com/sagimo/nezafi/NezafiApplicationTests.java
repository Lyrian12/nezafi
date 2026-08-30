package com.sagimo.nezafi;

import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Profil "test" (src/test/resources/application-test.properties) : surcouche additive sur
// application.properties (H2 en mémoire à la place de PostgreSQL, Flyway désactivé) — contrairement
// à un fichier test/resources/application.properties qui, lui, remplacerait entièrement le
// principal au lieu de le compléter et ferait perdre des propriétés non liées au datasource
// (ex. app.upload-dir) qui n'existent que dans le fichier main.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NezafiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void locataireCanSignInAndSeeBoutiqueDashboard() throws Exception {
        String email = "tenant-login-test@nezafi.com";
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            user.setNom("Tenant");
            user.setPrenom("Test");
            // Distinct de tous les numéros seedés par DataSeeder (admin/secretariat/comptable en
            // +212600000000..2, clients en +237690000001..6) — une collision ici fait échouer le
            // test avec une DataIntegrityViolationException sans rapport avec ce qui est testé.
            user.setTelephone("+212611111111");
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode("password123"));
            user.setRole(Role.ROLE_LOCATAIRE);
            userRepository.save(user);
        }

        mockMvc.perform(post("/signin")
                        .with(csrf())
                        .param("username", email)
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(get("/dashboard")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email)
                                .roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vos Emplacements")));
    }
}
