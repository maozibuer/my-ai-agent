package com.agent.tool.tool;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool for querying weather information for a specified city.
 * Returns mock weather data for common cities.
 */
@Slf4j
@Component
public class WeatherTool {

    /**
     * Queries weather information for the specified city.
     *
     * @param city the city name to query weather for
     * @return a mock weather description string
     */
    @Tool("Query weather information for the specified city")
    public String getWeather(@P("City name") String city) {
        log.info("Weather tool called for city: {}", city);

        if (city == null || city.isBlank()) {
            return "Please provide a city name.";
        }

        String normalizedCity = city.trim().toLowerCase();

        return switch (normalizedCity) {
            case "beijing", "北京" ->
                    "Beijing: Sunny, temperature 22°C, humidity 45%, wind NW 3级, " +
                    "air quality index 58 (Moderate).";
            case "shanghai", "上海" ->
                    "Shanghai: Cloudy, temperature 26°C, humidity 65%, wind SE 2-3级, " +
                    "air quality index 42 (Good).";
            case "guangzhou", "广州" ->
                    "Guangzhou: Light rain, temperature 28°C, humidity 80%, wind S 2级, " +
                    "air quality index 35 (Good).";
            case "shenzhen", "深圳" ->
                    "Shenzhen: Overcast, temperature 27°C, humidity 75%, wind S 3级, " +
                    "air quality index 38 (Good).";
            case "hangzhou", "杭州" ->
                    "Hangzhou: Partly cloudy, temperature 24°C, humidity 55%, wind NE 2级, " +
                    "air quality index 48 (Good).";
            case "chengdu", "成都" ->
                    "Chengdu: Foggy, temperature 20°C, humidity 70%, wind NW 1级, " +
                    "air quality index 65 (Moderate).";
            case "xian", "西安" ->
                    "Xi'an: Clear, temperature 18°C, humidity 40%, wind NE 3级, " +
                    "air quality index 72 (Moderate).";
            case "wuhan", "武汉" ->
                    "Wuhan: Showers, temperature 25°C, humidity 68%, wind SW 2级, " +
                    "air quality index 50 (Good).";
            default ->
                    city + ": Weather data not available for this city. " +
                    "Currently supported cities: Beijing, Shanghai, Guangzhou, Shenzhen, " +
                    "Hangzhou, Chengdu, Xi'an, Wuhan.";
        };
    }
}
