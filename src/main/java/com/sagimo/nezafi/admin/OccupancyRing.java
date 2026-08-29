package com.sagimo.nezafi.admin;

/**
 * Un anneau du diagramme "Gestion des emplacements" du tableau de bord : représente le
 * taux d'occupation propre d'un {@link com.sagimo.nezafi.emplacement.Palier} (pas sa part
 * dans le total occupé — contrairement à un donut classique, ces pourcentages sont
 * indépendants les uns des autres, donc chacun a son propre anneau plutôt que de partager
 * un seul cercle "parts d'un tout"). {@code dashArray} est déjà prêt à poser tel quel sur
 * l'attribut SVG {@code stroke-dasharray}.
 */
public record OccupancyRing(double radius, String dashArray, String color) {
}
