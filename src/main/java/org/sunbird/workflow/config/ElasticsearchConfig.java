package org.sunbird.workflow.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {
    Logger logger = LogManager.getLogger(ElasticsearchConfig.class);

    @Value("${user_es_host}")
    private String userESHost;

    @Value("${user_es_port}")
    private String userESPort;

    @Bean
    public RestHighLevelClient elasticsearchClient() {
        List<String> host = new ArrayList<>();
        List<Integer> ports = new ArrayList<>();
        String[] splitedHost = userESHost.split(",");
        Collections.addAll(host, splitedHost);

        String[] splitedPort = userESPort.split(",");
        for (String val : splitedPort) {
            ports.add(Integer.parseInt(val));
        }
        HttpHost[] httpHost = new HttpHost[host.size()];
        for (int i = 0; i < host.size(); i++) {
            httpHost[i] = new HttpHost(host.get(i), 9200);
        }

        RestClientBuilder builder = RestClient.builder(httpHost)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(5000) // 5 seconds connect timeout
                        .setSocketTimeout(60000) // 60 seconds socket timeout
                );

        RestHighLevelClient restClient = new RestHighLevelClient(builder);
        logger.info("ElasticsearchConfig:: RestHighLevelClient initialisation done.");
        return restClient;
    }
}
