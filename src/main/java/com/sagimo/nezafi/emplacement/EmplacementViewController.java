package com.sagimo.nezafi.emplacement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/shops")
@PreAuthorize("hasRole('LOCATAIRE')")
public class EmplacementViewController {

    private final EmplacementRepository emplacementRepository;

    public EmplacementViewController(EmplacementRepository emplacementRepository) {
        this.emplacementRepository = emplacementRepository;
    }

    @GetMapping("/{id}")
    public String viewEmplacement(@PathVariable Long id, Model model) {
        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emplacement not found"));
        model.addAttribute("shop", emplacement);
        return "emplacement-detail";
    }

    @PostMapping("/{id}/update")
    public String updateEmplacement(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String imageUrl,
            @RequestParam String statut) {

        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Emplacement not found"));

        emplacement.setName(name);
        emplacement.setImageUrl(imageUrl);
        emplacement.setStatut(StatutEmplacement.valueOf(statut));

        emplacementRepository.save(emplacement);
        return "redirect:/dashboard";
    }
}
