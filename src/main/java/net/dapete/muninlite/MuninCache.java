package net.dapete.muninlite;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@Getter
class MuninCache {
    private final Instant updatedAt;
    private final String node;
    private final String version;
    private final List<String> services;
    private final Map<String, String> configData;
    private final Map<String, String> fetchData;
}
