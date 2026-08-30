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
import com.sagimo.nezafi.storage.DocumentJointService;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    // Cette classe mélange des routes aux droits différents (lecture vs écriture, ADMIN seul
    // pour les suppressions) : plus de @PreAuthorize de classe unique, chaque méthode porte le
    // sien. Constantes pour éviter de répéter les mêmes expressions ~20 fois.
    //
    // COMPTABLE est strictement lecture seule, sans aucune exception : aucune création ni
    // modification nulle part, y compris sur emplacements et clients (un temps ouverts en
    // écriture pour lui, retiré sur demande explicite — LECTURE_STAFF reste son seul niveau
    // d'accès dans toute cette classe, jamais EDITION_STAFF).
    private static final String LECTURE_STAFF = "hasAnyRole('ADMIN','SECRETARIAT','COMPTABLE')";
    private static final String EDITION_STAFF = "hasAnyRole('ADMIN','SECRETARIAT')";
    private static final String ADMIN_SEUL = "hasRole('ADMIN')";

    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContratStatusService contratStatusService;
    private final EcheanceRepository echeanceRepository;
    private final EcheanceStatusService echeanceStatusService;
    private final AuditService auditService;
    private final AdminAlertService adminAlertService;
    private final DocumentJointService documentJointService;

    public AdminController(EmplacementRepository emplacementRepository, ContratRepository contratRepository,
                            UserRepository userRepository, PasswordEncoder passwordEncoder,
                            ContratStatusService contratStatusService, EcheanceRepository echeanceRepository,
                            EcheanceStatusService echeanceStatusService, AuditService auditService,
                            AdminAlertService adminAlertService, DocumentJointService documentJointService) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contratStatusService = contratStatusService;
        this.echeanceRepository = echeanceRepository;
        this.echeanceStatusService = echeanceStatusService;
        this.auditService = auditService;
        this.adminAlertService = adminAlertService;
        this.documentJointService = documentJointService;
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

    /**
     * Emplacements proposés dans le menu déroulant de création d'un contrat : uniquement
     * DISPONIBLE (jamais ceux déjà occupés par un contrat actif) — après avoir rattrapé au
     * passage les statuts éventuellement périmés (cf. ContratStatusService.rafraichirStatuts).
     * Ne concerne QUE la création : le renouvellement (GET /admin/contracts/{id}/renew)
     * pré-remplit déjà l'emplacement depuis l'ancien contrat et ne passe pas par ce menu ;
     * l'édition doit elle continuer à montrer l'emplacement déjà assigné même s'il n'est plus
     * DISPONIBLE (cf. rejectContractForm, appelé aussi bien depuis l'ajout que la modification).
     */
    private List<Emplacement> emplacementsDisponiblesPourCreation() {
        List<Emplacement> tous = emplacementRepository.findAll();
        contratStatusService.rafraichirStatuts(tous);
        return tous.stream().filter(e -> e.getStatut() == StatutEmplacement.DISPONIBLE).toList();
    }

    private String rejectContractForm(Model model, String error, Contrat contrat) {
        return rejectContractForm(model, error, contrat, true);
    }

    private String rejectContractForm(Model model, String error, Contrat contrat, boolean disponiblesUniquement) {
        model.addAttribute("error", error);
        model.addAttribute("contrat", contrat);
        model.addAttribute("emplacements", disponiblesUniquement
                ? emplacementsDisponiblesPourCreation()
                : emplacementRepository.findAll());
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
    }

    // Store Management
    @GetMapping("/stores")
    @PreAuthorize(LECTURE_STAFF)
    public String storesPage(
            @RequestParam(required = false) Palier palier,
            @RequestParam(required = false) StatutEmplacement statut,
            @RequestParam(required = false) CategorieEmplacement categorie,
            @RequestParam(required = false) BigDecimal prixMin,
            @RequestParam(required = false) BigDecimal prixMax,
            @RequestParam(required = false) BigDecimal superficieMin,
            @RequestParam(required = false) BigDecimal superficieMax,
            Model model) {
        List<Emplacement> allStores = emplacementRepository.findAll();
        // Rattrape les emplacements dont le contrat a simplement expiré sans qu'on y touche
        // (cf. ContratStatusService.rafraichirStatut) avant de filtrer/compter par statut.
        contratStatusService.rafraichirStatuts(allStores);

        // Tri par défaut par palier (Palier 1, puis 2, puis 3), sans qu'aucun filtre ne soit
        // nécessaire pour l'obtenir — même ordre que l'export CSV/PDF (EmplacementExportService).
        List<Emplacement> filteredStores = allStores.stream()
                .filter(e -> palier == null || e.getPalier() == palier)
                .filter(e -> statut == null || e.getStatut() == statut)
                .filter(e -> categorie == null || e.getCategorie() == categorie)
                .filter(e -> prixMin == null || (e.getPrix() != null && e.getPrix().compareTo(prixMin) >= 0))
                .filter(e -> prixMax == null || (e.getPrix() != null && e.getPrix().compareTo(prixMax) <= 0))
                .filter(e -> superficieMin == null || (e.getSuperficie() != null && e.getSuperficie().compareTo(superficieMin) >= 0))
                .filter(e -> superficieMax == null || (e.getSuperficie() != null && e.getSuperficie().compareTo(superficieMax) <= 0))
                .sorted(Comparator.comparingInt((Emplacement e) -> e.getPalier().ordinal())
                        .thenComparing(Emplacement::getName, String.CASE_INSENSITIVE_ORDER))
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

        model.addAttribute("totalStores", allStores.size());
        model.addAttribute("activeStoresCount", allStores.stream()
                .filter(b -> b.getStatut() == StatutEmplacement.DISPONIBLE).count());

        List<Emplacement> occupiedStores = allStores.stream()
                .filter(b -> b.getStatut() == StatutEmplacement.NON_DISPONIBLE)
                .toList();
        model.addAttribute("pendingStoresCount", occupiedStores.size());

        // Un emplacement occupé par un contrat actif est rattaché au client de ce contrat ;
        // s'il y a plusieurs contrats VALIDER pour le même emplacement (cas normalement
        // impossible en pratique), on garde le premier trouvé. Vérifie aussi la dateFin (pas
        // seulement statut==VALIDER) : sans ça, un contrat VALIDER simplement expiré resterait
        // affiché comme occupant l'emplacement même après que celui-ci soit repassé DISPONIBLE
        // (cf. ContratStatusService.rafraichirStatut, qui lui vérifie déjà la dateFin).
        LocalDate aujourdHui = LocalDate.now();
        Map<Long, User> clientByEmplacementId = new HashMap<>();
        for (Contrat contrat : contratRepository.findByStatut(StatutContrat.VALIDER)) {
            if (contrat.getDateFin() == null || !contrat.getDateFin().isBefore(aujourdHui)) {
                clientByEmplacementId.putIfAbsent(contrat.getEmplacement().getId(), contrat.getLocataire());
            }
        }
        model.addAttribute("clientByEmplacementId", clientByEmplacementId);

        return "admin-stores";
    }

    @GetMapping("/stores/{id}")
    @PreAuthorize(LECTURE_STAFF)
    public String storeDetail(@PathVariable Long id, Model model) {
        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));
        contratStatusService.rafraichirStatut(emplacement);

        List<Contrat> contrats = contratRepository.findByEmplacementId(id);
        contrats.sort(Comparator.comparing(Contrat::getDateDebut).reversed());

        model.addAttribute("emplacement", emplacement);
        model.addAttribute("contrats", contrats);
        model.addAttribute("estOrpheline", emplacement.getStatut() == StatutEmplacement.NON_DISPONIBLE
                && adminAlertService.sansContratActif(emplacement));
        model.addAttribute("photos", documentJointService.lister("Emplacement", id));
        return "admin-store-detail";
    }

    // Plusieurs fichiers en un seul envoi (attribut "multiple" côté formulaire) : chaque appel
    // accumule quand même sur les photos déjà attachées (cf. DocumentJointService.attacher),
    // rien n'est remplacé.
    @PostMapping("/stores/{id}/photos")
    @PreAuthorize(EDITION_STAFF)
    public String ajouterPhoto(@PathVariable Long id, @RequestParam("photos") List<MultipartFile> photos,
                                RedirectAttributes redirectAttributes) throws IOException {
        emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        List<MultipartFile> fichiers = photos.stream().filter(p -> !p.isEmpty()).toList();
        if (fichiers.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Aucune photo sélectionnée.");
            return "redirect:/admin/stores/" + id;
        }
        for (MultipartFile photo : fichiers) {
            String type = photo.getContentType();
            if (type == null || !type.startsWith("image/")) {
                redirectAttributes.addFlashAttribute("error", "Chaque fichier doit être une image.");
                return "redirect:/admin/stores/" + id;
            }
        }

        for (MultipartFile photo : fichiers) {
            documentJointService.attacher("Emplacement", id, photo, "emplacements");
        }
        return "redirect:/admin/stores/" + id;
    }

    @GetMapping("/stores/add")
    @PreAuthorize(EDITION_STAFF)
    public String addStorePage(Model model) {
        model.addAttribute("emplacement", new Emplacement());
        return "admin-store-form";
    }

    @PostMapping("/stores/add")
    @PreAuthorize(EDITION_STAFF)
    public String addStore(
            @RequestParam String name,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(defaultValue = "DISPONIBLE") String statut,
            @RequestParam String palier,
            @RequestParam BigDecimal superficie,
            @RequestParam BigDecimal prix,
            @RequestParam String categorie,
            RedirectAttributes redirectAttributes) throws IOException {

        if (superficie.signum() <= 0 || prix.signum() <= 0) {
            redirectAttributes.addFlashAttribute("error", "La superficie et le prix doivent être strictement positifs.");
            return "redirect:/admin/stores/add";
        }

        Emplacement emplacement = new Emplacement();
        emplacement.setName(name);
        emplacement.setStatut(StatutEmplacement.valueOf(statut));
        emplacement.setPalier(Palier.valueOf(palier));
        emplacement.setSuperficie(superficie);
        emplacement.setPrix(prix);
        emplacement.setCategorie(CategorieEmplacement.valueOf(categorie));
        emplacement.setAddedAt(LocalDateTime.now());

        emplacementRepository.save(emplacement);

        // Photo facultative à la création : l'admin peut aussi n'en ajouter aucune ici et
        // compléter la galerie plus tard depuis la fiche détail (cf. /stores/{id}/photos).
        if (image != null && !image.isEmpty()) {
            documentJointService.attacher("Emplacement", emplacement.getId(), image, "emplacements");
        }

        return "redirect:/admin/stores";
    }

    @GetMapping("/stores/edit/{id}")
    @PreAuthorize(EDITION_STAFF)
    public String editStorePage(@PathVariable Long id, Model model) {
        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));
        model.addAttribute("emplacement", emplacement);
        return "admin-store-form";
    }

    @PostMapping("/stores/edit/{id}")
    @PreAuthorize(EDITION_STAFF)
    public String editStore(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String statut,
            @RequestParam String palier,
            @RequestParam BigDecimal superficie,
            @RequestParam BigDecimal prix,
            @RequestParam String categorie,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (superficie.signum() <= 0 || prix.signum() <= 0) {
            redirectAttributes.addFlashAttribute("error", "La superficie et le prix doivent être strictement positifs.");
            return "redirect:/admin/stores/edit/" + id;
        }

        Emplacement emplacement = emplacementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        BigDecimal ancienPrix = emplacement.getPrix();

        emplacement.setName(name);
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

    // POST (pas GET) : une suppression est une action destructrice, elle doit passer par le
    // token CSRF déjà en place sur les formulaires — un lien/image GET en contournerait la
    // protection CSRF (Spring Security ne protège par défaut que POST/PUT/DELETE/PATCH).
    //
    // Un emplacement supprimé entraîne en cascade (JPA cascade=ALL) la suppression de tous ses
    // contrats, et transitivement de leurs échéances et paiements — potentiellement un
    // historique financier réel (paiements déjà encaissés). Sans confirmation=true, ce premier
    // POST ne supprime rien s'il existe un tel historique : il redirige vers la fiche avec un
    // avertissement explicite, qui propose un second bouton "confirmer=true" pour aller au bout.
    @PostMapping("/stores/delete/{id}")
    @PreAuthorize(ADMIN_SEUL)
    public String deleteStore(@PathVariable Long id, @RequestParam(required = false, defaultValue = "false") boolean confirmer,
                               RedirectAttributes redirectAttributes) {
        List<Contrat> contratsLies = contratRepository.findByEmplacementId(id);
        if (!contratsLies.isEmpty() && !confirmer) {
            redirectAttributes.addFlashAttribute("warning",
                    "Cet emplacement a " + contratsLies.size() + " contrat(s) rattaché(s), avec leurs échéances et paiements : "
                            + "supprimer l'emplacement effacera aussi tout cet historique financier. Confirmer la suppression ?");
            redirectAttributes.addFlashAttribute("demanderConfirmationSuppression", true);
            return "redirect:/admin/stores/" + id;
        }
        emplacementRepository.deleteById(id);
        return "redirect:/admin/stores";
    }

    // Contract Management
    @GetMapping("/contracts")
    @PreAuthorize(LECTURE_STAFF)
    public String contractsPage(
            @RequestParam(required = false) StatutContrat statut,
            @RequestParam(required = false) String search,
            Model model) {
        List<Contrat> allContracts = contratRepository.findAll();
        // Rattrape les contrats VALIDER dont la dateFin est simplement dépassée, sans qu'on y
        // touche (cf. ContratStatusService.verifierExpiration) avant de filtrer/compter par statut.
        contratStatusService.rafraichirStatutsExpiration(allContracts);

        String terme = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        List<Contrat> filteredContracts = allContracts.stream()
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> terme == null
                        || c.getEmplacement().getName().toLowerCase().contains(terme)
                        || c.getLocataire().getNom().toLowerCase().contains(terme)
                        || c.getLocataire().getPrenom().toLowerCase().contains(terme)
                        || (c.getNomEnseigne() != null && c.getNomEnseigne().toLowerCase().contains(terme))
                        || (c.getActivite() != null && c.getActivite().toLowerCase().contains(terme)))
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
    @PreAuthorize(EDITION_STAFF)
    public String addContractPage(Model model) {
        model.addAttribute("contrat", new Contrat());
        model.addAttribute("emplacements", emplacementsDisponiblesPourCreation());
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
    }

    @PostMapping("/contracts/add")
    @PreAuthorize(EDITION_STAFF)
    public String addContract(
            @RequestParam Long emplacementId,
            @RequestParam Long locataireId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String termes,
            @RequestParam(required = false) String activite,
            @RequestParam(required = false) String nomEnseigne,
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
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, activite, nomEnseigne,
                            statut, montantLoyer, dureeLoyerMois, montantCaution, dureeCautionMois));
        }
        if (montantLoyer.signum() <= 0 || montantCaution.signum() <= 0) {
            return rejectContractForm(model, "Le loyer et la caution doivent être des montants strictement positifs.",
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, activite, nomEnseigne,
                            statut, montantLoyer, dureeLoyerMois, montantCaution, dureeCautionMois));
        }

        StatutContrat statutEnum = StatutContrat.valueOf(statut);
        if (statutEnum == StatutContrat.VALIDER && aUnAutreContratValide(emplacement.getId(), null)) {
            return rejectContractForm(model,
                    "Cet emplacement a déjà un contrat validé en cours : résiliez-le avant d'en valider un nouveau.",
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, activite, nomEnseigne,
                            statut, montantLoyer, dureeLoyerMois, montantCaution, dureeCautionMois));
        }

        Contrat contratPrecedent = null;
        if (contratPrecedentId != null) {
            contratPrecedent = contratRepository.findById(contratPrecedentId).orElse(null);
            if (contratPrecedent != null && !peutEtreRenouvele(contratPrecedent)) {
                return rejectContractForm(model,
                        "Le renouvellement n'est possible qu'à partir d'un contrat résilié ou expiré.",
                        contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, activite, nomEnseigne,
                                statut, montantLoyer, dureeLoyerMois, montantCaution, dureeCautionMois));
            }
        }

        Contrat contrat = new Contrat();
        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(dateDebutParsed);
        contrat.setDateFin(dateFinParsed);
        contrat.setTermes(termes);
        contrat.setActivite((activite == null || activite.isBlank()) ? null : activite.trim());
        contrat.setNomEnseigne((nomEnseigne == null || nomEnseigne.isBlank()) ? null : nomEnseigne.trim());
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
                    contratPourRejet(emplacement, locataire, dateDebut, dateFin, termes, activite, nomEnseigne,
                            statut, montantLoyer, dureeLoyerMois, montantCaution, dureeCautionMois));
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
                                      String termes, String activite, String nomEnseigne, String statut,
                                      BigDecimal montantLoyer, Integer dureeLoyerMois,
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
        rejected.setActivite(activite);
        rejected.setNomEnseigne(nomEnseigne);
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
     * Vérifie aussi la dateFin (pas seulement statut==VALIDER) : un contrat VALIDER dont la
     * date de fin est dépassée n'a pas forcément encore été rebasculé à EXPIRE (recalcul
     * paresseux, cf. ContratStatusService.verifierExpiration) — sans cette vérification directe,
     * il bloquerait à tort la validation d'un nouveau contrat sur un emplacement en réalité libre.
     */
    private boolean aUnAutreContratValide(Long emplacementId, Long excludeContratId) {
        LocalDate aujourdHui = LocalDate.now();
        return contratRepository.findByEmplacementId(emplacementId).stream()
                .filter(c -> excludeContratId == null || !c.getId().equals(excludeContratId))
                .anyMatch(c -> c.getStatut() == StatutContrat.VALIDER
                        && (c.getDateFin() == null || !c.getDateFin().isBefore(aujourdHui)));
    }

    /** Un contrat ne peut être renouvelé que s'il est résilié, expiré, ou si sa date de fin est
     *  dépassée (ce dernier cas couvre aussi un contrat encore VALIDER en base dont le passage à
     *  EXPIRE n'a pas encore été recalculé — cf. ContratStatusService.verifierExpiration). */
    private boolean peutEtreRenouvele(Contrat contrat) {
        return contrat.getStatut() == StatutContrat.RESILIER
                || contrat.getStatut() == StatutContrat.EXPIRE
                || (contrat.getDateFin() != null && contrat.getDateFin().isBefore(LocalDate.now()));
    }

    @GetMapping("/contracts/edit/{id}")
    @PreAuthorize(EDITION_STAFF)
    public String editContractPage(@PathVariable Long id, Model model) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        model.addAttribute("contrat", contrat);
        model.addAttribute("emplacements", emplacementRepository.findAll());
        model.addAttribute("locataires", userRepository.findByRole(Role.ROLE_LOCATAIRE));
        return "admin-contract-form";
    }

    @PostMapping("/contracts/edit/{id}")
    @PreAuthorize(EDITION_STAFF)
    public String editContract(
            @PathVariable Long id,
            @RequestParam Long emplacementId,
            @RequestParam Long locataireId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String termes,
            @RequestParam(required = false) String activite,
            @RequestParam(required = false) String nomEnseigne,
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
            return rejectContractForm(model, "La date de début ne peut pas être postérieure à la date de fin.", contrat, false);
        }
        if (montantLoyer.signum() <= 0 || montantCaution.signum() <= 0) {
            return rejectContractForm(model, "Le loyer et la caution doivent être des montants strictement positifs.", contrat, false);
        }

        StatutContrat statutEnum = StatutContrat.valueOf(statut);
        if (statutEnum == StatutContrat.VALIDER && aUnAutreContratValide(emplacementId, contrat.getId())) {
            return rejectContractForm(model,
                    "Cet emplacement a déjà un contrat validé en cours : résiliez-le avant d'en valider un nouveau.", contrat, false);
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
            return rejectContractForm(model, erreurEcheances, contrat, false);
        }

        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(dateDebutParsed);
        contrat.setDateFin(dateFinParsed);
        contrat.setTermes(termes);
        contrat.setActivite((activite == null || activite.isBlank()) ? null : activite.trim());
        contrat.setNomEnseigne((nomEnseigne == null || nomEnseigne.isBlank()) ? null : nomEnseigne.trim());
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
            BigDecimal montantEcheance = new BigDecimal(montantStr.trim());
            if (montantEcheance.signum() <= 0) {
                return "Le montant d'une échéance (" + dateEcheance.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + ") doit être strictement positif.";
            }
            Echeance echeance = new Echeance();
            echeance.setContrat(contrat);
            echeance.setDateEcheance(dateEcheance);
            echeance.setMontantDu(montantEcheance);
            echeance.setStatut(StatutEcheance.EN_COURS);
            String typeStr = (types != null && i < types.size()) ? types.get(i) : null;
            echeance.setType((typeStr == null || typeStr.isBlank()) ? TypeEcheance.LOYER : TypeEcheance.valueOf(typeStr));
            out.add(echeance);
        }
        return null;
    }

    // POST (pas GET) : voir le commentaire sur deleteStore ci-dessus, même raison.
    @PostMapping("/contracts/delete/{id}")
    @PreAuthorize(ADMIN_SEUL)
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
    @PreAuthorize(EDITION_STAFF)
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
    @PreAuthorize(EDITION_STAFF)
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

    // L'export CSV/PDF des contrats vit désormais dans com.sagimo.nezafi.export.ExportController
    // (même route /admin/contracts/export) : regroupé avec le nouvel export PDF pour éviter de
    // dupliquer la logique de génération entre deux contrôleurs.

    // Client Management
    @GetMapping("/clients")
    @PreAuthorize(LECTURE_STAFF)
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

    // Vérifie aussi la dateFin (pas seulement statut==VALIDER), même raison que
    // aUnAutreContratValide ci-dessus.
    private boolean aUnContratActif(User client) {
        LocalDate aujourdHui = LocalDate.now();
        return contratRepository.findByLocataireId(client.getId()).stream()
                .anyMatch(c -> c.getStatut() == StatutContrat.VALIDER
                        && (c.getDateFin() == null || !c.getDateFin().isBefore(aujourdHui)));
    }

    @GetMapping("/clients/add")
    @PreAuthorize(EDITION_STAFF)
    public String addClientPage(Model model) {
        model.addAttribute("client", new User());
        return "admin-client-form";
    }

    @PostMapping("/clients/add")
    @PreAuthorize(EDITION_STAFF)
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
    @PreAuthorize(LECTURE_STAFF)
    public String clientDetail(@PathVariable Long id, Model model) {
        User client = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.ROLE_LOCATAIRE)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        model.addAttribute("client", client);
        model.addAttribute("contrats", contratRepository.findByLocataireId(id));
        return "admin-client-detail";
    }
}
