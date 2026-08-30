package com.sagimo.nezafi.contrat;

public enum StatutContrat {
    VALIDER,
    EN_ATTENTE,
    REJETER,
    RESILIER,
    // Jamais choisi manuellement (absent du <select> du formulaire d'édition, cf.
    // admin-contract-form.html) : appliqué automatiquement quand un contrat VALIDER dépasse sa
    // dateFin sans intervention (résiliation, renouvellement...) — cf. ContratStatusService.
    EXPIRE
}
