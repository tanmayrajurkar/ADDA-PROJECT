package com.adda.marketbot.chat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthReportsTheKnowledgeBase() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.chunks", greaterThan(20)));
    }

    @Test
    void answersNiftyOnlyFromContext() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is the Nifty 50?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.answer", containsString("Nifty 50")))
                .andExpect(jsonPath("$.answer", not(containsString("Nifty Next 50"))))
                .andExpect(jsonPath("$.sources[0].title", containsString("Nifty 50")));
    }

    @Test
    void refusesALookalikeWordFromOutsideTheMarket() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is the capital of France?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.answer", containsString("don't have that")));
    }

    @Test
    void stillAnswersCapitalGainsFromContext() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How are capital gains taxed on listed equity?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.answer", containsString("12.5 percent")));
    }

    @Test
    void refusesQuestionsOutsideTheKnowledgeBase() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is the weather in Mumbai tomorrow?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.answer", containsString("don't have that")));
    }

    @Test
    void refusesLivePricesUsingTheScopePassage() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is the share price of TCS?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.answer", containsString("live price")))
                .andExpect(jsonPath("$.sources[0].title").value("What this assistant cannot answer"));
    }

    @Test
    void doesNotTreatValuationQuestionsAsLivePrices() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is a price-to-earnings ratio?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.answer", containsString("earnings")));
    }

    @Test
    void refusesBuyAdviceFromThePolicyPassage() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Should I buy Reliance shares?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.sources[0].title").value("No investment advice"))
                .andExpect(jsonPath("$.answer", containsString("buy")))
                .andExpect(jsonPath("$.answer", not(containsString("you should buy"))));
    }

    @Test
    void followUpUsesThePreviousQuestionAsContext() throws Exception {
        MvcResult first = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How does T+1 settlement work?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andReturn();
        String sessionId = JsonPath.read(first.getResponse().getContentAsString(), "$.sessionId");

        MvcResult second = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"message\":\"Does it move if the next day is closed?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andReturn();
        String body = second.getResponse().getContentAsString().toLowerCase();
        assertTrue(body.contains("holiday") || body.contains("working day") || body.contains("trading day") || body.contains("settlement"), body);

        mvc.perform(delete("/api/sessions/" + sessionId)).andExpect(status().isNoContent());
    }

    @Test
    void rejectsAnEmptyQuestion() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
