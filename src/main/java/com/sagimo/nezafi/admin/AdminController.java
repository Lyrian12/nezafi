package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.audit.AuditService;
import com.sagimo.nezafi.audit.TypeActionAudit;
import com.sagimo.nezafi.emplacement.CategorieEmplacement;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.Palier;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.ContratStatusService;
import com.sagimo.nezafi.contrat.StatutCaution;
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.echeance.EcheanceRepository;
import com.sagimo.nezafi.echeance.StatutEcheance;
import com.sagimo.nezafi.echeance.TypeEcheance;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final EcheanceRepository echeanceRepository;
    private final AuditService auditService;

    public AdminController(EmplacementRepository emplacementRepository, ContratRepository contratRepository,
                            UserRepository userRepository, PasswordEncoder passwordEncoder,
                            ContratStatusService contratStatusService, EcheanceRepository echeanceRepository,
                            AuditService auditService) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contratStatusService = contratStatusService;
        this.echeanceRepository = echeanceRepository;
        this.auditService = auditService;
    }

    /** Résout l'admin actuellement connecté, pour attribuer les entrées du journal d'audit. */
    private User currentAdmin(Authentication authentication) {
        String identifier = authentication.getName();
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByTelephone(identifier))
                .orElseThrow(() -> new RuntimeException("Admin not found"));
    }

    /** Instantané des seuls champs de Contrat suivis par le journal d'audit. */
    private Map<String, Object> contratSnapshot(Contrat contrat) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("montantLoyer", contrat.getMontantLoyer());
        snapshot.put("statut", contrat.getStatut());
        snapshot.put("dateDebut", contrat.getDateDebut());
        snapshot.put("dateFin", contrat.getDateFin());
        snapshot.put("montantCaution", contrat.getMontantCaution());
        snapshot.put("statutCaution", contrat.getStatutCaution());
        snapshot.put("motifResiliation", contrat.getMotifResiliation());
        snapshot.put("datePreavis", contrat.getDatePreavis());
        return snapshot;
    }

    private boolean cautionMotifManquant(StatutCaution statutCaution, String motifRetenueCaution) {
        boolean retenue = statutCaution == StatutCaution.RETENUE_PARTIELLEMENT
                || statutCaution == StatutCaution.RETENUE_TOTALEMENT;
        return retenue && (motifRetenueCaution == null || motifRetenueCaution.isBlank());
    }

    private String rejectContractForm(Model model, String error, Contrat contrat) {
        model.addAttribute("error", error);
        model.addAttribute("contrat", contrat);
        model.addAttribute("emplacements", emplacementRepository.findAll());
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
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

        // Un emplacement occupé par un contrat actif est rattaché au client de ce contrat ;
        // s'il y a plusieurs contrats VALIDER pour le même emplacement (cas normalement
        // impossible en pratique), on garde le premier trouvé.
        Map<Long, User> clientByEmplacementId = new HashMap<>();
        for (Contrat contrat : contratRepository.findByStatut(StatutContrat.VALIDER)) {
            clientByEmplacementId.putIfAbsent(contrat.getEmplacement().getId(), contrat.getLocataire());
        }
        model.addAttribute("clientByEmplacementId", clientByEmplacementId);

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
            @RequestParam String categorie,
            Authentication authentication) {

        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        BigDecimal ancienPrix = emplacement.getPrix();

        emplacement.setName(name);
        emplacement.setImageUrl(imageUrl);
        emplacement.setStatut(StatutEmplacement.valueOf(statut));
        emplacement.setPalier(Palier.valueOf(palier));
        emplacement.setSuperficie(superficie);
        emplacement.setPrix(prix);
        emplacement.setCategorie(CategorieEmplacement.valueOf(categorie));

        emplacementRepository.save(emplacement);

        // Seul le prix d'une boutique est audité (pas le reste des champs de l'emplacement).
        if (ancienPrix == null || ancienPrix.compareTo(prix) != 0) {
            auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.MODIFICATION, "Emplacement", id,
                    Map.of("prix", ancienPrix == null ? "" : ancienPrix),
                    Map.of("prix", prix));
        }

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
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
    }

    @PostMapping("/contracts/add")
    public String addContract(
            @RequestParam Long emplacementId,
            @RequestParam Long locataireId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String termes,
            @RequestParam String statut,
            @RequestParam BigDecimal montantLoyer,
            @RequestParam Integer dureeLoyerMois,
            @RequestParam BigDecimal montantCaution,
            @RequestParam Integer dureeCautionMois,
            @RequestParam(defaultValue = "DETENUE") String statutCaution,
            @RequestParam(required = false) String motifRetenueCaution,
            @RequestParam(required = false) String datePreavis,
            @RequestParam(required = false) Long contratPrecedentId,
            @RequestParam(required = false) List<String> echeanceDates,
            @RequestParam(required = false) List<String> echeanceMontants,
            @RequestParam(required = false) List<String> echeanceTypes,
            Authentication authentication,
            Model model) {

        Emplacement emplacement = emplacementRepository.findById(emplacementId)
                .orElseThrow(() -> new RuntimeException("Store not found"));
        User locataire = userRepository.findById(locataireId)
                .orElseThrow(() -> new RuntimeException("Locataire not found"));

        StatutCaution statutCautionEnum = StatutCaution.valueOf(statutCaution);
        if (cautionMotifManquant(statutCautionEnum, motifRetenueCaution)) {
            Contrat rejected = new Contrat();
            rejected.setEmplacement(emplacement);
            rejected.setLocataire(locataire);
            rejected.setTermes(termes);
            rejected.setStatut(StatutContrat.valueOf(statut));
            rejected.setMontantLoyer(montantLoyer);
            rejected.setDureeLoyerMois(dureeLoyerMois);
            rejected.setMontantCaution(montantCaution);
            rejected.setDureeCautionMois(dureeCautionMois);
            rejected.setStatutCaution(statutCautionEnum);
            return rejectContractForm(model,
                    "Le motif est obligatoire quand la caution est retenue (partiellement ou totalement).", rejected);
        }

        Contrat contrat = new Contrat();
        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(LocalDate.parse(dateDebut));
        contrat.setDateFin(LocalDate.parse(dateFin));
        contrat.setTermes(termes);
        contrat.setStatut(StatutContrat.valueOf(statut));
        contrat.setMontantLoyer(montantLoyer);
        contrat.setDureeLoyerMois(dureeLoyerMois);
        contrat.setMontantCaution(montantCaution);
        contrat.setDureeCautionMois(dureeCautionMois);
        contrat.setStatutCaution(statutCautionEnum);
        contrat.setMotifRetenueCaution((motifRetenueCaution == null || motifRetenueCaution.isBlank())
                ? null : motifRetenueCaution.trim());
        contrat.setDatePreavis((datePreavis == null || datePreavis.isBlank()) ? null : LocalDate.parse(datePreavis));
        if (contratPrecedentId != null) {
            contratRepository.findById(contratPrecedentId).ifPresent(contrat::setContratPrecedent);
        }
        contratRepository.save(contrat);

        creerEcheances(contrat, echeanceDates, echeanceMontants, echeanceTypes);
        contratStatusService.syncEmplacementStatut(contrat);

        auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.CREATION, "Contrat", contrat.getId(),
                null, contratSnapshot(contrat));

        return "redirect:/admin/contracts/" + contrat.getId();
    }

    @GetMapping("/contracts/edit/{id}")
    public String editContractPage(@PathVariable Long id, Model model) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        model.addAttribute("contrat", contrat);
        model.addAttribute("emplacements", emplacementRepository.findAll());
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
    }

    @PostMapping("/contracts/edit/{id}")
    public String editContract(
            @PathVariable Long id,
            @RequestParam Long emplacementId,
            @RequestParam Long locataireId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String termes,
            @RequestParam String statut,
            @RequestParam BigDecimal montantLoyer,
            @RequestParam Integer dureeLoyerMois,
            @RequestParam BigDecimal montantCaution,
            @RequestParam Integer dureeCautionMois,
            @RequestParam(defaultValue = "DETENUE") String statutCaution,
            @RequestParam(required = false) String motifRetenueCaution,
            @RequestParam(required = false) String datePreavis,
            @RequestParam(required = false) List<String> echeanceDates,
            @RequestParam(required = false) List<String> echeanceMontants,
            @RequestParam(required = false) List<String> echeanceTypes,
            Authentication authentication,
            Model model) {

        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        Emplacement emplacement = emplacementRepository.findById(emplacementId)
                .orElseThrow(() -> new RuntimeException("Store not found"));
        User locataire = userRepository.findById(locataireId)
                .orElseThrow(() -> new RuntimeException("Locataire not found"));

        StatutCaution statutCautionEnum = StatutCaution.valueOf(statutCaution);
        if (cautionMotifManquant(statutCautionEnum, motifRetenueCaution)) {
            return rejectContractForm(model,
                    "Le motif est obligatoire quand la caution est retenue (partiellement ou totalement).", contrat);
        }

        // Le statut RESILIER n'est jamais soumis par le <select> du formulaire générique
        // (il n'y figure pas) : ce champ ne peut donc valoir RESILIER ici que si le contrat
        // l'était déjà avant cette édition, via le champ caché dédié du template.
        Map<String, Object> ancienSnapshot = contratSnapshot(contrat);

        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(LocalDate.parse(dateDebut));
        contrat.setDateFin(LocalDate.parse(dateFin));
        contrat.setTermes(termes);
        contrat.setStatut(StatutContrat.valueOf(statut));
        contrat.setMontantLoyer(montantLoyer);
        contrat.setDureeLoyerMois(dureeLoyerMois);
        contrat.setMontantCaution(montantCaution);
        contrat.setDureeCautionMois(dureeCautionMois);
        contrat.setStatutCaution(statutCautionEnum);
        contrat.setMotifRetenueCaution((motifRetenueCaution == null || motifRetenueCaution.isBlank())
                ? null : motifRetenueCaution.trim());
        contrat.setDatePreavis((datePreavis == null || datePreavis.isBlank()) ? null : LocalDate.parse(datePreavis));

        contratRepository.save(contrat);
        creerEcheances(contrat, echeanceDates, echeanceMontants, echeanceTypes);
        contratStatusService.syncEmplacementStatut(contrat);

        Map<String, Object> nouveauSnapshot = contratSnapshot(contrat);
        if (!ancienSnapshot.equals(nouveauSnapshot)) {
            auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.MODIFICATION, "Contrat",
                    contrat.getId(), ancienSnapshot, nouveauSnapshot);
        }

        return "redirect:/admin/contracts";
    }

    // Les échéances ne sont jamais générées automatiquement selon une périodicité :
    // chaque contrat a son propre échéancier négocié au cas par cas, saisi ici
    // manuellement par l'admin (dates/montants/types en listes parallèles depuis le
    // formulaire). Type par défaut LOYER si non renseigné.
    private void creerEcheances(Contrat contrat, List<String> dates, List<String> montants, List<String> types) {
        if (dates == null || montants == null) {
            return;
        }
        int n = Math.min(dates.size(), montants.size());
        for (int i = 0; i < n; i++) {
            String dateStr = dates.get(i);
            String montantStr = montants.get(i);
            if (dateStr == null || dateStr.isBlank() || montantStr == null || montantStr.isBlank()) {
                continue;
            }
            Echeance echeance = new Echeance();
            echeance.setContrat(contrat);
            echeance.setDateEcheance(LocalDate.parse(dateStr));
            echeance.setMontantDu(new BigDecimal(montantStr.trim()));
            echeance.setStatut(StatutEcheance.EN_ATTENTE);
            String typeStr = (types != null && i < types.size()) ? types.get(i) : null;
            echeance.setType((typeStr == null || typeStr.isBlank()) ? TypeEcheance.LOYER : TypeEcheance.valueOf(typeStr));
            echeanceRepository.save(echeance);
        }
    }

    @GetMapping("/contracts/delete/{id}")
    public String deleteContract(@PathVariable Long id, Authentication authentication) {
        contratRepository.findById(id).ifPresent(contrat -> {
            Map<String, Object> ancienSnapshot = contratSnapshot(contrat);
            // Un contrat supprimé ne pilote plus l'emplacement : il doit être libéré,
            // indépendamment du statut qu'avait le contrat avant sa suppression.
            contratStatusService.libererEmplacement(contrat.getEmplacement());
            contratRepository.deleteById(id);
            auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.SUPPRESSION, "Contrat", id,
                    ancienSnapshot, null);
        });
        return "redirect:/admin/contracts";
    }

    @PostMapping("/contracts/{id}/resilier")
    public String resilierContract(
            @PathVariable Long id,
            @RequestParam String motif,
            @RequestParam(required = false) String datePreavis,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (motif == null || motif.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Le motif de résiliation est obligatoire.");
            return "redirect:/admin/contracts/" + id;
        }

        Map<String, Object> ancienSnapshot = contratSnapshot(contrat);

        contrat.setStatut(StatutContrat.RESILIER);
        contrat.setMotifResiliation(motif.trim());
        if (datePreavis != null && !datePreavis.isBlank()) {
            contrat.setDatePreavis(LocalDate.parse(datePreavis));
        }
        contratRepository.save(contrat);
        contratStatusService.syncEmplacementStatut(contrat);

        auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.ANNULATION, "Contrat", contrat.getId(),
                ancienSnapshot, contratSnapshot(contrat));

        return "redirect:/admin/contracts/" + id;
    }

    @GetMapping("/contracts/{id}/renew")
    public String renewContractPage(@PathVariable Long id, Model model) {
        Contrat ancien = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        // Renouveler ne modifie jamais l'ancien contrat en place : on pré-remplit juste
        // le formulaire d'ajout avec les infos reprises de l'ancien, lié via contratPrecedent.
        Contrat nouveau = new Contrat();
        nouveau.setEmplacement(ancien.getEmplacement());
        nouveau.setLocataire(ancien.getLocataire());
        nouveau.setTermes(ancien.getTermes());
        nouveau.setStatut(StatutContrat.EN_ATTENTE);
        nouveau.setMontantLoyer(ancien.getMontantLoyer());
        nouveau.setDureeLoyerMois(ancien.getDureeLoyerMois());
        nouveau.setMontantCaution(ancien.getMontantCaution());
        nouveau.setDureeCautionMois(ancien.getDureeCautionMois());
        nouveau.setStatutCaution(StatutCaution.DETENUE);
        nouveau.setContratPrecedent(ancien);

        model.addAttribute("contrat", nouveau);
        model.addAttribute("emplacements", emplacementRepository.findAll());
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
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
