package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.boutique.Boutique;
import com.sagimo.nezafi.boutique.BoutiqueRepository;
import com.sagimo.nezafi.boutique.StatutBoutique;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.StatutContrat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final BoutiqueRepository boutiqueRepository;
    private final ContratRepository contratRepository;

    public AdminController(BoutiqueRepository boutiqueRepository, ContratRepository contratRepository) {
        this.boutiqueRepository = boutiqueRepository;
        this.contratRepository = contratRepository;
    }

    // Store Management
    @GetMapping("/stores")
    public String storesPage(Model model) {
        model.addAttribute("stores", boutiqueRepository.findAll());
        model.addAttribute("totalStores", boutiqueRepository.count());
        model.addAttribute("activeStoresCount", boutiqueRepository.findAll().stream()
                .filter(b -> b.getStatut() == StatutBoutique.DISPONIBLE).count());
        model.addAttribute("pendingStoresCount", boutiqueRepository.findAll().stream()
                .filter(b -> b.getStatut() == StatutBoutique.NON_DISPONIBLE).count());
        model.addAttribute("revenueToday", "600 fcfa ");
        return "admin-stores";
    }

    @GetMapping("/stores/add")
    public String addStorePage(Model model) {
        model.addAttribute("boutique", new Boutique());
        return "admin-store-form";
    }

    @PostMapping("/stores/add")
    public String addStore(
            @RequestParam String name,
            @RequestParam String imageUrl,
            @RequestParam(defaultValue = "DISPONIBLE") String statut) {

        Boutique boutique = new Boutique();
        boutique.setName(name);
        boutique.setImageUrl(imageUrl);
        boutique.setStatut(StatutBoutique.valueOf(statut));
        boutique.setAddedAt(LocalDateTime.now());

        boutiqueRepository.save(boutique);
        return "redirect:/admin/stores";
    }

    @GetMapping("/stores/edit/{id}")
    public String editStorePage(@PathVariable Long id, Model model) {
        Boutique boutique = boutiqueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));
        model.addAttribute("boutique", boutique);
        return "admin-store-form";
    }

    @PostMapping("/stores/edit/{id}")
    public String editStore(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String imageUrl,
            @RequestParam String statut) {

        Boutique boutique = boutiqueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        boutique.setName(name);
        boutique.setImageUrl(imageUrl);
        boutique.setStatut(StatutBoutique.valueOf(statut));

        boutiqueRepository.save(boutique);
        return "redirect:/admin/stores";
    }

    @GetMapping("/stores/delete/{id}")
    public String deleteStore(@PathVariable Long id) {
        boutiqueRepository.deleteById(id);
        return "redirect:/admin/stores";
    }

    // Contract Management
    @GetMapping("/contracts")
    public String contractsPage(Model model) {
        model.addAttribute("contracts", contratRepository.findAll());
        model.addAttribute("totalContracts", contratRepository.count());
        model.addAttribute("activeContracts", contratRepository.findAll().stream()
                .filter(c -> c.getStatut() == StatutContrat.VALIDER).count());
        model.addAttribute("pendingContracts", contratRepository.findAll().stream()
                .filter(c -> c.getStatut() == StatutContrat.EN_ATTENTE).count());
        return "admin-contracts";
    }

    @GetMapping("/contracts/add")
    public String addContractPage(Model model) {
        model.addAttribute("contrat", new Contrat());
        model.addAttribute("boutiques", boutiqueRepository.findAll());
        return "admin-contract-form";
    }

    @GetMapping("/contracts/edit/{id}")
    public String editContractPage(@PathVariable Long id, Model model) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        model.addAttribute("contrat", contrat);
        model.addAttribute("boutiques", boutiqueRepository.findAll());
        return "admin-contract-form";
    }

    @PostMapping("/contracts/edit/{id}")
    public String editContract(
            @PathVariable Long id,
            @RequestParam Long boutiqueId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam String termes,
            @RequestParam String statut) {

        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        Boutique boutique = boutiqueRepository.findById(boutiqueId)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        contrat.setBoutique(boutique);
        contrat.setTermes(termes);
        contrat.setStatut(StatutContrat.valueOf(statut));

        contratRepository.save(contrat);
        return "redirect:/admin/contracts";
    }

    @GetMapping("/contracts/delete/{id}")
    public String deleteContract(@PathVariable Long id) {
        contratRepository.deleteById(id);
        return "redirect:/admin/contracts";
    }
}
