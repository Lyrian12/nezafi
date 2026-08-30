package com.sagimo.nezafi.storage;

/**
 * Distingue plusieurs catégories de documents pour une même entité — aujourd'hui utilisé
 * seulement par "Contrat" (facture de paiement + scan du contrat signé peuvent coexister),
 * {@code null} ailleurs (photos d'emplacement, reçu de paiement : un seul type possible par
 * entité, pas besoin de cette distinction). Pas une colonne d'entité JPA elle-même — stocké
 * en {@code String} sur {@link DocumentJoint#getTypeDocument()} pour rester aussi générique que
 * {@code nomEntite}, cet enum n'est qu'une liste de valeurs valides côté appelant.
 */
public enum TypeDocumentJoint {
    FACTURE,
    CONTRAT_SCANNE
}
