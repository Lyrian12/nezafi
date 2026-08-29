package com.sagimo.nezafi.user;

public enum Role {
    ROLE_LOCATAIRE,
    ROLE_ADMIN,
    // Opérationnel au quotidien : création/modification sur emplacements, clients, contrats,
    // échéances, paiements — pas d'accès au journal d'audit ni à la gestion des comptes.
    ROLE_SECRETARIAT,
    // Strictement financier, lecture seule : échéances/paiements, informations financières
    // d'un contrat, emplacements en consultation — aucun droit de modification nulle part.
    ROLE_COMPTABLE
}
