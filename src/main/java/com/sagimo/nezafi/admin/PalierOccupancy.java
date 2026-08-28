package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.emplacement.Palier;

public record PalierOccupancy(Palier palier, long occupiedCount, long totalCount, double percentage) {
}
