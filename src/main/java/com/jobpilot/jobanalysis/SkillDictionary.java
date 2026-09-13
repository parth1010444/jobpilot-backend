package com.jobpilot.jobanalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Curated skill dictionary: canonical name → aliases (including the canonical form).
 * All names are stored lowercase to align with {@code SkillService} normalization.
 */
@Component
public class SkillDictionary {

    private final Map<String, List<String>> canonicalToAliases;

    public SkillDictionary() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        put(map, "java", "java");
        put(map, "spring boot", "spring boot", "springboot", "spring-boot");
        put(map, "kafka", "apache kafka", "kafka");
        put(map, "postgresql", "postgresql", "postgres", "psql");
        put(map, "mysql", "mysql");
        put(map, "mongodb", "mongodb", "mongo");
        put(map, "redis", "redis");
        put(map, "elasticsearch", "elasticsearch", "elastic search");
        put(map, "docker", "docker");
        put(map, "kubernetes", "kubernetes", "k8s");
        put(map, "aws", "amazon web services", "aws");
        put(map, "gcp", "google cloud platform", "gcp", "google cloud");
        put(map, "azure", "microsoft azure", "azure");
        put(map, "system design", "system design", "systems design");
        put(map, "microservices", "microservices", "micro-services", "micro services");
        put(map, "rest", "rest api", "restful", "rest");
        put(map, "graphql", "graphql");
        put(map, "rabbitmq", "rabbitmq");
        put(map, "terraform", "terraform");
        put(map, "ci/cd", "ci/cd", "cicd", "continuous integration");
        put(map, "linux", "linux");
        put(map, "git", "git");
        put(map, "sql", "sql");
        put(map, "hibernate", "hibernate");
        put(map, "jpa", "jpa");
        put(map, "javascript", "javascript", "js");
        put(map, "typescript", "typescript", "ts");
        put(map, "python", "python");
        put(map, "go", "golang", "go");
        put(map, "react", "react.js", "reactjs", "react");
        put(map, "node.js", "node.js", "nodejs", "node");
        put(map, "c++", "c++", "cpp");
        put(map, "c#", "c#", "csharp", "c sharp");
        put(map, "grpc", "grpc");
        this.canonicalToAliases = Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, List<String>> map, String canonical, String... aliases) {
        String key = canonical.toLowerCase(Locale.ROOT);
        List<String> normalized = new ArrayList<>();
        for (String alias : aliases) {
            normalized.add(alias.toLowerCase(Locale.ROOT));
        }
        map.put(key, List.copyOf(normalized));
    }

    /**
     * @return unmodifiable map of canonical skill → aliases (all lowercase)
     */
    public Map<String, List<String>> entries() {
        return canonicalToAliases;
    }
}
