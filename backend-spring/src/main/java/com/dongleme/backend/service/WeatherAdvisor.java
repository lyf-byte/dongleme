package com.dongleme.backend.service;

import com.dongleme.backend.dto.WeatherView;

import java.util.List;

public final class WeatherAdvisor {

    private WeatherAdvisor() {
    }

    public static WeatherView.Advice advise(WeatherView.Current current, WeatherView.Air air) {
        String type = current.weatherType() == null ? "cloudy" : current.weatherType();
        int aqi = air == null || air.aqi() == null ? -1 : air.aqi();
        Double uv = current.uvIndex() != null
                ? current.uvIndex()
                : air == null ? null : air.uvIndex();
        int windLevel = windLevel(current.windPower(), current.windSpeed());
        double temperature = current.temperature();

        if ("rain".equals(type) || "snow".equals(type) || "thunder".equals(type)) {
            return new WeatherView.Advice(
                    "不建议户外",
                    "有降水或雷暴，建议改为室内运动，注意地面湿滑。",
                    List.of(
                            sport("力量训练", "室内进行，不受天气影响"),
                            sport("跳绳", "短时高效，注意室内空间和缓冲"),
                            sport("游泳", "室内游泳不受降雨影响")
                    ));
        }

        if (aqi > 150) {
            return new WeatherView.Advice(
                    "不建议户外",
                    "空气质量较差，建议室内运动，外出可佩戴口罩。",
                    List.of(
                            sport("力量训练", "室内进行，减少户外暴露"),
                            sport("跳绳", "室内短时高效训练"),
                            sport("游泳", "室内运动，注意通风")
                    ));
        }

        if (aqi > 100) {
            return new WeatherView.Advice(
                    "一般",
                    "空气质量轻度污染，敏感人群应减少户外运动时长。",
                    List.of(
                            sport("快走", "强度温和，缩短户外时间"),
                            sport("力量训练", "室内进行更稳妥")
                    ));
        }

        if (temperature >= 32) {
            return new WeatherView.Advice(
                    "一般",
                    "气温较高，建议避开正午，注意补水防暑。",
                    List.of(
                            sport("游泳", "水中运动更适合高温天气"),
                            sport("快走", "选择清晨或傍晚，降低强度"),
                            sport("力量训练", "室内训练，避免暴晒")
                    ));
        }

        if (temperature <= 5) {
            return new WeatherView.Advice(
                    "一般",
                    "气温较低，户外运动前请充分热身，注意保暖。",
                    List.of(
                            sport("跑步", "充分热身后进行，注意保暖"),
                            sport("跳绳", "可室内进行，快速升温"),
                            sport("力量训练", "室内训练更舒适")
                    ));
        }

        if ("fog".equals(type)) {
            return new WeatherView.Advice(
                    "一般",
                    "能见度较低，户外运动注意安全，建议降低强度。",
                    List.of(
                            sport("快走", "选择熟悉路线，注意车辆"),
                            sport("力量训练", "室内训练更安全")
                    ));
        }

        if ("haze".equals(type)) {
            return new WeatherView.Advice(
                    "一般",
                    "能见度或空气质量不佳，建议减少户外停留。",
                    List.of(
                            sport("力量训练", "室内进行，减少吸入颗粒物"),
                            sport("跳绳", "室内短时训练")
                    ));
        }

        if (uv != null && uv >= 8) {
            return new WeatherView.Advice(
                    "一般",
                    "紫外线很强，建议选择早晨或傍晚，并做好防晒。",
                    List.of(
                            sport("快走", "避开正午，注意防晒"),
                            sport("跑步", "选择清晨或傍晚进行"),
                            sport("骑行", "做好防晒，避开强光时段")
                    ));
        }

        if (windLevel >= 6) {
            return new WeatherView.Advice(
                    "一般",
                    "风力较大，不建议骑行，注意防风。",
                    List.of(
                            sport("跑步", "选择背风路线，注意保暖"),
                            sport("快走", "降低强度，注意防风"),
                            sport("力量训练", "室内训练不受风力影响")
                    ));
        }

        return new WeatherView.Advice(
                "适宜",
                "天气条件不错，适合户外运动，记得及时补水。",
                List.of(
                        sport("跑步", "温度适宜、天气稳定"),
                        sport("骑行", "风力较小，注意补水"),
                        sport("快走", "强度温和，适合当前天气")
                ));
    }

    private static WeatherView.SportAdvice sport(String type, String reason) {
        return new WeatherView.SportAdvice(type, reason);
    }

    private static int windLevel(String windPower, Double windSpeed) {
        int level = 0;
        if (windPower != null) {
            for (char character : windPower.toCharArray()) {
                if (Character.isDigit(character)) {
                    level = Math.max(level, character - '0');
                }
            }
        }
        if (windSpeed != null && windSpeed >= 39) {
            level = Math.max(level, 6);
        }
        return level;
    }
}
