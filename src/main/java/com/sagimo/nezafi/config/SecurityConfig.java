package com.sagimo.nezafi.config;

import com.sagimo.nezafi.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SecurityConfig {

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
//            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
//            .authorizeHttpRequests(auth -> auth
//                .requestMatchers("/", "/signup", "/signin", "/register", "/css/**", "/js/**", "/h2-console/**").permitAll()
//                .requestMatchers("/admin/**").hasRole("ADMIN")
//                .requestMatchers("/shops/**", "/contracts/**").hasRole("LOCATAIRE")
//                .requestMatchers("/api/**").hasAnyRole("ADMIN", "LOCATAIRE")
//                .anyRequest().authenticated()
//            )
//            .formLogin(form -> form
//                .loginPage("/signin")
//                .loginProcessingUrl("/signin")
//                .defaultSuccessUrl("/dashboard", true)
//                .failureUrl("/signin?error=true")
//                .permitAll()
//            )
//            .logout(logout -> logout
//                .logoutUrl("/logout")
//                .logoutSuccessUrl("/")
//                .permitAll());
//
//        return http.build();
//    }
//
//    @Bean
//    public UserDetailsService userDetailsService(UserRepository userRepository) {
//        return username -> userRepository.findByEmail(username)
//                .map(user -> User.builder()
//                        .username(user.getEmail())
//                        .password(user.getPassword())
//                        .roles(user.getRole().name().replace("ROLE_", ""))
//                        .build())
//                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
//    }
//
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Désactive CSRF si tu testes avec Postman/cURL
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Autorise TOUTES les requêtes sans exception
                );

        return http.build();
    }
}
