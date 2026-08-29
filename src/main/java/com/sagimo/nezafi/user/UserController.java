package com.sagimo.nezafi.user;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Verrouillé à ADMIN : cette API expose la création/modification de n'importe quel compte, y
// compris son rôle — déjà imposé par SecurityConfig (/api/users/** -> hasRole('ADMIN')), ce
// @PreAuthorize est une deuxième barrière au niveau du contrôleur.
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContratRepository contratRepository;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, ContratRepository contratRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contratRepository = contratRepository;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Object> createUser(@RequestBody User user) {
        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Cet email est déjà utilisé");
        }
        if (user.getTelephone() != null && userRepository.findByTelephone(user.getTelephone()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce numéro de téléphone est déjà utilisé");
        }

        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        User saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> updateUser(@PathVariable Long id, @RequestBody User user) {
        return userRepository.findById(id)
                .<ResponseEntity<Object>>map(existing -> {
                    boolean emailTaken = user.getEmail() != null && userRepository.findByEmail(user.getEmail())
                            .filter(other -> !other.getId().equals(id)).isPresent();
                    if (emailTaken) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body("Cet email est déjà utilisé");
                    }
                    boolean telephoneTaken = user.getTelephone() != null && userRepository.findByTelephone(user.getTelephone())
                            .filter(other -> !other.getId().equals(id)).isPresent();
                    if (telephoneTaken) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce numéro de téléphone est déjà utilisé");
                    }

                    existing.setNom(user.getNom());
                    existing.setPrenom(user.getPrenom());
                    existing.setTelephone(user.getTelephone());
                    existing.setEmail(user.getEmail());
                    existing.setNumeroCNI(user.getNumeroCNI());
                    existing.setRole(user.getRole());
                    if (user.getPassword() != null && !user.getPassword().isBlank()) {
                        existing.setPassword(passwordEncoder.encode(user.getPassword()));
                    }
                    return ResponseEntity.ok(userRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Un locataire supprimé entraîne en cascade (JPA cascade=ALL, cf. User.contrats) la
    // suppression de tous ses contrats, et transitivement de leurs échéances et paiements —
    // potentiellement un historique financier réel. Sans ?confirmer=true, cette suppression est
    // refusée (409) s'il existe un tel historique, plutôt que de l'effacer silencieusement.
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteUser(@PathVariable Long id,
                                              @RequestParam(required = false, defaultValue = "false") boolean confirmer) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<Contrat> contratsLies = contratRepository.findByLocataireId(id);
        if (!contratsLies.isEmpty() && !confirmer) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    "Ce compte a " + contratsLies.size() + " contrat(s) rattaché(s), avec leurs échéances et paiements : "
                            + "la suppression effacera aussi tout cet historique financier. "
                            + "Relancer la requête avec ?confirmer=true pour confirmer.");
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}