package com.mavhu.ingestion;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full flow against the running app: upload over HTTP -> S3 (in-memory stand-in) -> async HTTP
 * call to the file processor -> rows in the file type's table (H2, created from the real DDL file).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileIngestionEndToEndTest {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private S3Template s3Template;

    private final Map<String, byte[]> s3Objects = new ConcurrentHashMap<>();

    private TestUsers.Ids ids;

    @BeforeEach
    void inMemoryS3AndUsers() {
        ids = TestUsers.seed(jdbcTemplate);
        when(s3Template.upload(anyString(), anyString(), any(InputStream.class), any())).thenAnswer(invocation -> {
            s3Objects.put(invocation.getArgument(0) + "/" + invocation.getArgument(1),
                    invocation.<InputStream>getArgument(2).readAllBytes());
            return null;
        });
        when(s3Template.download(anyString(), anyString())).thenAnswer(invocation -> {
            byte[] content = s3Objects.get(invocation.getArgument(0) + "/" + invocation.getArgument(1));
            S3Resource resource = mock(S3Resource.class);
            when(resource.getInputStream()).thenAnswer(ignored -> new ByteArrayInputStream(content));
            return resource;
        });
    }

    static Stream<Arguments> sampleFiles() {
        return Stream.of(
                Arguments.of("02_Entity_Reference.xlsx", "ENTITY_REFERENCE", "esg_entity_reference", 9),
                Arguments.of("03_Scope_1_Direct.xlsx", "SCOPE_1_DIRECT", "esg_scope1_direct_emissions", 10),
                Arguments.of("04_Scope_2_Energy.xlsx", "SCOPE_2_ENERGY", "esg_scope2_energy_emissions", 7),
                Arguments.of("05_Scope_3_Operational.xlsx", "SCOPE_3_OPERATIONAL", "esg_scope3_operational_emissions", 9),
                Arguments.of("06_Financed_Emissions.xlsx", "FINANCED_EMISSIONS", "esg_financed_emissions", 19),
                Arguments.of("07_DQ_Portfolio_Summary.xlsx", "DQ_PORTFOLIO_SUMMARY", "esg_dq_portfolio_summary", 10),
                Arguments.of("08_Insurance_Emissions.xlsx", "INSURANCE_EMISSIONS", "esg_insurance_emissions", 7),
                Arguments.of("09_Facilitated_Emissions.xlsx", "FACILITATED_EMISSIONS", "esg_facilitated_emissions", 3),
                Arguments.of("10_Satellite_Geospatial.xlsx", "SATELLITE_GEOSPATIAL", "esg_satellite_geospatial", 8),
                Arguments.of("11_Social_Fin_Inclusion.xlsx", "SOCIAL_FIN_INCLUSION", "esg_social_financial_inclusion", 5),
                Arguments.of("12_Social_Workforce.xlsx", "SOCIAL_WORKFORCE", "esg_social_workforce", 9),
                Arguments.of("13_Governance_Board.xlsx", "GOVERNANCE_BOARD", "esg_governance_board", 5),
                Arguments.of("14_Governance_Incidents.xlsx", "GOVERNANCE_INCIDENTS", "esg_governance_incidents", 5),
                Arguments.of("15_Ingestion_Audit_Log.xlsx", "INGESTION_AUDIT_LOG", "esg_ingestion_audit_log", 10));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("sampleFiles")
    void uploadedSampleIsProcessedIntoItsTable(String fileName, String fileType, String table, int expectedRows) {
        Map<String, Object> upload = upload(fileName, fileType);
        String jobId = (String) upload.get("jobId");
        String fileId = (String) upload.get("fileId");

        Map<String, Object> job = awaitFinished(jobId);

        assertThat(job.get("status")).as("error: %s", job.get("errorMessage")).isEqualTo("COMPLETED");
        assertThat(job.get("rowsIngested")).isEqualTo(expectedRows);
        assertThat(job.get("targetTable")).isEqualTo("mavhu." + table);
        assertThat(rowsForJob(table, jobId)).isEqualTo(expectedRows);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mavhu." + table + " WHERE file_id = ? AND customer_id = ? AND is_approved = FALSE",
                Integer.class, UUID.fromString(fileId), ids.customerId())).isEqualTo(expectedRows);
        assertThat(upload).containsEntry("approvalStatus", "PENDING").containsEntry("uploadedByUserId", ids.uploaderId());
    }

    @Test
    void storesTypedValues() {
        String jobId = (String) upload("06_Financed_Emissions.xlsx", "FINANCED_EMISSIONS").get("jobId");
        assertThat(awaitFinished(jobId).get("status")).isEqualTo("COMPLETED");

        Map<String, Object> fe001 = jdbcTemplate.queryForMap(
                "SELECT * FROM mavhu.esg_financed_emissions WHERE ingestion_job_id = ? AND position_id = 'FE-001'",
                UUID.fromString(jobId));

        assertThat(fe001.get("SUBSIDIARY")).isEqualTo("CBZBANK");
        assertThat((BigDecimal) fe001.get("OUTSTANDING_AMOUNT_USD")).isEqualByComparingTo("1250000");
        assertThat((BigDecimal) fe001.get("FINANCED_EMISSIONS_TCO2E")).isEqualByComparingTo("742.1875");
        assertThat(fe001.get("DATA_QUALITY_SCORE")).isEqualTo(3);
        assertThat(fe001.get("MAVHU_MRV_ENHANCED")).isEqualTo(true);
        assertThat(fe001.get("SOURCE_ROW_NUMBER")).isEqualTo(3);
    }

    @Test
    void fileUploadedUnderWrongTypeFailsWithoutWritingRows() {
        String jobId = (String) upload("03_Scope_1_Direct.xlsx", "SCOPE_2_ENERGY").get("jobId");

        Map<String, Object> job = awaitFinished(jobId);

        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat((String) job.get("errorMessage")).contains("does not match file type SCOPE_2_ENERGY");
        assertThat(rowsForJob("esg_scope2_energy_emissions", jobId)).isZero();
    }

    @Test
    void reprocessingAFileReplacesItsRowsInsteadOfDuplicating() {
        Map<String, Object> upload = upload("12_Social_Workforce.xlsx", "SOCIAL_WORKFORCE");
        String jobId = (String) upload.get("jobId");
        assertThat(awaitFinished(jobId).get("status")).isEqualTo("COMPLETED");

        ResponseEntity<Map> accepted = restTemplate.postForEntity("/ingestion/v1/files/process",
                Map.of("customerId", ids.customerId(), "s3Path", upload.get("s3Path"), "fileType", "SOCIAL_WORKFORCE"), Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.getBody()).containsEntry("jobId", jobId);

        assertThat(awaitFinished(jobId).get("status")).isEqualTo("COMPLETED");
        assertThat(rowsForJob("esg_social_workforce", jobId)).isEqualTo(9);
    }

    @Test
    void processorRejectsAnotherCustomersFile() {
        String s3Path = (String) upload("13_Governance_Board.xlsx", "GOVERNANCE_BOARD").get("s3Path");

        ResponseEntity<Map> response = restTemplate.postForEntity("/ingestion/v1/files/process",
                Map.of("customerId", ids.otherCustomerId(), "s3Path", s3Path, "fileType", "GOVERNANCE_BOARD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("error")).contains("does not belong to customer " + ids.otherCustomerId());
    }

    @Test
    void listsSupportedFileTypes() {
        ResponseEntity<Object[]> response = restTemplate.getForEntity("/ingestion/v1/files/types", Object[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(14);
    }

    private Map<String, Object> upload(String fileName, String fileType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ClassPathResource("sample/" + fileName));
        body.add("fileType", fileType);
        body.add("userId", ids.uploaderId());
        int customerId = ids.customerId();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/ingestion/v1/customers/{customerId}/files", new HttpEntity<>(body, headers), Map.class, customerId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = response.getBody();
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitFinished(String jobId) {
        return await().atMost(PROCESSING_TIMEOUT).pollInterval(Duration.ofMillis(100))
                .until(() -> restTemplate.getForObject("/ingestion/v1/files/jobs/{jobId}", Map.class, jobId),
                        job -> job != null && ("COMPLETED".equals(job.get("status")) || "FAILED".equals(job.get("status"))));
    }

    // --- approval workflow ---------------------------------------------------------------------

    @Test
    void managerApprovesPendingFileAndRowsBecomeApproved() {
        Map<String, Object> upload = upload("04_Scope_2_Energy.xlsx", "SCOPE_2_ENERGY");
        String fileId = (String) upload.get("fileId");
        assertThat(awaitFinished((String) upload.get("jobId")).get("status")).isEqualTo("COMPLETED");
        assertThat(fileIds("PENDING")).contains(fileId);

        ResponseEntity<Map> approved = review(fileId, "approve", ids.managerId(), "Figures reconciled");

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody())
                .containsEntry("approvalStatus", "APPROVED")
                .containsEntry("isApproved", true)
                .containsEntry("reviewComment", "Figures reconciled");
        assertThat((Map<String, Object>) approved.getBody().get("reviewedBy"))
                .containsEntry("userId", ids.managerId()).containsEntry("name", "Tendai Moyo");
        assertThat((Map<String, Object>) approved.getBody().get("uploadedBy"))
                .containsEntry("userId", ids.uploaderId()).containsEntry("name", "Tatenda Shumba");
        assertThat(fileIds("APPROVED")).contains(fileId);
        assertThat(fileIds("PENDING")).doesNotContain(fileId);
        assertThat(rowsApproved("esg_scope2_energy_emissions", fileId)).containsOnly(true).hasSize(7);
    }

    @Test
    void managerRejectsPendingFileWithReason() {
        Map<String, Object> upload = upload("11_Social_Fin_Inclusion.xlsx", "SOCIAL_FIN_INCLUSION");
        String fileId = (String) upload.get("fileId");
        awaitFinished((String) upload.get("jobId"));

        ResponseEntity<Map> rejected = review(fileId, "reject", ids.managerId(), "Repayment rates look wrong");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody())
                .containsEntry("approvalStatus", "REJECTED")
                .containsEntry("isApproved", false)
                .containsEntry("reviewComment", "Repayment rates look wrong");
        assertThat(fileIds("REJECTED")).contains(fileId);
        assertThat(rowsApproved("esg_social_financial_inclusion", fileId)).containsOnly(false).hasSize(5);
    }

    @Test
    void reviewedFilesCannotBeReviewedAgainOrReprocessed() {
        Map<String, Object> upload = upload("09_Facilitated_Emissions.xlsx", "FACILITATED_EMISSIONS");
        String fileId = (String) upload.get("fileId");
        awaitFinished((String) upload.get("jobId"));
        assertThat(review(fileId, "approve", ids.managerId(), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> again = review(fileId, "reject", ids.managerId(), "changed my mind");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) again.getBody().get("error")).contains("is already APPROVED");

        ResponseEntity<Map> reprocess = restTemplate.postForEntity("/ingestion/v1/files/process",
                Map.of("customerId", ids.customerId(), "s3Path", upload.get("s3Path"), "fileType", "FACILITATED_EMISSIONS"),
                Map.class);
        assertThat(reprocess.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failedIngestionCannotBeApprovedButCanBeRejected() {
        Map<String, Object> upload = upload("03_Scope_1_Direct.xlsx", "SCOPE_2_ENERGY");
        String fileId = (String) upload.get("fileId");
        assertThat(awaitFinished((String) upload.get("jobId")).get("status")).isEqualTo("FAILED");

        ResponseEntity<Map> approve = review(fileId, "approve", ids.managerId(), null);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) approve.getBody().get("error")).contains("ingestion is FAILED");

        assertThat(review(fileId, "reject", ids.managerId(), "Wrong template").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyManagersOfTheSameCustomerCanReview() {
        Map<String, Object> upload = upload("13_Governance_Board.xlsx", "GOVERNANCE_BOARD");
        String fileId = (String) upload.get("fileId");
        awaitFinished((String) upload.get("jobId"));

        ResponseEntity<Map> byUploader = review(fileId, "approve", ids.uploaderId(), null);
        assertThat(byUploader.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((String) byUploader.getBody().get("error")).contains("is not a MANAGER");

        ResponseEntity<Map> byAuditor = review(fileId, "reject", ids.auditorId(), "no");
        assertThat(byAuditor.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> byOtherCustomersManager = review(fileId, "approve", ids.otherManagerId(), null);
        assertThat(byOtherCustomersManager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((String) byOtherCustomersManager.getBody().get("error")).contains("does not belong to customer");

        assertThat(restTemplate.getForObject("/ingestion/v1/customers/{c}/files/{f}", Map.class, ids.customerId(), fileId))
                .containsEntry("approvalStatus", "PENDING");
    }

    @Test
    void reviewRequestValidation() {
        Map<String, Object> upload = upload("14_Governance_Incidents.xlsx", "GOVERNANCE_INCIDENTS");
        String fileId = (String) upload.get("fileId");
        awaitFinished((String) upload.get("jobId"));

        assertThat(review(fileId, "reject", ids.managerId(), "  ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(review(fileId, "approve", null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(review(fileId, "approve", 999_999, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(review(UUID.randomUUID().toString(), "approve", ids.managerId(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // A file is only visible under its own customer.
        assertThat(restTemplate.getForEntity("/ingestion/v1/customers/{c}/files/{f}", Map.class,
                ids.otherCustomerId(), fileId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/ingestion/v1/customers/{c}/files?status=DONE", Map.class,
                ids.customerId()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadRequiresAUserOfTheCustomer() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ClassPathResource("sample/13_Governance_Board.xlsx"));
        body.add("fileType", "GOVERNANCE_BOARD");
        body.add("userId", ids.otherManagerId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.postForEntity("/ingestion/v1/customers/{customerId}/files",
                new HttpEntity<>(body, headers), Map.class, ids.customerId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- CORS --------------------------------------------------------------------------------------

    @Test
    void corsPreflightAllowsConfiguredFrontendOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setAccessControlRequestMethod(org.springframework.http.HttpMethod.POST);
        headers.setAccessControlRequestHeaders(java.util.List.of("content-type"));

        ResponseEntity<String> preflight = restTemplate.exchange("/ingestion/v1/customers/{c}/files",
                org.springframework.http.HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class, ids.customerId());

        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(preflight.getHeaders().getAccessControlAllowMethods())
                .contains(org.springframework.http.HttpMethod.POST);
    }

    @Test
    void corsAllowsActualRequestFromConfiguredOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:3000");

        ResponseEntity<String> response = restTemplate.exchange("/ingestion/v1/files/types",
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
    }

    @Test
    void corsRejectsUnknownOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://evil.example");
        headers.setAccessControlRequestMethod(org.springframework.http.HttpMethod.POST);

        ResponseEntity<String> preflight = restTemplate.exchange("/ingestion/v1/customers/{c}/files",
                org.springframework.http.HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class, ids.customerId());

        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private ResponseEntity<Map> review(String fileId, String action, Integer managerUserId, String comment) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("managerUserId", managerUserId);
        request.put("comment", comment);
        return restTemplate.postForEntity("/ingestion/v1/customers/{c}/files/{f}/" + action, request, Map.class,
                ids.customerId(), fileId);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> fileIds(String status) {
        Map<String, Object>[] files = restTemplate.getForObject("/ingestion/v1/customers/{c}/files?status={s}",
                Map[].class, ids.customerId(), status);
        return java.util.Arrays.stream(files).map(f -> (String) f.get("fileId")).toList();
    }

    private java.util.List<Boolean> rowsApproved(String table, String fileId) {
        return jdbcTemplate.queryForList("SELECT is_approved FROM mavhu." + table + " WHERE file_id = ?",
                Boolean.class, UUID.fromString(fileId));
    }

    private int rowsForJob(String table, String jobId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mavhu." + table + " WHERE ingestion_job_id = ?",
                Integer.class, UUID.fromString(jobId));
    }
}
