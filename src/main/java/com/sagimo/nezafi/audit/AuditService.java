package com.sagimo.nezafi.audit;

import com.sagimo.nezafi.user.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Point d'entrée unique pour journaliser une action dans le {@link JournalAudit}.
 * Volontairement générique : ce service ne connaît rien de Contrat/Paiement/
 * Emplacement — chaque appelant lui fournit un instantané (une {@code Map<String,
 * Object>}, dans l'ordre où les entrées doivent s'afficher) des seuls champs qu'il
 * veut tracer pour cette entité.
 *
 * Le rendu est un simple "clé: valeur" par ligne (pas de JSON) pour ne pas ajouter
 * de dépendance à un mapper JSON juste pour ce module — largement suffisant pour de
 * la lecture humaine dans la page d'audit.
 */
@Service
public class AuditService {

    private final JournalAuditRepository journalAuditRepository;

    public AuditService(JournalAuditRepository journalAuditRepository) {
        this.journalAuditRepository = journalAuditRepository;
    }

    public void enregistrer(User utilisateur, TypeActionAudit typeAction, String nomEntite, Long entiteId,
                             Map<String, Object> ancienneValeur, Map<String, Object> nouvelleValeur) {
        JournalAudit entree = new JournalAudit();
        entree.setUtilisateur(utilisateur);
        entree.setTypeAction(typeAction);
        entree.setNomEntite(nomEntite);
        entree.setEntiteId(entiteId);
        entree.setAncienneValeur(formatter(ancienneValeur));
        entree.setNouvelleValeur(formatter(nouvelleValeur));
        entree.setDateAction(LocalDateTime.now());
        journalAuditRepository.save(entree);
    }

    private String formatter(Map<String, Object> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : valeurs.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }
}
