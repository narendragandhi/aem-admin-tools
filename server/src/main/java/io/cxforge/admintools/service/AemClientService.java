package io.cxforge.admintools.service;

import io.cxforge.admintools.config.AemConfig;
import io.cxforge.admintools.exception.AemConnectionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class AemClientService {

    private final AemConfig aemConfig;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AemClientService(AemConfig aemConfig, @Qualifier("aemHttpClient") CloseableHttpClient httpClient) {
        this.aemConfig = aemConfig;
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return aemConfig.isEnabled();
    }

    @CircuitBreaker(name = "aem", fallbackMethod = "testConnectionFallback")
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

    @SuppressWarnings("unused")
    private boolean testConnectionFallback(Exception e) {
        log.warn("AEM circuit breaker is open, connection test returning false: {}", e.getMessage());
        return false;
    }

    @CircuitBreaker(name = "aem", fallbackMethod = "queryBuilderFallback")
    @Retry(name = "aem")
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

    @SuppressWarnings("unused")
    private List<Map<String, Object>> queryBuilderFallback(Map<String, String> predicates, Exception e) {
        log.error("AEM circuit breaker open for queryBuilder. Predicates: {}, Error: {}", predicates, e.getMessage());
        throw new AemConnectionException("AEM is currently unavailable. Please try again later.", e);
    }

    @CircuitBreaker(name = "aem", fallbackMethod = "findPagesFallback")
    @Retry(name = "aem")
    public List<Map<String, Object>> findPages(String rootPath, int limit) throws Exception {
        Map<String, String> predicates = new LinkedHashMap<>();
        predicates.put("path", rootPath);
        predicates.put("type", "cq:Page");
        predicates.put("p.limit", String.valueOf(limit));
        predicates.put("orderby", "@jcr:content/cq:lastModified");
        predicates.put("orderby.sort", "desc");

        return queryBuilder(predicates);
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> findPagesFallback(String rootPath, int limit, Exception e) {
        log.error("AEM circuit breaker open for findPages. Path: {}, Error: {}", rootPath, e.getMessage());
        throw new AemConnectionException("AEM is currently unavailable. Please try again later.", e);
    }

    @CircuitBreaker(name = "aem", fallbackMethod = "findAssetsFallback")
    @Retry(name = "aem")
    public List<Map<String, Object>> findAssets(String damPath, int limit) throws Exception {
        Map<String, String> predicates = new LinkedHashMap<>();
        predicates.put("path", damPath);
        predicates.put("type", "dam:Asset");
        predicates.put("p.limit", String.valueOf(limit));
        predicates.put("orderby", "@jcr:content/jcr:lastModified");
        predicates.put("orderby.sort", "desc");

        return queryBuilder(predicates);
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> findAssetsFallback(String damPath, int limit, Exception e) {
        log.error("AEM circuit breaker open for findAssets. Path: {}, Error: {}", damPath, e.getMessage());
        throw new AemConnectionException("AEM is currently unavailable. Please try again later.", e);
    }

    @CircuitBreaker(name = "aem", fallbackMethod = "findPagesWithTagFallback")
    @Retry(name = "aem")
    public List<Map<String, Object>> findPagesWithTag(String rootPath, String tagId, int limit) throws Exception {
        Map<String, String> predicates = new LinkedHashMap<>();
        predicates.put("path", rootPath);
        predicates.put("type", "cq:Page");
        predicates.put("property", "jcr:content/cq:tags");
        predicates.put("property.value", tagId);
        predicates.put("p.limit", String.valueOf(limit));

        return queryBuilder(predicates);
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> findPagesWithTagFallback(String rootPath, String tagId, int limit, Exception e) {
        log.error("AEM circuit breaker open for findPagesWithTag. Path: {}, Tag: {}, Error: {}", rootPath, tagId, e.getMessage());
        throw new AemConnectionException("AEM is currently unavailable. Please try again later.", e);
    }

    @CircuitBreaker(name = "aem", fallbackMethod = "getNodePropertiesFallback")
    @Retry(name = "aem")
    public Map<String, Object> getNodeProperties(String path) throws Exception {
        String response = get(path + ".json");
        if (response == null) {
            return null;
        }
        return objectMapper.readValue(response, new TypeReference<>() {});
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getNodePropertiesFallback(String path, Exception e) {
        log.error("AEM circuit breaker open for getNodeProperties. Path: {}, Error: {}", path, e.getMessage());
        throw new AemConnectionException("AEM is currently unavailable. Please try again later.", e);
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

    private String post(String path, List<NameValuePair> params) throws Exception {
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
