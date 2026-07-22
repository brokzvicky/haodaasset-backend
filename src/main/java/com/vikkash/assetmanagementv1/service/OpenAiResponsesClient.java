package com.vikkash.assetmanagementv1.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Talks to OpenAI's Responses API (POST /v1/responses) with server-sent-event
 * streaming enabled, and hands parsed events back to the caller one at a time.
 *
 * Deliberately dependency-free beyond the JDK's own HttpClient — no OpenAI
 * SDK, no WebFlux/Reactor needed, so nothing new has to be added to pom.xml.
 *
 * Model/API key are read from application.properties (openai.api.key /
 * openai.model), which in turn read from environment variables — the same
 * pattern already used everywhere else in this project (see BREVO_API_KEY,
 * JWT_SECRET, etc. in application.properties).
 */
@Component
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model:gpt-4.1}")
    private String model;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiResponsesClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Callback surface for one streamed model turn. All methods are invoked
     * synchronously, on the calling thread, in the order OpenAI emits them.
     */
    public interface StreamListener {
        /** A chunk of assistant-visible text to append to the current answer bubble. */
        void onTextDelta(String delta);

        /** The model decided to call a tool. Arguments are already-complete, valid JSON. */
        void onFunctionCall(String callId, String toolName, String argumentsJson);

        /** The turn finished with no further tool calls pending. */
        void onCompleted();

        /** Something went wrong (network, API error, malformed stream). */
        void onError(String message);
    }

    /**
     * @param input concersation so far, as Responses-API "input" items:
     *              {"role":"system"|"user"|"assistant","content":"..."} or
     *              {"type":"function_call", "call_id":..., "name":..., "arguments":...} or
     *              {"type":"function_call_output", "call_id":..., "output":...}
     * @param tools tool/function definitions, see {@link AiToolSchema#definitions()}
     */
    public void stream(List<Map<String, Object>> input, List<Map<String, Object>> tools, StreamListener listener) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);
            body.set("input", objectMapper.valueToTree(input));
            body.set("tools", objectMapper.valueToTree(tools));
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESPONSES_URL))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("OpenAI Responses API returned {}: {}", response.statusCode(), errBody);
                listener.onError("The AI service returned an error (" + response.statusCode() + "). Please try again.");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String currentEvent = null;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        currentEvent = null;
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                        continue;
                    }
                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) continue;

                    JsonNode event = objectMapper.readTree(data);
                    String type = event.has("type") ? event.get("type").asText() : currentEvent;
                    if (type == null) continue;

                    switch (type) {
                        case "response.output_text.delta" -> {
                            String delta = event.path("delta").asText("");
                            if (!delta.isEmpty()) listener.onTextDelta(delta);
                        }
                        case "response.output_item.done" -> {
                            JsonNode item = event.path("item");
                            if ("function_call".equals(item.path("type").asText())) {
                                listener.onFunctionCall(
                                        item.path("call_id").asText(),
                                        item.path("name").asText(),
                                        item.path("arguments").asText("{}"));
                            }
                        }
                        case "response.completed" -> listener.onCompleted();
                        case "response.failed", "error" -> {
                            String msg = event.path("response").path("error").path("message").asText(
                                    event.path("message").asText("The AI assistant hit an error."));
                            listener.onError(msg);
                        }
                        default -> { /* ignore other lifecycle events (created, in_progress, etc.) */ }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to reach OpenAI Responses API", e);
            listener.onError("Couldn't reach the AI service. Please check connectivity and try again.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error while streaming from OpenAI", e);
            listener.onError("Unexpected error talking to the AI assistant.");
        }
    }
}
