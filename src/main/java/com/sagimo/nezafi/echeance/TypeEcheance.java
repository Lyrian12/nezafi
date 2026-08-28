package com.sagimo.nezafi.echeance;

/**
 * Ce qu'une échéance couvre : le loyer, ou la caution. Permet de mélanger, sur un
 * même contrat, un échéancier loyer et un échéancier caution indépendants (ex. loyer
 * payé en 3 fois, caution payée cash en une seule échéance).
 */
public enum TypeEcheance {
    LOYER,
    CAUTION
}
