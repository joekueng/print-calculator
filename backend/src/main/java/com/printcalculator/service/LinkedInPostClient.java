package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.LinkedInPostDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LinkedInPostClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String accessToken;
    private final String organizationUrn;
    private final String apiVersion;
    private final String baseUrl;
    private final Duration timeout;
    private final int postCount;

    public LinkedInPostClient(
            ObjectMapper objectMapper,
            @Value("${linkedin.access-token:}") String accessToken,
            @Value("${linkedin.organization-urn:}") String organizationUrn,
            @Value("${linkedin.api-version:202608}") String apiVersion,
            @Value("${linkedin.api-base-url:https://api.linkedin.com/rest}") String baseUrl,
            @Value("${linkedin.timeout-seconds:15}") long timeoutSeconds,
            @Value("${linkedin.post-count:5}") int postCount
    ) {
        this.objectMapper = objectMapper;
        this.accessToken = trim(accessToken);
        this.organizationUrn = trim(organizationUrn);
        this.apiVersion = trim(apiVersion);
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.postCount = Math.max(1, Math.min(10, postCount));
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean isConfigured() {
        return !accessToken.isBlank() && !organizationUrn.isBlank();
    }

    public List<LinkedInPostDto> fetchPosts() throws IOException, InterruptedException {
        if (!isConfigured()) {
            return List.of();
        }

        String author = URLEncoder.encode(organizationUrn, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/posts?author=" + author
                + "&q=author&count=" + postCount + "&sortBy=CREATED");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + accessToken)
                .header("Linkedin-Version", apiVersion)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LinkedIn Posts API returned HTTP " + response.statusCode());
        }
        List<LinkedInPostDto> posts = parsePosts(response.body());
        Map<String, ImageReference> imagesByPost = parseImageReferences(response.body());
        Map<String, String> reshareParentsByPost = parseReshareParents(response.body());
        List<LinkedInPostDto> enrichedPosts = new ArrayList<>(posts.size());
        for (LinkedInPostDto post : posts) {
            ImageReference image = imagesByPost.get(post.id());
            if (image == null) {
                String parentUrn = reshareParentsByPost.get(post.id());
                image = parentUrn == null ? null : fetchPostImageReference(parentUrn);
            }
            String imageUrl = image == null ? null : fetchImageUrl(image.urn());
            enrichedPosts.add(new LinkedInPostDto(
                    post.id(),
                    post.commentary(),
                    post.publishedAt(),
                    post.url(),
                    imageUrl,
                    image == null ? null : image.altText()
            ));
        }
        return List.copyOf(enrichedPosts);
    }

    List<LinkedInPostDto> parsePosts(String responseBody) throws IOException {
        JsonNode elements = objectMapper.readTree(responseBody).path("elements");
        if (!elements.isArray()) {
            return List.of();
        }

        List<LinkedInPostDto> posts = new ArrayList<>();
        for (JsonNode element : elements) {
            String id = element.path("id").asText("").trim();
            String commentary = element.path("commentary").asText("").trim();
            String lifecycleState = element.path("lifecycleState").asText("");
            long publishedAt = element.path("publishedAt").asLong(element.path("createdAt").asLong(0));
            if (id.isBlank() || commentary.isBlank() || publishedAt <= 0
                    || !"PUBLISHED".equals(lifecycleState)) {
                continue;
            }
            posts.add(new LinkedInPostDto(
                    id,
                    commentary,
                    Instant.ofEpochMilli(publishedAt),
                    "https://www.linkedin.com/feed/update/" + id + "/",
                    null,
                    null
            ));
        }
        posts.sort(Comparator.comparing(LinkedInPostDto::publishedAt).reversed());
        return List.copyOf(posts);
    }

    private Map<String, ImageReference> parseImageReferences(String responseBody) throws IOException {
        JsonNode elements = objectMapper.readTree(responseBody).path("elements");
        Map<String, ImageReference> imagesByPost = new HashMap<>();
        if (!elements.isArray()) {
            return imagesByPost;
        }

        for (JsonNode element : elements) {
            String postId = element.path("id").asText("").trim();
            ImageReference image = imageReference(element);
            if (!postId.isBlank() && image != null) {
                imagesByPost.put(postId, image);
            }
        }
        return imagesByPost;
    }

    private Map<String, String> parseReshareParents(String responseBody) throws IOException {
        JsonNode elements = objectMapper.readTree(responseBody).path("elements");
        Map<String, String> parentsByPost = new HashMap<>();
        if (!elements.isArray()) {
            return parentsByPost;
        }
        for (JsonNode element : elements) {
            String postId = element.path("id").asText("").trim();
            String parentUrn = element.path("reshareContext").path("parent").asText("").trim();
            if (!postId.isBlank() && !parentUrn.isBlank()) {
                parentsByPost.put(postId, parentUrn);
            }
        }
        return parentsByPost;
    }

    private ImageReference fetchPostImageReference(String postUrn) throws InterruptedException {
        String encodedPostUrn = URLEncoder.encode(postUrn, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/posts/" + encodedPostUrn))
                .timeout(timeout)
                .header("Authorization", "Bearer " + accessToken)
                .header("Linkedin-Version", apiVersion)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return imageReference(objectMapper.readTree(response.body()));
        } catch (IOException exception) {
            return null;
        }
    }

    private ImageReference imageReference(JsonNode post) {
        JsonNode content = post.path("content");
        JsonNode media = content.path("media");
        JsonNode firstMultiImage = content.path("multiImage").path("images").path(0);
        JsonNode articleThumbnail = content.path("article").path("thumbnail");
        JsonNode image = media.path("id").asText("").startsWith("urn:li:image:")
                ? media
                : firstMultiImage.path("id").asText("").startsWith("urn:li:image:")
                ? firstMultiImage
                : articleThumbnail;
        String imageUrn = image.path("id").asText(image.asText("")).trim();
        if (!imageUrn.startsWith("urn:li:image:")) {
            return null;
        }
        String altText = image.path("altText").asText("").trim();
        return new ImageReference(imageUrn, altText.isBlank() ? null : altText);
    }

    private String fetchImageUrl(String imageUrn) throws InterruptedException {
        String encodedImageUrn = URLEncoder.encode(imageUrn, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/" + encodedImageUrn))
                .timeout(timeout)
                .header("Authorization", "Bearer " + accessToken)
                .header("Linkedin-Version", apiVersion)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String downloadUrl = objectMapper.readTree(response.body()).path("downloadUrl").asText("").trim();
            return downloadUrl.isBlank() ? null : downloadUrl;
        } catch (IOException exception) {
            return null;
        }
    }

    private record ImageReference(String urn, String altText) {
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String normalized = trim(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
