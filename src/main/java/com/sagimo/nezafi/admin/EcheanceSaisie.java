package com.sagimo.nezafi.admin;

/** Une ligne d'échéance telle qu'affichée/soumise dans le formulaire contrat
 *  (admin-contract-form.html). Deux usages :
 *  <ul>
 *      <li>Réaffichage d'un formulaire rejeté (montant invalide...) — {@code id} porte alors
 *          l'id de l'échéance existante en cours d'édition, ou reste null pour une ligne
 *          nouvellement ajoutée par l'utilisateur avant le rejet.</li>
 *      <li>Pré-remplissage initial des échéances déjà rattachées au contrat en édition — sans
 *          {@code id}, une échéance ressaisie dans une ligne vide serait traitée comme une
 *          NOUVELLE échéance par AdminController.construireEcheances au lieu de mettre à jour
 *          l'existante : elle resterait en doublon, l'ancienne orpheline. {@code id} est donc
 *          ce qui permet au serveur de distinguer "mettre à jour cette ligne" de "en créer une
 *          nouvelle" — jamais un doublon silencieux.</li>
 *  </ul> */
public record EcheanceSaisie(Long id, String date, String montant, String type) {
}
