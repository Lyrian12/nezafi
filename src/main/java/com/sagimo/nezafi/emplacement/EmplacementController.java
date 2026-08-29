package com.sagimo.nezafi.emplacement;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emplacements")
public class EmplacementController {

    private final EmplacementRepository emplacementRepository;

    public EmplacementController(EmplacementRepository emplacementRepository) {
        this.emplacementRepository = emplacementRepository;
    }

    @GetMapping
    public List<Emplacement> getAllEmplacements() {
        return emplacementRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Emplacement> getEmplacementById(@PathVariable Long id) {
        return emplacementRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Emplacement> createEmplacement(@RequestBody Emplacement emplacement) {
        Emplacement saved = emplacementRepository.save(emplacement);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Emplacement> updateEmplacement(@PathVariable Long id, @RequestBody Emplacement emplacement) {
        return emplacementRepository.findById(id)
                .map(existing -> {
                    existing.setName(emplacement.getName());
                    existing.setImageUrl(emplacement.getImageUrl());
                    existing.setStatut(emplacement.getStatut());
                    existing.setPalier(emplacement.getPalier());
                    existing.setSuperficie(emplacement.getSuperficie());
                    existing.setPrix(emplacement.getPrix());
                    existing.setCategorie(emplacement.getCategorie());
                    if (emplacement.getAddedAt() != null) {
                        existing.setAddedAt(emplacement.getAddedAt());
                    }
                    return ResponseEntity.ok(emplacementRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmplacement(@PathVariable Long id) {
        if (!emplacementRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        emplacementRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
