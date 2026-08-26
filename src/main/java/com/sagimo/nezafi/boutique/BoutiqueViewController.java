package com.sagimo.nezafi.boutique;

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
public class BoutiqueViewController {

    private final BoutiqueRepository boutiqueRepository;

    public BoutiqueViewController(BoutiqueRepository boutiqueRepository) {
        this.boutiqueRepository = boutiqueRepository;
    }

    @GetMapping("/{id}")
    public String viewBoutique(@PathVariable Long id, Model model) {
        Boutique boutique = boutiqueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Boutique not found"));
        model.addAttribute("shop", boutique);
        return "boutique-detail";
    }

    @PostMapping("/{id}/update")
    public String updateBoutique(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String imageUrl,
            @RequestParam String statut) {

        Boutique boutique = boutiqueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Boutique not found"));

        boutique.setName(name);
        boutique.setImageUrl(imageUrl);
        boutique.setStatut(StatutBoutique.valueOf(statut));

        boutiqueRepository.save(boutique);
        return "redirect:/dashboard";
    }
}
