package com.agent.tool.tool;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool for querying business data statistics.
 * Returns mock statistical data for various dimensions.
 */
@Slf4j
@Component
public class DataStatisticsTool {

    /**
     * Queries business statistics for the specified dimension.
     *
     * @param dimension the statistics dimension (e.g., "users", "orders", "revenue")
     * @return a mock statistics report string
     */
    @Tool("Query business data statistics for the specified dimension")
    public String getStatistics(@P("Statistics dimension: users, orders, revenue, or products") String dimension) {
        log.info("Data statistics tool called for dimension: {}", dimension);

        if (dimension == null || dimension.isBlank()) {
            return "Please specify a statistics dimension: users, orders, revenue, or products.";
        }

        String normalizedDim = dimension.trim().toLowerCase();

        return switch (normalizedDim) {
            case "users", "用户" -> """
                    User Statistics Report:
                    - Total registered users: 128,450
                    - Active users (30 days): 89,230
                    - New users (today): 1,256
                    - Premium subscribers: 15,820
                    - User growth rate (monthly): 8.3%
                    """;
            case "orders", "订单" -> """
                    Order Statistics Report:
                    - Total orders (all time): 2,456,780
                    - Orders (today): 8,920
                    - Orders (this month): 234,560
                    - Average order value: ¥328.50
                    - Order completion rate: 94.2%
                    - Refund rate: 2.1%
                    """;
            case "revenue", "收入" -> """
                    Revenue Statistics Report:
                    - Total revenue (all time): ¥807,902,340
                    - Revenue (today): ¥2,930,640
                    - Revenue (this month): ¥77,021,880
                    - Revenue growth (YoY): 15.6%
                    - Revenue by channel:
                      - Online: 68.5%
                      - Offline: 22.3%
                      - Partner: 9.2%
                    """;
            case "products", "产品" -> """
                    Product Statistics Report:
                    - Total products: 12,450
                    - Active products: 10,820
                    - Out of stock: 340
                    - Top-selling category: Electronics (32%)
                    - Average product rating: 4.6/5.0
                    - Products added (this month): 286
                    """;
            default -> "Unsupported statistics dimension: " + dimension +
                    ". Available dimensions: users, orders, revenue, products.";
        };
    }
}
