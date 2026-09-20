package org.kansei.tailwind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.photo.CommonsCandidate;
import org.kansei.tailwind.photo.CommonsClient;
import org.kansei.tailwind.photo.CommonsUnavailableException;
import org.kansei.tailwind.photo.ImageDownloader;
import org.kansei.tailwind.repository.AircraftPhotoRepository;
import org.kansei.tailwind.repository.AircraftTypeRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aircraft photos end to end against a real Postgres and a real storage directory: placeholder fallback,
 * admin pick from Commons, upload, replace, delete, family lookup, and the admin gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AircraftPhotoIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static Path storageRoot;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        storageRoot = java.nio.file.Files.createTempDirectory("tailwind-photos");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("SERVER_PORT", () -> "0");
        registry.add("LOG_FILE", () -> "target/test-logs/tailwind.json");
        registry.add("REDIS_HOST", () -> "localhost");
        registry.add("REDIS_PORT", () -> "6379");
        registry.add("REDIS_PASSWORD", () -> "test");
        registry.add("SHIELDWALL_INTERNAL_URI", () -> "http://localhost:1");
        registry.add("INTERNAL_SERVICE_SECRET", () -> "test");
        registry.add("RABBITMQ_HOST", () -> "localhost");
        registry.add("RABBITMQ_PORT", () -> "5672");
        registry.add("RABBITMQ_USERNAME", () -> "test");
        registry.add("RABBITMQ_PASSWORD", () -> "test");
        registry.add("AUDIT_FALLBACK_FILE", () -> "target/test-logs/audit-fallback.jsonl");
        registry.add("STORAGE_ROOT", () -> storageRoot.toString());
        registry.add("AERODATABOX_API_KEY", () -> "test");
        registry.add("AERODATABOX_MONTHLY_UNIT_CAP", () -> "400");
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9};
    private static final String A346_THUMB = "https://upload.wikimedia.org/a346.jpg";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AircraftTypeRepository aircraftTypeRepository;
    @Autowired
    private AircraftPhotoRepository aircraftPhotoRepository;
    @Autowired
    private TailwindUserRepository tailwindUserRepository;

    @MockitoBean
    private CommonsClient commonsClient;
    @MockitoBean
    private ImageDownloader imageDownloader;
    @MockitoBean
    private AuditPublisher auditPublisher;
    @MockitoBean
    private ShieldwallUserClient shieldwallUserClient;

    private UUID admin;
    private UUID user;
    private long a346;
    private long a343;

    @BeforeEach
    void setUp() {
        aircraftPhotoRepository.deleteAll();
        admin = optedIn();
        user = optedIn();
        a346 = aircraftTypeRepository.findByIcaoCode("A346").orElseThrow().getId();
        a343 = aircraftTypeRepository.findByIcaoCode("A343").orElseThrow().getId();
    }

    private UUID optedIn() {
        UUID id = UUID.randomUUID();
        tailwindUserRepository.save(TailwindUser.builder().userId(id).joinedAt(Instant.now()).build());
        return id;
    }

    // MockMvc has no PUT multipart builder, so the POST one is switched to PUT
    private MockMultipartHttpServletRequestBuilder putMultipart(MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/tailwind/admin/aircraft-types/" + a346 + "/photo");
        builder.file(file).with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return builder;
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B asAdmin(B request) {
        return request.header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN");
    }

    private void commonsHas(String title) {
        CommonsCandidate candidate = new CommonsCandidate(title, A346_THUMB, "https://commons.wikimedia.org/wiki/" + title,
                "Julian Herzog (Website)", "CC BY 4.0", 1280, 720, "image/jpeg");
        when(commonsClient.findByTitle(title)).thenReturn(Optional.of(candidate));
        when(commonsClient.search(any(), anyInt())).thenReturn(List.of(candidate));
        when(imageDownloader.download(eq(A346_THUMB), anyInt())).thenReturn(JPEG);
    }

    private void pickFromCommons(long typeId, String title) throws Exception {
        commonsHas(title);
        mockMvc.perform(asAdmin(post("/tailwind/admin/aircraft-types/" + typeId + "/photo/commons"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void aTypeWithoutAPhotoServesThePlaceholder() throws Exception {
        mockMvc.perform(get("/tailwind/aircraft-types/" + a346 + "/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Photo-Placeholder", "true"))
                .andExpect(header().string("Content-Type", "image/svg+xml"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(get("/tailwind/aircraft-types/" + a346 + "/photo/info").header("X-User-Id", user.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPhoto").value(false))
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void adminPicksACommonsPhotoAndEveryoneCanSeeItWithItsAttribution() throws Exception {
        String title = "File:South African Airways Airbus A340-313 ZS-SXE MUC 2015 02.jpg";
        pickFromCommons(a346, title);

        mockMvc.perform(get("/tailwind/aircraft-types/" + a346 + "/photo"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Photo-Placeholder", "false"))
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));

        mockMvc.perform(get("/tailwind/aircraft-types/" + a346 + "/photo/info").header("X-User-Id", user.toString()))
                .andExpect(jsonPath("$.hasPhoto").value(true))
                .andExpect(jsonPath("$.origin").value("COMMONS"))
                .andExpect(jsonPath("$.author").value("Julian Herzog (Website)"))
                .andExpect(jsonPath("$.license").value("CC BY 4.0"))
                .andExpect(jsonPath("$.sourceUrl").value("https://commons.wikimedia.org/wiki/" + title));

        verify(auditPublisher).publishWithResolvedUsername(eq("SET_AIRCRAFT_PHOTO"), eq(admin), eq("AIRCRAFT_TYPE"), eq(String.valueOf(a346)), anyMap());
    }

    @Test
    void theFamilyRouteServesTheFirstPhotoOfAnyVariant() throws Exception {
        mockMvc.perform(get("/tailwind/aircraft-families/photo").param("name", "A340"))
                .andExpect(status().isOk()).andExpect(header().string("X-Photo-Placeholder", "true"));

        pickFromCommons(a346, "File:A346.jpg");

        mockMvc.perform(get("/tailwind/aircraft-families/photo").param("name", "A340"))
                .andExpect(status().isOk()).andExpect(header().string("X-Photo-Placeholder", "false"));
        mockMvc.perform(get("/tailwind/aircraft-families/photo/info").param("name", "A340").header("X-User-Id", user.toString()))
                .andExpect(jsonPath("$.aircraftTypeId").value(a346));
        // a sibling variant without its own photo still falls back to the placeholder on its own route
        mockMvc.perform(get("/tailwind/aircraft-types/" + a343 + "/photo"))
                .andExpect(header().string("X-Photo-Placeholder", "true"));
        mockMvc.perform(get("/tailwind/aircraft-families/photo").param("name", "Nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadReplacesTheCommonsPhotoAndTheOldFileIsRemoved() throws Exception {
        pickFromCommons(a346, "File:A346.jpg");
        String firstFile = aircraftPhotoRepository.findByAircraftTypeId(a346).orElseThrow().getFileName();

        mockMvc.perform(asAdmin(putMultipart(new MockMultipartFile("file", "own.png", "image/png", PNG))
                        .param("author", "Me").param("license", "All rights reserved")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("UPLOAD"))
                .andExpect(jsonPath("$.author").value("Me"));

        assertThat(aircraftPhotoRepository.count()).isEqualTo(1);
        String secondFile = aircraftPhotoRepository.findByAircraftTypeId(a346).orElseThrow().getFileName();
        assertThat(secondFile).isNotEqualTo(firstFile);
        assertThat(storageRoot.resolve("aircraft-photos").resolve(firstFile)).doesNotExist();
        assertThat(storageRoot.resolve("aircraft-photos").resolve(secondFile)).exists();
        mockMvc.perform(get("/tailwind/aircraft-types/" + a346 + "/photo")).andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void aFileThatIsNotAnImageIsRefusedWhateverItClaimsToBe() throws Exception {
        mockMvc.perform(asAdmin(putMultipart(new MockMultipartFile("file", "evil.jpg", "image/jpeg", "<?php echo 1; ?>".getBytes()))))
                .andExpect(status().isBadRequest());

        assertThat(aircraftPhotoRepository.count()).isZero();
    }

    @Test
    void deleteRemovesTheRowTheFileAndFallsBackToThePlaceholder() throws Exception {
        pickFromCommons(a346, "File:A346.jpg");
        String file = aircraftPhotoRepository.findByAircraftTypeId(a346).orElseThrow().getFileName();

        mockMvc.perform(asAdmin(delete("/tailwind/admin/aircraft-types/" + a346 + "/photo"))).andExpect(status().isNoContent());

        assertThat(aircraftPhotoRepository.count()).isZero();
        assertThat(storageRoot.resolve("aircraft-photos").resolve(file)).doesNotExist();
        mockMvc.perform(get("/tailwind/aircraft-types/" + a346 + "/photo")).andExpect(header().string("X-Photo-Placeholder", "true"));
        mockMvc.perform(asAdmin(delete("/tailwind/admin/aircraft-types/" + a346 + "/photo"))).andExpect(status().isNotFound());
    }

    @Test
    void candidatesUseTheTypeNameByDefaultAndAcceptASearchOfTheirOwn() throws Exception {
        commonsHas("File:A346.jpg");

        mockMvc.perform(asAdmin(get("/tailwind/admin/aircraft-types/" + a346 + "/photo/candidates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("File:A346.jpg"))
                .andExpect(jsonPath("$[0].license").value("CC BY 4.0"));
        verify(commonsClient).search(eq("Airbus A340-600 aircraft"), anyInt());

        mockMvc.perform(asAdmin(get("/tailwind/admin/aircraft-types/" + a346 + "/photo/candidates")).param("q", "lufthansa a340"))
                .andExpect(status().isOk());
        verify(commonsClient).search(eq("lufthansa a340"), anyInt());
    }

    @Test
    void aNonAdminCannotTouchPhotos() throws Exception {
        MockHttpServletRequestBuilder asUser = get("/tailwind/admin/aircraft-types/" + a346 + "/photo/candidates")
                .header("X-User-Id", user.toString()).header("X-User-Role", "USER");

        mockMvc.perform(asUser).andExpect(status().isForbidden());
        mockMvc.perform(post("/tailwind/admin/aircraft-types/" + a346 + "/photo/commons")
                        .header("X-User-Id", user.toString()).header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"File:A346.jpg\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/tailwind/admin/aircraft-types/" + a346 + "/photo")
                        .header("X-User-Id", user.toString()).header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        assertThat(aircraftPhotoRepository.count()).isZero();
        verify(auditPublisher, never()).publishWithResolvedUsername(any(), any(), any(), any(), any());
    }

    @Test
    void commonsProblemsAndUnknownTypesAreReportedNotStored() throws Exception {
        when(commonsClient.findByTitle("File:Gone.jpg")).thenReturn(Optional.empty());
        mockMvc.perform(asAdmin(post("/tailwind/admin/aircraft-types/" + a346 + "/photo/commons"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"File:Gone.jpg\"}"))
                .andExpect(status().isNotFound());

        when(commonsClient.findByTitle("File:Down.jpg")).thenThrow(new CommonsUnavailableException("down"));
        mockMvc.perform(asAdmin(post("/tailwind/admin/aircraft-types/" + a346 + "/photo/commons"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"File:Down.jpg\"}"))
                .andExpect(status().isBadGateway());

        commonsHas("File:Big.jpg");
        when(imageDownloader.download(eq(A346_THUMB), anyInt())).thenThrow(new ImageDownloader.ImageDownloadException("the image is larger than 5242880 bytes"));
        mockMvc.perform(asAdmin(post("/tailwind/admin/aircraft-types/" + a346 + "/photo/commons"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"File:Big.jpg\"}"))
                .andExpect(status().isBadGateway());

        mockMvc.perform(asAdmin(post("/tailwind/admin/aircraft-types/" + a346 + "/photo/commons"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"not-a-file-title\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/tailwind/admin/aircraft-types/999999/photo/candidates"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/tailwind/aircraft-types/999999/photo")).andExpect(status().isNotFound());

        assertThat(aircraftPhotoRepository.count()).isZero();
    }
}
