package com.agent.infrastructure;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

/**
 * Elasticsearch configuration using the official Java API Client (8.x).
 * Reads connection parameters from application properties.
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${es.host:localhost}")
    private String host;

    @Value("${es.port:9200}")
    private int port;

    @Value("${es.username:}")
    private String username;

    @Value("${es.password:}")
    private String password;

    /**
     * Creates a low-level RestClient for Elasticsearch.
     * Configures Basic Auth when username/password are provided.
     */
    @Bean
    public RestClient restClient() {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(host, port, "http"));

        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        return builder.build();
    }

    /**
     * Creates a high-level ElasticsearchClient backed by the RestClient.
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
