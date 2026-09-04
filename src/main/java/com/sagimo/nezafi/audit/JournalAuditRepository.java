package com.sagimo.nezafi.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {
    List<JournalAudit> findAllByOrderByDateActionDesc();

    // Utilisé pour bloquer la suppression d'un compte du personnel qui a des entrées à son nom
    // dans le journal d'audit (cf. StaffController) : l'effacer casserait la référence
    // utilisateur_id NOT NULL, et de toute façon le journal d'audit ne doit jamais perdre de trace.
    boolean existsByUtilisateurId(Long utilisateurId);
}
