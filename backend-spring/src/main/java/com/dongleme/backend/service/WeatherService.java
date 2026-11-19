package com.dongleme.backend.service;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.WeatherView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final long WEATHER_TTL_MS = Duration.ofMinutes(15).toMillis();
    private static final long REGEO_TTL_MS = Duration.ofHours(24).toMillis();

    private static final String AMAP_REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    private static final String OPEN_METEO_WEATHER_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String OPEN_METEO_AIR_URL = "https://air-quality-api.open-meteo.com/v1/air-quality";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String amapKey;

    private final Map<String, CacheEntry<WeatherView>> weatherCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Regeo>> regeoCache = new ConcurrentHashMap<>();

    public WeatherService(
            ObjectMapper objectMapper,
            @Value("${app.weather.amap-key:}") String amapKey) {
        this.objectMapper = objectMapper;
        this.amapKey = amapKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WeatherView getWeather(double lat, double lon) {
        String key = cacheKey(lat, lon);
        WeatherView cached = valid(weatherCache, key);
        if (cached != null) {
            return cached;
        }

        WeatherView.Air air = fetchAir(lat, lon);
        WeatherView view = null;

        if (amapKey != null && !amapKey.isBlank()) {
            try {
                view = fetchFromAmap(lat, lon, air);
            } catch (Exception exception) {
                log.warn("高德天气获取失败，回退 Open-Meteo: {}", exception.getMessage());
            }
        }

        if (view == null) {
            view = fetchFromOpenMeteo(lat, lon, air);
        }

        if (view == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "天气服务暂时不可用，请稍后再试");
        }

        weatherCache.put(key, new CacheEntry<>(view, System.currentTimeMillis() + WEATHER_TTL_MS));
        return view;
    }

    private WeatherView fetchFromAmap(double lat, double lon, WeatherView.Air air) throws Exception {
        Regeo regeo = regeo(lat, lon);

        JsonNode liveRoot = getJson(uri(AMAP_WEATHER_URL, Map.of(
                "key", amapKey,
                "city", regeo.adcode(),
                "extensions", "base"
        )));
        checkAmap(liveRoot);

        JsonNode live = liveRoot.path("lives").path(0);
        String condition = text(live, "weather");
        if (condition == null) {
            throw new IllegalStateException("高德实况为空");
        }

        double temperature = parseDouble(text(live, "temperature"), 0);
        Integer humidity = parseInt(text(live, "humidity"), null);
        String windPower = text(live, "windpower");
        String type = weatherType(condition);
        boolean isDay = isDayNow();
        Double uvIndex = air == null ? null : air.uvIndex();

        WeatherView.Current current = new WeatherView.Current(
                temperature,
                null,
                humidity,
                condition,
                type,
                isDay,
                null,
                windPower,
                null,
                uvIndex
        );

        JsonNode forecastRoot = getJson(uri(AMAP_WEATHER_URL, Map.of(
                "key", amapKey,
                "city", regeo.adcode(),
                "extensions", "all"
        )));
        checkAmap(forecastRoot);

        List<WeatherView.Daily> daily = new ArrayList<>();
        JsonNode casts = forecastRoot.path("forecasts").path(0).path("casts");
        for (int index = 0; index < casts.size() && daily.size() < 3; index++) {
            JsonNode cast = casts.get(index);
            String dateText = text(cast, "date");
            String dayWeather = text(cast, "dayweather");
            if (dateText == null || dayWeather == null) {
                continue;
            }
            int dayTemp = parseInt(text(cast, "daytemp"), 0);
            int nightTemp = parseInt(text(cast, "nighttemp"), 0);
            daily.add(new WeatherView.Daily(
                    LocalDate.parse(dateText),
                    dayWeather,
                    weatherType(dayWeather),
                    Math.max(dayTemp, nightTemp),
                    Math.min(dayTemp, nightTemp),
                    null,
                    null
            ));
        }

        WeatherView.Advice advice = WeatherAdvisor.advise(current, air);
        return new WeatherView(
                new WeatherView.Location(regeo.name(), lat, lon),
                current,
                air,
                daily,
                advice
        );
    }

    private WeatherView fetchFromOpenMeteo(double lat, double lon, WeatherView.Air air) {
        try {
            JsonNode root = getJson(uri(OPEN_METEO_WEATHER_URL, Map.of(
                    "latitude", formatCoord(lat),
                    "longitude", formatCoord(lon),
                    "current", "temperature_2m,apparent_temperature,relative_humidity_2m,"
                            + "precipitation,weather_code,wind_speed_10m,is_day",
                    "daily", "weather_code,temperature_2m_max,temperature_2m_min,"
                            + "uv_index_max,precipitation_probability_max",
                    "timezone", "Asia/Shanghai",
                    "forecast_days", "3"
            )));

            JsonNode current = root.path("current");
            int code = current.path("weather_code").asInt(2);
            boolean isDay = current.path("is_day").asInt(1) == 1;
            Double uvIndex = air == null ? null : air.uvIndex();

            WeatherView.Current currentView = new WeatherView.Current(
                    current.path("temperature_2m").asDouble(),
                    optDouble(current, "apparent_temperature"),
                    optInt(current, "relative_humidity_2m"),
                    wmoCondition(code),
                    wmoType(code),
                    isDay,
                    optDouble(current, "wind_speed_10m"),
                    null,
                    optDouble(current, "precipitation"),
                    uvIndex
            );

            List<WeatherView.Daily> daily = new ArrayList<>();
            JsonNode dailyNode = root.path("daily");
            JsonNode dates = dailyNode.path("time");
            JsonNode codes = dailyNode.path("weather_code");
            JsonNode maxTemps = dailyNode.path("temperature_2m_max");
            JsonNode minTemps = dailyNode.path("temperature_2m_min");
            JsonNode uvMaxes = dailyNode.path("uv_index_max");
            JsonNode precipProbabilities = dailyNode.path("precipitation_probability_max");

            for (int index = 0; index < dates.size() && daily.size() < 3; index++) {
                int dailyCode = codes.path(index).asInt(2);
                daily.add(new WeatherView.Daily(
                        LocalDate.parse(dates.path(index).asText()),
                        wmoCondition(dailyCode),
                        wmoType(dailyCode),
                        (int) Math.round(maxTemps.path(index).asDouble()),
                        (int) Math.round(minTemps.path(index).asDouble()),
                        arrayDouble(uvMaxes, index),
                        arrayInt(precipProbabilities, index)
                ));
            }

            WeatherView.Advice advice = WeatherAdvisor.advise(currentView, air);
            return new WeatherView(
                    new WeatherView.Location("当前位置", lat, lon),
                    currentView,
                    air,
                    daily,
                    advice
            );
        } catch (Exception exception) {
            log.error("Open-Meteo 天气获取失败", exception);
            return null;
        }
    }

    private WeatherView.Air fetchAir(double lat, double lon) {
        try {
            JsonNode current = getJson(uri(OPEN_METEO_AIR_URL, Map.of(
                    "latitude", formatCoord(lat),
                    "longitude", formatCoord(lon),
                    "current", "us_aqi,pm2_5,pm10,uv_index",
                    "timezone", "Asia/Shanghai"
            ))).path("current");

            Integer aqi = optInt(current, "us_aqi");
            if (aqi == null) {
                return null;
            }
            return new WeatherView.Air(
                    aqi,
                    aqiCategory(aqi),
                    optDouble(current, "pm2_5"),
                    optDouble(current, "pm10"),
                    optDouble(current, "uv_index")
            );
        } catch (Exception exception) {
            log.warn("空气质量获取失败: {}", exception.getMessage());
            return null;
        }
    }

    private Regeo regeo(double lat, double lon) throws Exception {
        String key = cacheKey(lat, lon);
        Regeo cached = valid(regeoCache, key);
        if (cached != null) {
            return cached;
        }

        JsonNode root = getJson(uri(AMAP_REGEO_URL, Map.of(
                "key", amapKey,
                "location", String.format(Locale.ROOT, "%.6f,%.6f", lon, lat)
        )));
        checkAmap(root);

        JsonNode component = root.path("regeocode").path("addressComponent");
        String adcode = text(component, "adcode");
        if (adcode == null) {
            throw new IllegalStateException("高德逆地理编码缺少 adcode");
        }

        Regeo result = new Regeo(adcode, joinName(
                text(component, "city"),
                text(component, "province"),
                text(component, "district")
        ));
        regeoCache.put(key, new CacheEntry<>(result, System.currentTimeMillis() + REGEO_TTL_MS));
        return result;
    }

    private JsonNode getJson(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private static URI uri(String base, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base);
        params.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private static void checkAmap(JsonNode root) {
        if (!"1".equals(root.path("status").asText())) {
            throw new IllegalStateException("高德接口错误: " + root.path("info").asText());
        }
    }

    private static String joinName(String city, String province, String district) {
        String base = city != null ? city : province;
        if (base == null) {
            base = "当前位置";
        }
        if (district != null && !district.equals(base)) {
            return base + district;
        }
        return base;
    }

    private static boolean isDayNow() {
        int hour = LocalTime.now(ShanghaiTime.ZONE).getHour();
        return hour >= 6 && hour < 18;
    }

    private static String weatherType(String condition) {
        if (condition == null) {
            return "cloudy";
        }
        if (condition.contains("雷")) {
            return "thunder";
        }
        if (condition.contains("雪") || condition.contains("冰雹")) {
            return "snow";
        }
        if (condition.contains("雨")) {
            return "rain";
        }
        if (condition.contains("雾")) {
            return "fog";
        }
        if (condition.contains("霾") || condition.contains("沙") || condition.contains("尘")) {
            return "haze";
        }
        if (condition.contains("阴")) {
            return "overcast";
        }
        if (condition.contains("云")) {
            return "cloudy";
        }
        if (condition.contains("晴")) {
            return "sunny";
        }
        return "cloudy";
    }

    private static String wmoCondition(int code) {
        if (code == 0) {
            return "晴";
        }
        if (code == 1) {
            return "晴间多云";
        }
        if (code == 2) {
            return "多云";
        }
        if (code == 3) {
            return "阴";
        }
        if (code == 45 || code == 48) {
            return "雾";
        }
        if (code >= 51 && code <= 57) {
            return "毛毛雨";
        }
        if (code == 61) {
            return "小雨";
        }
        if (code == 63) {
            return "中雨";
        }
        if (code == 65) {
            return "大雨";
        }
        if (code == 66 || code == 67) {
            return "冻雨";
        }
        if (code == 71) {
            return "小雪";
        }
        if (code == 73) {
            return "中雪";
        }
        if (code == 75) {
            return "大雪";
        }
        if (code == 77) {
            return "雪粒";
        }
        if (code == 80) {
            return "阵雨";
        }
        if (code >= 81 && code <= 82) {
            return "强阵雨";
        }
        if (code == 85 || code == 86) {
            return "阵雪";
        }
        if (code >= 95) {
            return "雷阵雨";
        }
        return "多云";
    }

    private static String wmoType(int code) {
        if (code == 0 || code == 1) {
            return "sunny";
        }
        if (code == 2) {
            return "cloudy";
        }
        if (code == 3) {
            return "overcast";
        }
        if (code == 45 || code == 48) {
            return "fog";
        }
        if (code >= 51 && code <= 67) {
            return "rain";
        }
        if (code >= 71 && code <= 77) {
            return "snow";
        }
        if (code >= 80 && code <= 82) {
            return "rain";
        }
        if (code == 85 || code == 86) {
            return "snow";
        }
        if (code >= 95) {
            return "thunder";
        }
        return "cloudy";
    }

    private static String aqiCategory(int aqi) {
        if (aqi <= 50) {
            return "优";
        }
        if (aqi <= 100) {
            return "良";
        }
        if (aqi <= 150) {
            return "轻度污染";
        }
        if (aqi <= 200) {
            return "中度污染";
        }
        if (aqi <= 300) {
            return "重度污染";
        }
        return "严重污染";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.isArray()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Integer parseInt(String value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Integer optInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.asInt() : parseInt(value.asText(), null);
    }

    private static Double optDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.asDouble() : parseDouble(value.asText(), 0);
    }

    private static Double arrayDouble(JsonNode array, int index) {
        JsonNode value = array.path(index);
        return value.isMissingNode() || value.isNull() ? null : value.asDouble();
    }

    private static Integer arrayInt(JsonNode array, int index) {
        JsonNode value = array.path(index);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static String formatCoord(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String cacheKey(double lat, double lon) {
        return String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
    }

    private static <T> T valid(Map<String, CacheEntry<T>> cache, String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiresAt()) {
            cache.remove(key);
            return null;
        }
        return entry.value();
    }

    private record CacheEntry<T>(T value, long expiresAt) {
    }

    private record Regeo(String adcode, String name) {
    }
}
