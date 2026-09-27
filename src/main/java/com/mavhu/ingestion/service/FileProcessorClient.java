package com.mavhu.ingestion.service;

import com.mavhu.ingestion.dto.ProcessFileRequest;
import com.mavhu.ingestion.processing.IngestionJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Calls the file processor controller over HTTP, off the request thread, so uploads return
 * without waiting for processing. Pointing app.processor.base-url elsewhere lets the processor
 * run as a separate deployment.
 */
@Slf4j
@Component
public class FileProcessorClient {

    static final String PROCESS_PATH = "/ingestion/v1/files/process";

    private final RestClient restClient;
    private final Environment environment;
    private final IngestionJobRepository jobRepository;
    private final String configuredBaseUrl;

    public FileProcessorClient(RestClient.Builder restClientBuilder,
                               Environment environment,
                               IngestionJobRepository jobRepository,
                               @Value("${app.processor.base-url:}") String configuredBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.environment = environment;
        this.jobRepository = jobRepository;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    @Async
    public void triggerProcessing(ProcessFileRequest request) {
        String url = baseUrl() + PROCESS_PATH;
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Triggered processing of job {} via {}", request.jobId(), url);
        } catch (Exception e) {
            log.error("Could not trigger processing of job {} via {}", request.jobId(), url, e);
            jobRepository.markFailed(request.jobId(), "Could not reach file processor: " + e.getMessage());
        }
    }

    // Defaults to this instance; the port is resolved per call because it is only known once the server has started.
    private String baseUrl() {
        if (StringUtils.hasText(configuredBaseUrl)) {
            return configuredBaseUrl.replaceAll("/+$", "");
        }
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port;
    }
}
