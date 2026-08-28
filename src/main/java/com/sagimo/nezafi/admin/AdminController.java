package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.emplacement.CategorieEmplacement;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.Palier;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.ContratStatusService;
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContratStatusService contratStatusService;

    public AdminController(EmplacementRepository emplacementRepository, ContratRepository contratRepository,
                            UserRepository userRepository, PasswordEncoder passwordEncoder,
                            ContratStatusService contratStatusService) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contratStatusService = contratStatusService;
    }

    // Store Management
    @GetMapping("/stores")
    public String storesPage(Model model) {
        List<Emplacement> allStores = emplacementRepository.findAll();
        model.addAttribute("stores", allStores);
        model.addAttribute("totalStores", allStores.size());
        model.addAttribute("activeStoresCount", allStores.stream()
                .filter(b -> b.getStatut() == StatutEmplacement.DISPONIBLE).count());

        List<Emplacement> occupiedStores = allStores.stream()
                .filter(b -> b.getStatut() == StatutEmplacement.NON_DISPONIBLE)
                .toList();
        model.addAttribute("pendingStoresCount", occupiedStores.size());

        BigDecimal revenueToday = occupiedStores.stream()
                .map(Emplacement::getPrix)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("revenueToday", revenueToday);

        List<PalierOccupancy> occupancyByPalier = Arrays.stream(Palier.values())
                .map(palier -> {
                    long total = allStores.stream().filter(b -> b.getPalier() == palier).count();
                    long occupied = allStores.stream()
                            .filter(b -> b.getPalier() == palier && b.getStatut() == StatutEmplacement.NON_DISPONIBLE)
                            .count();
                    double percentage = total == 0 ? 0.0 : (occupied * 100.0 / total);
                    return new PalierOccupancy(palier, occupied, total, percentage);
                })
                .toList();
        model.addAttribute("occupancyByPalier", occupancyByPalier);

        return "admin-stores";
    }

    @GetMapping("/stores/add")
    public String addStorePage(Model model) {
        model.addAttribute("emplacement", new Emplacement());
        return "admin-store-form";
    }

    @PostMapping("/stores/add")
    public String addStore(
            @RequestParam String name,
            @RequestParam String imageUrl,
            @RequestParam(defaultValue = "DISPONIBLE") String statut,
            @RequestParam String palier,
            @RequestParam BigDecimal superficie,
            @RequestParam BigDecimal prix,
            @RequestParam String categorie) {

        Emplacement emplacement = new Emplacement();
        emplacement.setName(name);
        emplacement.setImageUrl(imageUrl);
        emplacement.setStatut(StatutEmplacement.valueOf(statut));
        emplacement.setPalier(Palier.valueOf(palier));
        emplacement.setSuperficie(superficie);
        emplacement.setPrix(prix);
        emplacement.setCategorie(CategorieEmplacement.valueOf(categorie));
        emplacement.setAddedAt(LocalDateTime.now());

        emplacementRepository.save(emplacement);
        return "redirect:/admin/stores";
    }

    @GetMapping("/stores/edit/{id}")
    public String editStorePage(@PathVariable Long id, Model model) {
        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));
        model.addAttribute("emplacement", emplacement);
        return "admin-store-form";
    }

    @PostMapping("/stores/edit/{id}")
    public String editStore(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String imageUrl,
            @RequestParam String statut,
            @RequestParam String palier,
            @RequestParam BigDecimal superficie,
            @RequestParam BigDecimal prix,
            @RequestParam String categorie) {

        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        emplacement.setName(name);
        emplacement.setImageUrl(imageUrl);
        emplacement.setStatut(StatutEmplacement.valueOf(statut));
        emplacement.setPalier(Palier.valueOf(palier));
        emplacement.setSuperficie(superficie);
        emplacement.setPrix(prix);
        emplacement.setCategorie(CategorieEmplacement.valueOf(categorie));

        emplacementRepository.save(emplacement);
        return "redirect:/admin/stores";
    }

    @GetMapping("/stores/delete/{id}")
    public String deleteStore(@PathVariable Long id) {
        emplacementRepository.deleteById(id);
        return "redirect:/admin/stores";
    }

    // Contract Management
    @GetMapping("/contracts")
    public String contractsPage(Model model) {
        List<Contrat> allContracts = contratRepository.findAll();
        model.addAttribute("contracts", allContracts);
        model.addAttribute("totalContracts", allContracts.size());
        model.addAttribute("activeContracts", allContracts.stream()
                .filter(c -> c.getStatut() == StatutContrat.VALIDER).count());
        model.addAttribute("pendingContracts", allContracts.stream()
                .filter(c -> c.getStatut() == StatutContrat.EN_ATTENTE).count());

        LocalDate today = LocalDate.now();
        List<Contrat> expiringContracts = contratRepository.findByStatutAndDateFinBetweenOrderByDateFinAsc(
                StatutContrat.VALIDER, today, today.plusDays(30));
        model.addAttribute("expiringContracts", expiringContracts);

        return "admin-contracts";
    }

    @GetMapping("/contracts/add")
    public String addContractPage(Model model) {
        model.addAttribute("contrat", new Contrat());
        model.addAttribute("emplacements", emplacementRepository.findAll());
        return "admin-contract-form";
    }

    @GetMapping("/contracts/edit/{id}")
    public String editContractPage(@PathVariable Long id, Model model) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        model.addAttribute("contrat", contrat);
        model.addAttribute("emplacements", emplacementRepository.findAll());
        return "admin-contract-form";
    }

    @PostMapping("/contracts/edit/{id}")
    public String editContract(
            @PathVariable Long id,
            @RequestParam Long emplacementId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam String termes,
            @RequestParam String statut) {

        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        Emplacement emplacement = emplacementRepository.findById(emplacementId)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        contrat.setEmplacement(emplacement);
        contrat.setTermes(termes);
        contrat.setStatut(StatutContrat.valueOf(statut));

        contratRepository.save(contrat);
        contratStatusService.syncEmplacementStatut(contrat);
        return "redirect:/admin/contracts";
    }

    @GetMapping("/contracts/delete/{id}")
    public String deleteContract(@PathVariable Long id) {
        contratRepository.findById(id).ifPresent(contrat -> {
            // Un contrat supprimé ne pilote plus l'emplacement : il doit être libéré,
            // indépendamment du statut qu'avait le contrat avant sa suppression.
            contratStatusService.libererEmplacement(contrat.getEmplacement());
            contratRepository.deleteById(id);
        });
        return "redirect:/admin/contracts";
    }

    @GetMapping("/contracts/export")
    public void exportActiveContracts(HttpServletResponse response) throws IOException {
        List<Contrat> activeContracts = contratRepository.findByStatut(StatutContrat.VALIDER);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"contrats_actifs.csv\"");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        PrintWriter writer = response.getWriter();
        writer.write(0xFEFF); // BOM UTF-8 pour un affichage correct des accents dans Excel
        writer.println("Emplacement;Locataire;Loyer (FCFA);Date de début;Date de fin");

        for (Contrat contrat : activeContracts) {
            BigDecimal loyer = contrat.getEmplacement().getPrix();
            writer.println(String.join(";",
                    csvField(contrat.getEmplacement().getName()),
                    csvField(contrat.getLocataire().getPrenom() + " " + contrat.getLocataire().getNom()),
                    loyer != null ? loyer.toPlainString() : "",
                    contrat.getDateDebut().format(dateFormatter),
                    contrat.getDateFin().format(dateFormatter)));
        }
        writer.flush();
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // Client Management
    @GetMapping("/clients")
    public String clientsPage(@RequestParam(required = false) String search, Model model) {
        List<User> clients = (search == null || search.isBlank())
                ? userRepository.findByRole(Role.ROLE_LOCATAIRE)
                : userRepository.searchByRoleAndNomOrPrenomOrTelephone(Role.ROLE_LOCATAIRE, search.trim());

        model.addAttribute("clients", clients);
        model.addAttribute("search", search);
        model.addAttribute("totalClients", userRepository.findByRole(Role.ROLE_LOCATAIRE).size());
        return "admin-clients";
    }

    @GetMapping("/clients/add")
    public String addClientPage(Model model) {
        model.addAttribute("client", new User());
        return "admin-client-form";
    }

    @PostMapping("/clients/add")
    public String addClient(
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam String telephone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String numeroCNI,
            Model model) {

        String normalizedEmail = (email == null || email.isBlank()) ? null : email.trim();
        String trimmedTelephone = telephone.trim();

        if (normalizedEmail != null && userRepository.findByEmail(normalizedEmail).isPresent()) {
            return rejectClientForm(model, "Cet email est déjà utilisé par un autre client",
                    nom, prenom, trimmedTelephone, normalizedEmail, numeroCNI);
        }

        if (userRepository.findByTelephone(trimmedTelephone).isPresent()) {
            return rejectClientForm(model, "Ce numéro de téléphone est déjà utilisé par un autre client",
                    nom, prenom, trimmedTelephone, normalizedEmail, numeroCNI);
        }

        User client = new User();
        client.setNom(nom);
        client.setPrenom(prenom);
        client.setTelephone(trimmedTelephone);
        client.setEmail(normalizedEmail);
        client.setNumeroCNI((numeroCNI == null || numeroCNI.isBlank()) ? null : numeroCNI.trim());
        client.setRole(Role.ROLE_LOCATAIRE);
        // Compte créé par l'admin : mot de passe aléatoire inutilisable tant qu'aucune
        // procédure de réinitialisation de mot de passe n'existe (le client ne peut donc
        // pas se connecter lui-même via ce chemin, cf. exigence "dossier géré par l'admin").
        client.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        userRepository.save(client);
        return "redirect:/admin/clients";
    }

    private String rejectClientForm(Model model, String error, String nom, String prenom,
                                     String telephone, String email, String numeroCNI) {
        model.addAttribute("error", error);
        User rejected = new User();
        rejected.setNom(nom);
        rejected.setPrenom(prenom);
        rejected.setTelephone(telephone);
        rejected.setEmail(email);
        rejected.setNumeroCNI(numeroCNI);
        model.addAttribute("client", rejected);
        return "admin-client-form";
    }

    @GetMapping("/clients/{id}")
    public String clientDetail(@PathVariable Long id, Model model) {
        User client = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.ROLE_LOCATAIRE)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        model.addAttribute("client", client);
        model.addAttribute("contrats", contratRepository.findByLocataireId(id));
        return "admin-client-detail";
    }
}
