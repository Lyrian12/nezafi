package com.sagimo.nezafi.config;

import com.sagimo.nezafi.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
// Indispensable pour que les @PreAuthorize posés sur les méthodes de contrôleur (droits fins
// par route : lecture/écriture/ADMIN seul) soient réellement évalués — sans cette annotation,
// ils sont silencieusement ignorés et seule la règle grossière de /admin/** ci-dessous compte.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // La console H2 garde son exemption CSRF (son propre flux de formulaires ne porte
            // pas le jeton CSRF de l'application) — mais elle n'est plus accessible à tous, cf.
            // hasRole("ADMIN") ci-dessous : accès direct en SQL à toute la base, réservé ADMIN.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/signup", "/signin", "/forgot-password", "/register", "/css/**", "/js/**").permitAll()
                // Console H2 : accès direct en SQL à toute la base (y compris les mots de passe
                // hashés), réservée à ADMIN — elle était avant ouverte à tous sans connexion.
                .requestMatchers("/h2-console/**").hasRole("ADMIN")
                // Règle grossière ici : /admin/staff (gestion des comptes) et /admin/audit
                // restent ADMIN uniquement via ces deux matchers plus spécifiques, évalués
                // avant la règle large qui suit. Le détail fin (SECRETARIAT en écriture,
                // COMPTABLE en lecture seule) est ensuite tranché route par route avec
                // @PreAuthorize sur chaque méthode de contrôleur.
                .requestMatchers("/admin/staff/**", "/admin/audit").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SECRETARIAT", "COMPTABLE")
                .requestMatchers("/shops/**", "/contracts/**").hasRole("LOCATAIRE")
                // /api/users expose la création/modification de n'importe quel compte (y
                // compris le rôle) : ADMIN uniquement, plus restrictif que /api/** en général.
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                // /api/contrats accueille maintenant aussi SECRETARIAT (édition) et COMPTABLE
                // (lecture seule) : le détail fin est tranché dans ContratController lui-même,
                // cette règle n'est que la barrière grossière laissant passer les 4 rôles.
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "SECRETARIAT", "COMPTABLE", "LOCATAIRE")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/signin")
                .loginProcessingUrl("/signin")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/signin?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return identifier -> userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .map(user -> User.builder()
                        .username(user.getEmail() != null ? user.getEmail() : user.getTelephone())
                        .password(user.getPassword())
                        .roles(user.getRole().name().replace("ROLE_", ""))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
