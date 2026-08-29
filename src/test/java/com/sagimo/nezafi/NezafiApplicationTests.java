package com.sagimo.nezafi;

import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
            user.setTelephone("+212600000001");
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
