package com.adobe.aem.admintools.service;

import com.adobe.aem.admintools.config.AemConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AemClientService {

    private final AemConfig aemConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isEnabled() {
        return aemConfig.isEnabled();
    }

    public boolean testConnection() {
        if (!isEnabled()) {
            return false;
        }
        try {
            String response = get("/libs/granite/core/content/login.html");
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.warn("AEM connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> queryBuilder(Map<String, String> predicates) throws Exception {
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, String> entry : predicates.entrySet()) {
            if (!queryString.isEmpty()) {
                queryString.append("&");
            }
            queryString.append(entry.getKey()).append("=").append(entry.getValue());
        }
        queryString.append("&p.hits=full&p.nodedepth=2");

        String url = "/bin/querybuilder.json?" + queryString;
        String response = get(url);

        if (response == null) {
            return Collections.emptyList();
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode hits = root.get("hits");

        if (hits == null || !hits.isArray()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            Map<String, Object> item = objectMapper.convertValue(hit, new TypeReference<>() {});
            results.add(item);
        }

        return results;
    }

    public List<Map<String, Object>> findPages(String rootPath, int limit) throws Exception {
        Map<String, String> predicates = new LinkedHashMap<>();
        predicates.put("path", rootPath);
        predicates.put("type", "cq:Page");
        predicates.put("p.limit", String.valueOf(limit));
        predicates.put("orderby", "@jcr:content/cq:lastModified");
        predicates.put("orderby.sort", "desc");

        return queryBuilder(predicates);
    }

    public List<Map<String, Object>> findAssets(String damPath, int limit) throws Exception {
        Map<String, String> predicates = new LinkedHashMap<>();
        predicates.put("path", damPath);
        predicates.put("type", "dam:Asset");
        predicates.put("p.limit", String.valueOf(limit));
        predicates.put("orderby", "@jcr:content/jcr:lastModified");
        predicates.put("orderby.sort", "desc");

        return queryBuilder(predicates);
    }

    public List<Map<String, Object>> findPagesWithTag(String rootPath, String tagId, int limit) throws Exception {
        Map<String, String> predicates = new LinkedHashMap<>();
        predicates.put("path", rootPath);
        predicates.put("type", "cq:Page");
        predicates.put("property", "jcr:content/cq:tags");
        predicates.put("property.value", tagId);
        predicates.put("p.limit", String.valueOf(limit));

        return queryBuilder(predicates);
    }

    public Map<String, Object> getNodeProperties(String path) throws Exception {
        String response = get(path + ".json");
        if (response == null) {
            return null;
        }
        return objectMapper.readValue(response, new TypeReference<>() {});
    }

    public Map<String, Object> getPageProperties(String pagePath) throws Exception {
        return getNodeProperties(pagePath + "/jcr:content");
    }

    public String addTag(String pagePath, String tagId) throws Exception {
        Map<String, Object> currentProps = getPageProperties(pagePath);
        if (currentProps == null) {
            return "Page not found: " + pagePath;
        }

        Object existingTags = currentProps.get("cq:tags");
        Set<String> tags = new HashSet<>();

        if (existingTags instanceof List) {
            tags.addAll((List<String>) existingTags);
        } else if (existingTags instanceof String) {
            tags.add((String) existingTags);
        }

        if (tags.contains(tagId)) {
            return "Tag already exists on page";
        }

        tags.add(tagId);

        List<NameValuePair> params = new ArrayList<>();
        for (String tag : tags) {
            params.add(new BasicNameValuePair("cq:tags", tag));
        }
        params.add(new BasicNameValuePair("cq:tags@TypeHint", "String[]"));

        return post(pagePath + "/jcr:content", params);
    }

    public String removeTag(String pagePath, String tagId) throws Exception {
        Map<String, Object> currentProps = getPageProperties(pagePath);
        if (currentProps == null) {
            return "Page not found: " + pagePath;
        }

        Object existingTags = currentProps.get("cq:tags");
        Set<String> tags = new HashSet<>();

        if (existingTags instanceof List) {
            tags.addAll((List<String>) existingTags);
        } else if (existingTags instanceof String) {
            tags.add((String) existingTags);
        }

        if (!tags.contains(tagId)) {
            return "Tag not found on page";
        }

        tags.remove(tagId);

        List<NameValuePair> params = new ArrayList<>();
        if (tags.isEmpty()) {
            params.add(new BasicNameValuePair("cq:tags@Delete", "true"));
        } else {
            for (String tag : tags) {
                params.add(new BasicNameValuePair("cq:tags", tag));
            }
            params.add(new BasicNameValuePair("cq:tags@TypeHint", "String[]"));
        }

        return post(pagePath + "/jcr:content", params);
    }

    public String replaceTag(String pagePath, String oldTag, String newTag) throws Exception {
        Map<String, Object> currentProps = getPageProperties(pagePath);
        if (currentProps == null) {
            return "Page not found: " + pagePath;
        }

        Object existingTags = currentProps.get("cq:tags");
        Set<String> tags = new HashSet<>();

        if (existingTags instanceof List) {
            tags.addAll((List<String>) existingTags);
        } else if (existingTags instanceof String) {
            tags.add((String) existingTags);
        }

        if (!tags.contains(oldTag)) {
            return "Source tag not found on page";
        }

        tags.remove(oldTag);
        tags.add(newTag);

        List<NameValuePair> params = new ArrayList<>();
        for (String tag : tags) {
            params.add(new BasicNameValuePair("cq:tags", tag));
        }
        params.add(new BasicNameValuePair("cq:tags@TypeHint", "String[]"));

        return post(pagePath + "/jcr:content", params);
    }

    private String get(String path) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = aemConfig.getAuthorUrl() + path;
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", "Basic " + aemConfig.getBasicAuth());

            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    return EntityUtils.toString(response.getEntity());
                } else if (statusCode == 404) {
                    return null;
                } else {
                    throw new RuntimeException("AEM request failed with status: " + statusCode);
                }
            });
        }
    }

    private String post(String path, List<NameValuePair> params) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = aemConfig.getAuthorUrl() + path;
            HttpPost request = new HttpPost(url);
            request.setHeader("Authorization", "Basic " + aemConfig.getBasicAuth());
            request.setEntity(new UrlEncodedFormEntity(params));

            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return "Success";
                } else {
                    String body = EntityUtils.toString(response.getEntity());
                    throw new RuntimeException("AEM POST failed: " + statusCode + " - " + body);
                }
            });
        }
    }
}
