package com.dongleme.backend.dto;

import com.dongleme.backend.entity.CustomSport;

public record CustomSportView(Long id, String name, double met) {

    public static CustomSportView of(CustomSport sport) {
        return new CustomSportView(
                sport.getId(),
                sport.getName(),
                sport.getMet() == null ? 5.5 : sport.getMet().doubleValue()
        );
    }
}
