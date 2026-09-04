package com.sagimo.nezafi.admin;

/** Une ligne d'échéance telle que saisie dans le formulaire contrat (admin-contract-form.html),
 *  avant toute validation/persistance — sert uniquement à réafficher ce que l'utilisateur avait
 *  déjà tapé si le formulaire est rejeté (montant invalide, etc.), plutôt qu'un formulaire vidé
 *  qui obligerait à ressaisir tout l'échéancier depuis le début. */
public record EcheanceSaisie(String date, String montant, String type) {
}
