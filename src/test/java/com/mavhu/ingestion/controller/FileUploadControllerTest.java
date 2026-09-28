package com.mavhu.ingestion.controller;

import com.mavhu.ingestion.dto.ProcessFileRequest;
import com.mavhu.ingestion.files.ApprovalStatus;
import com.mavhu.ingestion.files.FileRegistrationService;
import com.mavhu.ingestion.files.UploadedFile;
import com.mavhu.ingestion.processing.FileType;
import com.mavhu.ingestion.processing.IngestionJob;
import com.mavhu.ingestion.processing.IngestionJobStatus;
import com.mavhu.ingestion.service.FileProcessorClient;
import com.mavhu.ingestion.service.FileStorageService;
import com.mavhu.ingestion.service.FileUploadService;
import com.mavhu.ingestion.users.AppUser;
import com.mavhu.ingestion.users.UserDirectory;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
@Import({FileUploadService.class, FileStorageService.class})
@ActiveProfiles("test")
class FileUploadControllerTest {

    private static final String BUCKET = "mavhu-data-ingestion-engine-service";
    private static final String DATASET = "CBZ ESG Sample Dataset.xlsx";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private S3Template s3Template;

    @MockitoBean
    private FileRegistrationService fileRegistrationService;

    @MockitoBean
    private UserDirectory userDirectory;

    @MockitoBean
    private FileProcessorClient fileProcessorClient;

    @BeforeEach
    void users() {
        when(userDirectory.findActiveUser(3)).thenReturn(Optional.of(new AppUser(3, 42, "Tatenda Shumba", "t@cbz.example")));
        when(userDirectory.findActiveUser(9)).thenReturn(Optional.of(new AppUser(9, 77, "Other User", "o@other.example")));
    }

    @Test
    void uploadsDatasetToCustomerPartitionAndTriggersProcessing() throws Exception {
        byte[] content = datasetBytes();
        MockMultipartFile file = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, content);

        AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();
        AtomicReference<ObjectMetadata> uploadedMetadata = new AtomicReference<>();
        when(s3Template.upload(eq(BUCKET), any(), any(InputStream.class), any())).thenAnswer(invocation -> {
            uploadedBytes.set(invocation.<InputStream>getArgument(2).readAllBytes());
            uploadedMetadata.set(invocation.getArgument(3));
            return null;
        });
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileRegistrationService.register(eq(42), eq(3), eq(FileType.FINANCED_EMISSIONS), eq(DATASET),
                eq(XLSX_CONTENT_TYPE), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    String s3Path = invocation.getArgument(6);
                    return new FileRegistrationService.Registration(
                            new UploadedFile(fileId, 42, 3, FileType.FINANCED_EMISSIONS, DATASET, XLSX_CONTENT_TYPE,
                                    (long) content.length, s3Path, ApprovalStatus.PENDING, false, null, null, null,
                                    OffsetDateTime.now()),
                            new IngestionJob(jobId, fileId, 42, FileType.FINANCED_EMISSIONS, s3Path, DATASET,
                                    IngestionJobStatus.PENDING, null, null, OffsetDateTime.now(), null, null));
                });

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String expectedKey = "^42/files/financed_emissions/" + today + "/[0-9a-f-]{36}-CBZ_ESG_Sample_Dataset\\.xlsx$";

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42)
                        .file(file)
                        .param("userId", "3")
                        .param("fileType", "financed-emissions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.customerId").value(42))
                .andExpect(jsonPath("$.uploadedByUserId").value(3))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.fileType").value("FINANCED_EMISSIONS"))
                .andExpect(jsonPath("$.originalFileName").value(DATASET))
                .andExpect(jsonPath("$.bucket").value(BUCKET))
                .andExpect(jsonPath("$.key", matchesPattern(expectedKey)))
                .andExpect(jsonPath("$.s3Path", matchesPattern("^s3://" + BUCKET + "/42/files/financed_emissions/.*")))
                .andExpect(jsonPath("$.size").value(content.length))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"));

        assertThat(uploadedBytes.get()).isEqualTo(content);
        assertThat(uploadedMetadata.get().getContentType()).isEqualTo(XLSX_CONTENT_TYPE);
        assertThat(uploadedMetadata.get().getMetadata())
                .containsEntry("original-file-name", DATASET)
                .containsEntry("customer-id", "42")
                .containsEntry("file-type", "FINANCED_EMISSIONS");

        ArgumentCaptor<ProcessFileRequest> trigger = ArgumentCaptor.forClass(ProcessFileRequest.class);
        verify(fileProcessorClient).triggerProcessing(trigger.capture());
        assertThat(trigger.getValue().jobId()).isEqualTo(jobId);
        assertThat(trigger.getValue().customerId()).isEqualTo(42);
        assertThat(trigger.getValue().fileType()).isEqualTo("FINANCED_EMISSIONS");
        assertThat(trigger.getValue().s3Path()).matches("^s3://" + BUCKET + "/" + expectedKey.substring(1));
    }

    @Test
    void rejectsMissingFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, datasetBytes());

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42).file(file).param("userId", "3"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsMissingUserId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, datasetBytes());

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42)
                        .file(file).param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsUnknownUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, datasetBytes());

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42)
                        .file(file).param("userId", "404").param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown userId 404"));

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsUserOfAnotherCustomer() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, datasetBytes());

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42)
                        .file(file).param("userId", "9").param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User 9 does not belong to customer 42"));

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsUnknownFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, datasetBytes());

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42)
                        .file(file).param("userId", "3").param("fileType", "ESG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported fileType 'ESG'")))
                .andExpect(jsonPath("$.error", containsString("SCOPE_1_DIRECT")));

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", DATASET, XLSX_CONTENT_TYPE, new byte[0]);

        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files", 42)
                        .file(empty).param("userId", "3").param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File must not be empty"));

        verify(s3Template, never()).upload(any(), any(), any(InputStream.class), any());
        verifyNoInteractions(fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsBatchLargerThanTheLimitWithoutStoringAnything() throws Exception {
        var request = multipart("/ingestion/v1/customers/{customerId}/files/batch", 42);
        for (int i = 0; i < 21; i++) {
            request.file(new MockMultipartFile("files", "f" + i + ".xlsx", XLSX_CONTENT_TYPE, new byte[]{1}));
        }

        mockMvc.perform(request.param("userId", "3").param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Too many files: 21 (at most 20 per batch)"));

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsBatchWithoutFiles() throws Exception {
        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files/batch", 42)
                        .param("userId", "3").param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    @Test
    void rejectsBatchFromUserOfAnotherCustomer() throws Exception {
        mockMvc.perform(multipart("/ingestion/v1/customers/{customerId}/files/batch", 42)
                        .file(new MockMultipartFile("files", DATASET, XLSX_CONTENT_TYPE, datasetBytes()))
                        .param("userId", "9").param("fileType", "SCOPE_1_DIRECT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(s3Template, fileRegistrationService, fileProcessorClient);
    }

    private static byte[] datasetBytes() throws Exception {
        try (InputStream in = new ClassPathResource(DATASET).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
