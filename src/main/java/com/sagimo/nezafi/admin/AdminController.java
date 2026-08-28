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
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.echeance.EcheanceRepository;
import com.sagimo.nezafi.echeance.EcheanceStatusService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private final EcheanceStatusService echeanceStatusService;
    private final AuditService auditService;
    private final AdminAlertService adminAlertService;

    public AdminController(EmplacementRepository emplacementRepository, ContratRepository contratRepository,
                            UserRepository userRepository, PasswordEncoder passwordEncoder,
                            ContratStatusService contratStatusService, EcheanceRepository echeanceRepository,
                            EcheanceStatusService echeanceStatusService, AuditService auditService,
                            AdminAlertService adminAlertService) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contratStatusService = contratStatusService;
        this.echeanceRepository = echeanceRepository;
        this.echeanceStatusService = echeanceStatusService;
        this.auditService = auditService;
        this.adminAlertService = adminAlertService;
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
        snapshot.put("motifResiliation", contrat.getMotifResiliation());
        snapshot.put("datePreavis", contrat.getDatePreavis());
        return snapshot;
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
    public String storesPage(
            @RequestParam(required = false) Palier palier,
            @RequestParam(required = false) StatutEmplacement statut,
            @RequestParam(required = false) CategorieEmplacement categorie,
            @RequestParam(required = false) BigDecimal prixMin,
            @RequestParam(required = false) BigDecimal prixMax,
            @RequestParam(required = false) BigDecimal superficieMin,
            @RequestParam(required = false) BigDecimal superficieMax,
            @RequestParam(required = false) StatutEcheance echeanceStatut,
            Model model) {
        List<Emplacement> allStores = emplacementRepository.findAll();

        List<Emplacement> filteredStores = allStores.stream()
                .filter(e -> palier == null || e.getPalier() == palier)
                .filter(e -> statut == null || e.getStatut() == statut)
                .filter(e -> categorie == null || e.getCategorie() == categorie)
                .filter(e -> prixMin == null || (e.getPrix() != null && e.getPrix().compareTo(prixMin) >= 0))
                .filter(e -> prixMax == null || (e.getPrix() != null && e.getPrix().compareTo(prixMax) <= 0))
                .filter(e -> superficieMin == null || (e.getSuperficie() != null && e.getSuperficie().compareTo(superficieMin) >= 0))
                .filter(e -> superficieMax == null || (e.getSuperficie() != null && e.getSuperficie().compareTo(superficieMax) <= 0))
                .toList();
        model.addAttribute("stores", filteredStores);
        model.addAttribute("palierFiltre", palier);
        model.addAttribute("statutFiltre", statut);
        model.addAttribute("categorieFiltre", categorie);
        model.addAttribute("prixMin", prixMin);
        model.addAttribute("prixMax", prixMax);
        model.addAttribute("superficieMin", superficieMin);
        model.addAttribute("superficieMax", superficieMax);
        model.addAttribute("paliers", Palier.values());
        model.addAttribute("statuts", StatutEmplacement.values());
        model.addAttribute("categories", CategorieEmplacement.values());

        // Widget de synthèse des incohérences non bloquantes (cf. AdminAlertService) :
        // affiché en haut de cette page, qui sert de tableau de bord admin.
        model.addAttribute("emplacementsOrphelins", adminAlertService.emplacementsNonDisponiblesSansContratActif());
        model.addAttribute("contratsEcartLoyer", adminAlertService.contratsAvecEcartLoyerSignificatif());
        model.addAttribute("echeancesExcedentaires", adminAlertService.echeancesAvecPaiementExcedentaire());

        // Widget "Échéances" : toutes les échéances de tous les contrats (pas seulement
        // celles en retard), avec le même recalcul paresseux de statut que les autres vues,
        // et un filtre optionnel sur le statut.
        List<Echeance> toutesEcheances = echeanceRepository.findAll();
        echeanceStatusService.rafraichirStatuts(toutesEcheances);
        List<Echeance> echeancesDashboard = toutesEcheances.stream()
                .filter(e -> echeanceStatut == null || e.getStatut() == echeanceStatut)
                .sorted(Comparator.comparing(Echeance::getDateEcheance))
                .toList();
        Map<Long, BigDecimal> totalPayeParEcheanceDashboard = new HashMap<>();
        for (Echeance echeance : echeancesDashboard) {
            totalPayeParEcheanceDashboard.put(echeance.getId(), echeanceStatusService.totalPaye(echeance.getId()));
        }
        model.addAttribute("echeancesDashboard", echeancesDashboard);
        model.addAttribute("totalPayeParEcheanceDashboard", totalPayeParEcheanceDashboard);
        model.addAttribute("echeanceStatutFiltre", echeanceStatut);
        model.addAttribute("echeanceStatuts", StatutEcheance.values());

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
                .map(p -> {
                    long total = allStores.stream().filter(b -> b.getPalier() == p).count();
                    long occupied = allStores.stream()
                            .filter(b -> b.getPalier() == p && b.getStatut() == StatutEmplacement.NON_DISPONIBLE)
                            .count();
                    double percentage = total == 0 ? 0.0 : (occupied * 100.0 / total);
                    return new PalierOccupancy(p, occupied, total, percentage);
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

    @GetMapping("/stores/{id}")
    public String storeDetail(@PathVariable Long id, Model model) {
        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        List<Contrat> contrats = contratRepository.findByEmplacementId(id);
        contrats.sort(Comparator.comparing(Contrat::getDateDebut).reversed());

        model.addAttribute("emplacement", emplacement);
        model.addAttribute("contrats", contrats);
        model.addAttribute("estOrpheline", emplacement.getStatut() == StatutEmplacement.NON_DISPONIBLE
                && adminAlertService.sansContratActif(emplacement));
        return "admin-store-detail";
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
    public String contractsPage(
            @RequestParam(required = false) StatutContrat statut,
            @RequestParam(required = false) String search,
            Model model) {
        List<Contrat> allContracts = contratRepository.findAll();

        String terme = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        List<Contrat> filteredContracts = allContracts.stream()
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> terme == null
                        || c.getEmplacement().getName().toLowerCase().contains(terme)
                        || c.getLocataire().getNom().toLowerCase().contains(terme)
                        || c.getLocataire().getPrenom().toLowerCase().contains(terme))
                .toList();
        model.addAttribute("contracts", filteredContracts);
        model.addAttribute("statutFiltre", statut);
        model.addAttribute("search", search);
        model.addAttribute("statuts", StatutContrat.values());
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

        LocalDate dateDebutParsed = LocalDate.parse(dateDebut);
        LocalDate dateFinParsed = LocalDate.parse(dateFin);
        if (dateDebutParsed.isAfter(dateFinParsed)) {
            return rejectContractForm(model, "La date de début ne peut pas être postérieure à la date de fin.",
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, statut, montantLoyer,
                            dureeLoyerMois, montantCaution, dureeCautionMois));
        }

        StatutContrat statutEnum = StatutContrat.valueOf(statut);
        if (statutEnum == StatutContrat.VALIDER && aUnAutreContratValide(emplacement.getId(), null)) {
            return rejectContractForm(model,
                    "Cet emplacement a déjà un contrat validé en cours : résiliez-le avant d'en valider un nouveau.",
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, statut, montantLoyer,
                            dureeLoyerMois, montantCaution, dureeCautionMois));
        }

        Contrat contratPrecedent = null;
        if (contratPrecedentId != null) {
            contratPrecedent = contratRepository.findById(contratPrecedentId).orElse(null);
            if (contratPrecedent != null && !peutEtreRenouvele(contratPrecedent)) {
                return rejectContractForm(model,
                        "Le renouvellement n'est possible qu'à partir d'un contrat résilié ou expiré.",
                        contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, statut, montantLoyer,
                                dureeLoyerMois, montantCaution, dureeCautionMois));
            }
        }

        Contrat contrat = new Contrat();
        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(dateDebutParsed);
        contrat.setDateFin(dateFinParsed);
        contrat.setTermes(termes);
        contrat.setStatut(statutEnum);
        contrat.setMontantLoyer(montantLoyer);
        contrat.setDureeLoyerMois(dureeLoyerMois);
        contrat.setMontantCaution(montantCaution);
        contrat.setDureeCautionMois(dureeCautionMois);
        contrat.setDatePreavis((datePreavis == null || datePreavis.isBlank()) ? null : LocalDate.parse(datePreavis));
        contrat.setContratPrecedent(contratPrecedent);

        List<Echeance> echeances = new ArrayList<>();
        String erreurEcheances = construireEcheances(contrat, echeanceDates, echeanceMontants, echeanceTypes, echeances);
        if (erreurEcheances != null) {
            return rejectContractForm(model, erreurEcheances,
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, statut, montantLoyer,
                            dureeLoyerMois, montantCaution, dureeCautionMois));
        }

        contratRepository.save(contrat);
        echeances.forEach(echeanceRepository::save);
        contratStatusService.syncEmplacementStatut(contrat);

        auditService.enregistrer(currentAdmin(authentication), TypeActionAudit.CREATION, "Contrat", contrat.getId(),
                null, contratSnapshot(contrat));

        return "redirect:/admin/contracts/" + contrat.getId();
    }

    /** Reconstruit un Contrat non persisté pour re-remplir le formulaire après un rejet. */
    private Contrat contratPourRejet(Emplacement emplacement, User locataire, String dateDebut, String dateFin,
                                      String termes, String statut, BigDecimal montantLoyer, Integer dureeLoyerMois,
                                      BigDecimal montantCaution, Integer dureeCautionMois) {
        Contrat rejected = new Contrat();
        rejected.setEmplacement(emplacement);
        rejected.setLocataire(locataire);
        try {
            rejected.setDateDebut(LocalDate.parse(dateDebut));
            rejected.setDateFin(LocalDate.parse(dateFin));
        } catch (RuntimeException ignored) {
            // Dates invalides ou absentes : le formulaire les affichera simplement vides.
        }
        rejected.setTermes(termes);
        rejected.setStatut(StatutContrat.valueOf(statut));
        rejected.setMontantLoyer(montantLoyer);
        rejected.setDureeLoyerMois(dureeLoyerMois);
        rejected.setMontantCaution(montantCaution);
        rejected.setDureeCautionMois(dureeCautionMois);
        return rejected;
    }

    /**
     * Un emplacement ne peut pas avoir deux contrats VALIDER actifs simultanément.
     * {@code excludeContratId} permet d'ignorer le contrat en cours d'édition lui-même.
     */
    private boolean aUnAutreContratValide(Long emplacementId, Long excludeContratId) {
        return contratRepository.findByEmplacementId(emplacementId).stream()
                .filter(c -> excludeContratId == null || !c.getId().equals(excludeContratId))
                .anyMatch(c -> c.getStatut() == StatutContrat.VALIDER);
    }

    /** Un contrat ne peut être renouvelé que s'il est résilié, ou si sa date de fin est dépassée. */
    private boolean peutEtreRenouvele(Contrat contrat) {
        return contrat.getStatut() == StatutContrat.RESILIER
                || (contrat.getDateFin() != null && contrat.getDateFin().isBefore(LocalDate.now()));
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

        LocalDate dateDebutParsed = LocalDate.parse(dateDebut);
        LocalDate dateFinParsed = LocalDate.parse(dateFin);
        if (dateDebutParsed.isAfter(dateFinParsed)) {
            return rejectContractForm(model, "La date de début ne peut pas être postérieure à la date de fin.", contrat);
        }

        StatutContrat statutEnum = StatutContrat.valueOf(statut);
        if (statutEnum == StatutContrat.VALIDER && aUnAutreContratValide(emplacementId, contrat.getId())) {
            return rejectContractForm(model,
                    "Cet emplacement a déjà un contrat validé en cours : résiliez-le avant d'en valider un nouveau.", contrat);
        }

        // Le statut RESILIER n'est jamais soumis par le <select> du formulaire générique
        // (il n'y figure pas) : ce champ ne peut donc valoir RESILIER ici que si le contrat
        // l'était déjà avant cette édition, via le champ caché dédié du template.
        Map<String, Object> ancienSnapshot = contratSnapshot(contrat);

        // Validation des échéances contre la nouvelle période avant toute modification de
        // l'entité gérée (JPA) : on valide sur une copie de travail des dates, pas sur
        // `contrat` lui-même, pour ne rien modifier en base si le formulaire est rejeté.
        Contrat periodeCandidate = new Contrat();
        periodeCandidate.setDateDebut(dateDebutParsed);
        periodeCandidate.setDateFin(dateFinParsed);
        List<Echeance> echeances = new ArrayList<>();
        String erreurEcheances = construireEcheances(periodeCandidate, echeanceDates, echeanceMontants, echeanceTypes, echeances);
        if (erreurEcheances != null) {
            return rejectContractForm(model, erreurEcheances, contrat);
        }

        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(dateDebutParsed);
        contrat.setDateFin(dateFinParsed);
        contrat.setTermes(termes);
        contrat.setStatut(statutEnum);
        contrat.setMontantLoyer(montantLoyer);
        contrat.setDureeLoyerMois(dureeLoyerMois);
        contrat.setMontantCaution(montantCaution);
        contrat.setDureeCautionMois(dureeCautionMois);
        contrat.setDatePreavis((datePreavis == null || datePreavis.isBlank()) ? null : LocalDate.parse(datePreavis));

        contratRepository.save(contrat);
        echeances.forEach(e -> { e.setContrat(contrat); echeanceRepository.save(e); });
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
    //
    // Ne persiste rien : construit la liste d'Echeance en mémoire (ajoutées à `out`) et
    // renvoie un message d'erreur dès qu'une date d'échéance sort de la période du
    // contrat — pour permettre au contrôleur de rejeter tout le formulaire (contrat compris)
    // avant d'écrire quoi que ce soit en base si une seule échéance est invalide.
    private String construireEcheances(Contrat contrat, List<String> dates, List<String> montants, List<String> types,
                                        List<Echeance> out) {
        if (dates == null || montants == null) {
            return null;
        }
        int n = Math.min(dates.size(), montants.size());
        for (int i = 0; i < n; i++) {
            String dateStr = dates.get(i);
            String montantStr = montants.get(i);
            if (dateStr == null || dateStr.isBlank() || montantStr == null || montantStr.isBlank()) {
                continue;
            }
            LocalDate dateEcheance = LocalDate.parse(dateStr);
            if (dateEcheance.isBefore(contrat.getDateDebut()) || dateEcheance.isAfter(contrat.getDateFin())) {
                return "La date d'une échéance (" + dateEcheance.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + ") est en dehors de la période du contrat.";
            }
            Echeance echeance = new Echeance();
            echeance.setContrat(contrat);
            echeance.setDateEcheance(dateEcheance);
            echeance.setMontantDu(new BigDecimal(montantStr.trim()));
            echeance.setStatut(StatutEcheance.EN_COURS);
            String typeStr = (types != null && i < types.size()) ? types.get(i) : null;
            echeance.setType((typeStr == null || typeStr.isBlank()) ? TypeEcheance.LOYER : TypeEcheance.valueOf(typeStr));
            out.add(echeance);
        }
        return null;
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
    public String renewContractPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Contrat ancien = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (!peutEtreRenouvele(ancien)) {
            redirectAttributes.addFlashAttribute("error",
                    "Le renouvellement n'est possible qu'à partir d'un contrat résilié ou expiré.");
            return "redirect:/admin/contracts/" + id;
        }

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
    public String clientsPage(@RequestParam(required = false) String search,
                               @RequestParam(required = false) String contratActif, Model model) {
        List<User> clients = (search == null || search.isBlank())
                ? userRepository.findByRole(Role.ROLE_LOCATAIRE)
                : userRepository.searchByRoleAndNomOrPrenomOrTelephone(Role.ROLE_LOCATAIRE, search.trim());

        if ("avec".equals(contratActif)) {
            clients = clients.stream().filter(this::aUnContratActif).toList();
        } else if ("sans".equals(contratActif)) {
            clients = clients.stream().filter(c -> !aUnContratActif(c)).toList();
        }

        model.addAttribute("clients", clients);
        model.addAttribute("search", search);
        model.addAttribute("contratActifFiltre", contratActif);
        model.addAttribute("totalClients", userRepository.findByRole(Role.ROLE_LOCATAIRE).size());
        return "admin-clients";
    }

    private boolean aUnContratActif(User client) {
        return contratRepository.findByLocataireId(client.getId()).stream()
                .anyMatch(c -> c.getStatut() == StatutContrat.VALIDER);
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
        String normalizedCNI = (numeroCNI == null || numeroCNI.isBlank()) ? null : numeroCNI.trim();

        if (normalizedEmail != null && userRepository.findByEmail(normalizedEmail).isPresent()) {
            return rejectClientForm(model, "Cet email est déjà utilisé par un autre client",
                    nom, prenom, trimmedTelephone, normalizedEmail, numeroCNI);
        }

        if (userRepository.findByTelephone(trimmedTelephone).isPresent()) {
            return rejectClientForm(model, "Ce numéro de téléphone est déjà utilisé par un autre client",
                    nom, prenom, trimmedTelephone, normalizedEmail, numeroCNI);
        }

        if (normalizedCNI != null && userRepository.findByNumeroCNI(normalizedCNI).isPresent()) {
            return rejectClientForm(model, "Ce numéro de CNI est déjà utilisé par un autre client",
                    nom, prenom, trimmedTelephone, normalizedEmail, numeroCNI);
        }

        User client = new User();
        client.setNom(nom);
        client.setPrenom(prenom);
        client.setTelephone(trimmedTelephone);
        client.setEmail(normalizedEmail);
        client.setNumeroCNI(normalizedCNI);
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
