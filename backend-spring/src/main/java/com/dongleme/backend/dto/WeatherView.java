package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record WeatherView(
        Location location,
        Current current,
        Air air,
        List<Daily> daily,
        Advice advice
) {

    public record Location(String name, double lat, double lon) {
    }

    public record Current(
            double temperature,
            Double apparentTemperature,
            Integer humidity,
            String condition,
            String weatherType,
            boolean isDay,
            Double windSpeed,
            String windPower,
            Double precipitation,
            Double uvIndex
    ) {
    }

    public record Air(
            Integer aqi,
            String category,
            Double pm25,
            Double pm10,
            Double uvIndex
    ) {
    }

    public record Daily(
            LocalDate date,
            String condition,
            String weatherType,
            int max,
            int min,
            Double uvMax,
            Integer precipitationProbability
    ) {
    }

    public record Advice(String level, String summary, List<SportAdvice> sports) {
    }

    public record SportAdvice(String type, String reason) {
    }
}
