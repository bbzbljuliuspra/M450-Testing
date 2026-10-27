package stegmueller.til.centic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Rollback(false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryControllerTest {

    @Autowired
    private MockMvc api;

    private final ObjectMapper mapper = new ObjectMapper();

    private String adminToken;
    private String userToken;
    private Long createdCategoryId;

    @BeforeAll
    void setup() {
        // Tokens einmalig beziehen spart Zeit und eliminiert Redundanz
        this.adminToken = obtainAccessToken("admin", "1234");
        this.userToken = obtainAccessToken("user", "user");
    }

    // --- CREATE ---

    @Test
    @Order(1)
    void testCreate_asAdmin() throws Exception {
        String response = api.perform(
                        post("/api/categories")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Lebensmittel\",\"colorCode\":\"#00FF00\",\"globalFlag\":true}")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lebensmittel"))
                .andReturn().getResponse().getContentAsString();

        createdCategoryId = mapper.readTree(response).get("id").asLong();
    }

    @Test
    @Order(2)
    void testCreate_asUser_forbidden() throws Exception {
        api.perform(
                        post("/api/categories")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Verboten\",\"colorCode\":\"#FF0000\",\"globalFlag\":false}")
                )
                .andExpect(status().isForbidden());
    }

    // --- READ ---

    @Test
    @Order(3)
    void testGetAll_asUser() throws Exception {
        api.perform(
                        get("/api/categories")
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @Order(4)
    void testGetById_asUser() throws Exception {
        api.perform(
                        get("/api/categories/" + createdCategoryId)
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdCategoryId));
    }

    // --- UPDATE ---

    @Test
    @Order(5)
    void testUpdate_asAdmin() throws Exception {
        api.perform(
                        put("/api/categories/" + createdCategoryId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Lebensmittel neu\",\"colorCode\":\"#00FF00\",\"globalFlag\":true}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lebensmittel neu"));
    }

    @Test
    @Order(6)
    void testUpdate_asUser_forbidden() throws Exception {
        api.perform(
                        put("/api/categories/" + createdCategoryId)
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Hack\",\"colorCode\":\"#FF0000\",\"globalFlag\":false}")
                )
                .andExpect(status().isForbidden());
    }

    // --- DELETE ---

    @Test
    @Order(7)
    void testDelete_asUser_forbidden() throws Exception {
        api.perform(
                        delete("/api/categories/" + createdCategoryId)
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void testDelete_asAdmin() throws Exception {
        api.perform(
                        delete("/api/categories/" + createdCategoryId)
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(9)
    void testUnauthorized_noToken() throws Exception {
        api.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    private String obtainAccessToken(String username, String password) {
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = String.format("client_id=centic&grant_type=password&scope=openid profile roles&username=%s&password=%s",
                username, password);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = rest.postForEntity(
                "http://localhost:8080/realms/ILV/protocol/openid-connect/token",
                entity,
                String.class
        );

        return new JacksonJsonParser().parseMap(resp.getBody()).get("access_token").toString();
    }
}