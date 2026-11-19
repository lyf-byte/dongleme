package com.dongleme.backend.service;

import java.util.Map;

public final class Sports {

    public static final Map<String, Double> BUILTIN_MET = Map.of(
            "跑步", 8.0,
            "快走", 4.3,
            "骑行", 7.5,
            "跳绳", 12.3,
            "游泳", 6.0,
            "力量训练", 5.5,
            "羽毛球", 5.5
    );

    private Sports() {
    }

    public static double genderFactor(String gender) {
        return "female".equals(gender) ? 0.9 : 1.0;
    }
}
