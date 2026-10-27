package stegmueller.til.centic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import stegmueller.til.centic.model.Transaction;
import stegmueller.til.centic.model.TransactionType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.json.JacksonJsonParser;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Rollback(false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionControllerTest {

    @Autowired
    private MockMvc api;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private String token;
    private Long createdTransactionId;

    @BeforeAll
    void setup() {
        this.token = obtainAccessToken();
    }

    @Test
    @Order(1)
    void testGetAllTransactions() throws Exception {
        api.perform(
                        get("/api/transactions")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void testCreateTransaction() throws Exception {
        Transaction transaction = Transaction.builder()
                .amount(new BigDecimal("75.00"))
                .date(LocalDate.now())
                .description("Supermarkt")
                .type(TransactionType.EXPENSE)
                .build();

        String response = api.perform(
                        post("/api/transactions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(transaction))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        createdTransactionId = mapper.readTree(response).get("id").asLong();
    }

    @Test
    @Order(3)
    void testGetTransactionById() throws Exception {
        api.perform(
                        get("/api/transactions/" + createdTransactionId)
                                .header("Authorization", "Bearer " + token)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdTransactionId));
    }

    @Test
    @Order(4)
    void testUpdateTransaction() throws Exception {
        Transaction updated = Transaction.builder()
                .amount(new BigDecimal("90.00"))
                .date(LocalDate.now())
                .description("Supermarkt aktualisiert")
                .type(TransactionType.EXPENSE)
                .build();

        api.perform(
                        put("/api/transactions/" + createdTransactionId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(updated))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Supermarkt aktualisiert"));
    }

    @Test
    @Order(5)
    void testDeleteTransaction() throws Exception {
        api.perform(
                        delete("/api/transactions/" + createdTransactionId)
                                .header("Authorization", "Bearer " + token)
                )
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(6)
    void testUnauthorized_noToken() throws Exception {
        api.perform(get("/api/transactions"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void testForbidden_adminEndpoint_withUserToken() throws Exception {

        api.perform(
                        post("/api/categories")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Test\",\"colorCode\":\"#FF0000\",\"globalFlag\":false}")
                )
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    // Token von Keycloak holen
    private String obtainAccessToken() {
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "client_id=centic&" +
                "grant_type=password&" +
                "scope=openid profile roles offline_access&" +
                "username=user&" +
                "password=user";

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = rest.postForEntity(
                "http://localhost:8080/realms/ILV/protocol/openid-connect/token",
                entity,
                String.class
        );

        return new JacksonJsonParser().parseMap(resp.getBody()).get("access_token").toString();
    }
}