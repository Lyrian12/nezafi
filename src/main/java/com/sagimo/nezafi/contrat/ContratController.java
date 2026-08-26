package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.boutique.Boutique;
import com.sagimo.nezafi.boutique.BoutiqueRepository;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contrats")
public class ContratController {

    private final ContratRepository contratRepository;
    private final BoutiqueRepository boutiqueRepository;
    private final UserRepository userRepository;

    public ContratController(ContratRepository contratRepository,
                            BoutiqueRepository boutiqueRepository,
                            UserRepository userRepository) {
        this.contratRepository = contratRepository;
        this.boutiqueRepository = boutiqueRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Contrat> getAllContrats() {
        return contratRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contrat> getContratById(@PathVariable Long id) {
        return contratRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/boutique/{boutiqueId}")
    public List<Contrat> getContratsByBoutique(@PathVariable Long boutiqueId) {
        return contratRepository.findByBoutiqueId(boutiqueId);
    }

    @GetMapping("/locataire/{locataireId}")
    public List<Contrat> getContratsByLocataire(@PathVariable Long locataireId) {
        return contratRepository.findByLocataireId(locataireId);
    }

    @PostMapping
    public ResponseEntity<Contrat> createContrat(@RequestBody Contrat contrat) {
        Boutique boutique = boutiqueRepository.findById(contrat.getBoutique().getId()).orElse(null);
        User locataire = userRepository.findById(contrat.getLocataire().getId()).orElse(null);

        if (boutique == null || locataire == null) {
            return ResponseEntity.badRequest().build();
        }

        contrat.setBoutique(boutique);
        contrat.setLocataire(locataire);
        Contrat saved = contratRepository.save(contrat);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contrat> updateContrat(@PathVariable Long id, @RequestBody Contrat contrat) {
        return contratRepository.findById(id)
                .map(existing -> {
                    if (contrat.getBoutique() != null && contrat.getBoutique().getId() != null) {
                        Boutique boutique = boutiqueRepository.findById(contrat.getBoutique().getId()).orElse(null);
                        if (boutique == null) {
                            return ResponseEntity.badRequest().<Contrat>build();
                        }
                        existing.setBoutique(boutique);
                    }

                    if (contrat.getLocataire() != null && contrat.getLocataire().getId() != null) {
                        User locataire = userRepository.findById(contrat.getLocataire().getId()).orElse(null);
                        if (locataire == null) {
                            return ResponseEntity.badRequest().<Contrat>build();
                        }
                        existing.setLocataire(locataire);
                    }

                    if (contrat.getDateDebut() != null) {
                        existing.setDateDebut(contrat.getDateDebut());
                    }
                    if (contrat.getDateFin() != null) {
                        existing.setDateFin(contrat.getDateFin());
                    }
                    if (contrat.getTermes() != null) {
                        existing.setTermes(contrat.getTermes());
                    }
                    if (contrat.getStatut() != null) {
                        existing.setStatut(contrat.getStatut());
                    }

                    return ResponseEntity.ok(contratRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContrat(@PathVariable Long id) {
        if (!contratRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        contratRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
