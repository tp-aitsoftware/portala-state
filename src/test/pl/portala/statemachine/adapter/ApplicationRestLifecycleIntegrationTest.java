package pl.portala.statemachine.adapter.incoming.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.portala.statemachine.config.DatabaseTestcontainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An end-to-end REST test driven through MockMvc against a real PostgreSQL
 * Testcontainer (append-only schema created from schema.sql). Exercises the whole
 * vertical: HTTP -> security -> use cases -> state machine -> JDBC -> DB.
 *
 * <p>The happy paths run as {@code admin} (ROLE_ADMIN, needed for writes); two extra
 * tests pin the  authorization model: anonymous -> 401, non-admin write -> 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseTestcontainer.class)
class ApplicationRestLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("full lifecycle: create -> edit -> verify -> accept -> publish, one row per version")
    void fullLifecycle() throws Exception {
        JsonNode created = postJson(createUri(), body(Map.of("name", "req-" + tag(), "body", "initial", "author", "creator")), status().isCreated());
        UUID objId = UUID.fromString(created.get("objId").asText());
        assertThat(created.get("state").asText()).isEqualTo("CREATED");
        assertThat(created.get("version").asLong()).isZero();

        // content editable in CREATED, appends version 1
        JsonNode edited = putJson("/api/applications/" + objId + "/content",
                body(Map.of("name", "req-renamed", "body", "amended", "author", "editor", "expectedVersion", 0)),
                status().isOk());
        assertThat(edited.get("version").asLong()).isEqualTo(1);
        assertThat(edited.get("name").asText()).isEqualTo("req-renamed");

        JsonNode verified = fire(objId, "VERIFY", Map.of("author", "reviewer"), 1, status().isOk());
        assertThat(verified.get("state").asText()).isEqualTo("VERIFIED");

        JsonNode accepted = fire(objId, "ACCEPT", Map.of("author", "approver"), 2, status().isOk());
        assertThat(accepted.get("state").asText()).isEqualTo("ACCEPTED");

        JsonNode published = fire(objId, "PUBLISH", Map.of("author", "publisher"), 3, status().isOk());
        assertThat(published.get("state").asText()).isEqualTo("PUBLISHED");
        // a unique numeric publication identifier was assigned
        assertThat(published.get("publicationId").isNumber()).isTrue();

        // the complete audit history is retained (5 versions: 0..4)
        MvcResult history = mockMvc.perform(admin(get("/api/applications/" + objId + "/history"))).andExpect(status().isOk()).andReturn();
        JsonNode versions = objectMapper.readTree(history.getResponse().getContentAsString());
        assertThat(versions).hasSize(5);
        assertThat(versions.get(0).get("version").asLong()).isZero();
        assertThat(versions.get(4).get("state").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("delete without a reason is a guard conflict (409)")
    void deleteWithoutReasonConflicts() throws Exception {
        JsonNode created = postJson(createUri(), body(Map.of("name", "del-" + tag(), "body", "b")), status().isCreated());
        UUID objId = UUID.fromString(created.get("objId").asText());
        fire(objId, "DELETE", Map.of("author", "admin"), 0, status().isConflict());
    }

    @Test
    @DisplayName("an illegal transition (ACCEPT straight from CREATED) conflicts (409)")
    void illegalTransitionConflicts() throws Exception {
        JsonNode created = postJson(createUri(), body(Map.of("name", "ill-" + tag(), "body", "b")), status().isCreated());
        UUID objId = UUID.fromString(created.get("objId").asText());
        fire(objId, "ACCEPT", Map.of("author", "a"), 0, status().isConflict());
    }

    @Test
    @DisplayName("optimistic locking: a stale expectedVersion is refused (412)")
    void staleVersionRejected() throws Exception {
        JsonNode created = postJson(createUri(), body(Map.of("name", "lock-" + tag(), "body", "b")), status().isCreated());
        UUID objId = UUID.fromString(created.get("objId").asText());
        mockMvc.perform(admin(post("/api/applications/" + objId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(Map.of("event", "VERIFY", "author", "a", "expectedVersion", 99))))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("create without a body fails bean validation (400)")
    void createRequiresBody() throws Exception {
        mockMvc.perform(admin(post(createUri())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(Map.of("name", "only-name-" + tag()))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("list filters by name (case-insensitive) and hides DELETED unless asked")
    void browsingAndSoftDeleteFilter() throws Exception {
        String name = "browse-" + tag();
        JsonNode created = postJson(createUri(), body(Map.of("name", name, "body", "b")), status().isCreated());
        UUID objId = UUID.fromString(created.get("objId").asText());

        // matches case-insensitively on the CURRENT version's name
        mockMvc.perform(admin(get("/api/applications").param("name", name.toUpperCase()).param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].objId").value(objId.toString()));

        // delete it, then confirm it disappears from the default list but shows under state=DELETED
        fire(objId, "DELETE", Map.of("author", "admin", "reason", "FINISH_ON_DELETE"), 0, status().isOk());

        mockMvc.perform(admin(get("/api/applications").param("name", name)))
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(admin(get("/api/applications").param("name", name).param("state", "DELETED")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.content[0].state").value("DELETED"));
    }

    @Test
    @DisplayName("a unique publicationId is issued per publish (F5)")
    void publicationIdsAreUnique() throws Exception {
        long first = publishAndGetId();
        long second = publishAndGetId();
        assertThat(second).isNotEqualTo(first);
    }

    // ---- PORTAL-STATE-2: security / authorization ----------------------------

    @Test
    @DisplayName("anonymous request to the API is unauthenticated (401)")
    void anonymousCannotReachApi() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a read-only USER may read but not write (403 on create)")
    void nonAdminCannotWrite() throws Exception {
        String name = "reader-" + tag();
        // read is fine for any authenticated principal
        mockMvc.perform(get("/api/applications").param("name", name).with(user("reader").roles("USER")))
                .andExpect(status().isOk());
        // create is a write -> admin only
        mockMvc.perform(post(createUri())
                        .with(user("reader").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(Map.of("name", name, "body", "b")))))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ---------------------------------------------------------------

    private long publishAndGetId() throws Exception {
        JsonNode created = postJson(createUri(), body(Map.of("name", "pub-" + tag(), "body", "b")), status().isCreated());
        UUID objId = UUID.fromString(created.get("objId").asText());
        fire(objId, "VERIFY", Map.of("author", "a"), 0, status().isOk());
        fire(objId, "ACCEPT", Map.of("author", "a"), 1, status().isOk());
        JsonNode published = fire(objId, "PUBLISH", Map.of("author", "a"), 2, status().isOk());
        return published.get("publicationId").asLong();
    }

    private JsonNode fire(UUID objId, String event, Map<String, Object> extra, long expectedVersion, org.springframework.test.web.servlet.ResultMatcher statusMatcher) throws Exception {
        Map<String, Object> fields = new HashMap<>(extra);
        fields.put("event", event);
        fields.put("expectedVersion", expectedVersion);
        return postJson("/api/applications/" + objId + "/events", body(fields), statusMatcher);
    }

    private static String createUri() {
        return "/api/applications";
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> body(Map<String, Object> fields) {
        return new HashMap<>(fields);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Every business request runs as the admin user (writes need ROLE_ADMIN). */
    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.with(user("admin").roles("ADMIN"));
    }

    private JsonNode postJson(String uri, Map<String, Object> payload, org.springframework.test.web.servlet.ResultMatcher statusMatcher) throws Exception {
        MvcResult result = mockMvc.perform(admin(post(uri).contentType(MediaType.APPLICATION_JSON).content(json(payload))))
                .andExpect(statusMatcher).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode putJson(String uri, Map<String, Object> payload, org.springframework.test.web.servlet.ResultMatcher statusMatcher) throws Exception {
        MvcResult result = mockMvc.perform(admin(put(uri).contentType(MediaType.APPLICATION_JSON).content(json(payload))))
                .andExpect(statusMatcher).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
