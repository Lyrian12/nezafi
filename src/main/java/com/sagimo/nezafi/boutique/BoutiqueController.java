package com.sagimo.nezafi.boutique;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boutiques")
public class BoutiqueController {

    private final BoutiqueRepository boutiqueRepository;

    public BoutiqueController(BoutiqueRepository boutiqueRepository) {
        this.boutiqueRepository = boutiqueRepository;
    }

    @GetMapping
    public List<Boutique> getAllBoutiques() {
        return boutiqueRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Boutique> getBoutiqueById(@PathVariable Long id) {
        return boutiqueRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Boutique> createBoutique(@RequestBody Boutique boutique) {
        Boutique saved = boutiqueRepository.save(boutique);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Boutique> updateBoutique(@PathVariable Long id, @RequestBody Boutique boutique) {
        return boutiqueRepository.findById(id)
                .map(existing -> {
                    existing.setName(boutique.getName());
                    existing.setImageUrl(boutique.getImageUrl());
                    existing.setStatut(boutique.getStatut());
                    existing.setPalier(boutique.getPalier());
                    existing.setSuperficie(boutique.getSuperficie());
                    existing.setPrix(boutique.getPrix());
                    existing.setCategorie(boutique.getCategorie());
                    if (boutique.getAddedAt() != null) {
                        existing.setAddedAt(boutique.getAddedAt());
                    }
                    return ResponseEntity.ok(boutiqueRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBoutique(@PathVariable Long id) {
        if (!boutiqueRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        boutiqueRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
