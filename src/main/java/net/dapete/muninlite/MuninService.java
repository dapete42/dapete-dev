package net.dapete.muninlite;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApplicationScoped
@Slf4j
public class MuninService {

    private MuninCache cache;

    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    private final MuninExecutor executor;

    @Inject
    public MuninService(MuninExecutor executor) {
        this.executor = executor;
    }

    void updateCache() throws MuninException {
        cacheLock.writeLock().lock();
        try {
            if (cache == null || cache.getUpdatedAt().isBefore(Instant.now().minusSeconds(30))) {
                LOG.info("Updating Munin cache");
                final var cacheBuilder = MuninCache.builder();

                final var responses = executor.execute(List.of("nodes", "version", "list"));

                final var node = responses.get(0).split("\\n")[0];
                final var version = responses.get(1);
                final var services = Arrays.asList(responses.get(2).split(" "));

                final var configCommands = services.stream()
                        .map(service -> "config " + service)
                        .toList();
                final var configResponses = executor.execute(configCommands);

                final var fetchCommands = services.stream()
                        .map(service -> "fetch " + service)
                        .toList();
                final var fetchResponses = executor.execute(fetchCommands);

                final Map<String, String> configData = new HashMap<>();
                final Map<String, String> fetchData = new HashMap<>();
                for (int i = 0; i < services.size(); i++) {
                    final var service = services.get(i);
                    configData.put(service, configResponses.get(i));
                    fetchData.put(service, fetchResponses.get(i));
                }
                cache = cacheBuilder
                        .node(node)
                        .version(version)
                        .services(services)
                        .configData(configData)
                        .fetchData(fetchData)
                        .updatedAt(Instant.now())
                        .build();
                LOG.info("Updating Munin cache completed; node '{}', version '{}': services {}", node, version, services);
            } else {
                LOG.info("Munin cache is up to date");
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    List<String> execute(List<String> commands) throws MuninException {
        updateCache();
        cacheLock.readLock().lock();
        final List<String> responses = new ArrayList<>();
        try {
            for (var command : commands) {
                final String response = switch (command) {
                    case "list" -> servicesToListResponse(cache.getServices());
                    case "nodes" -> nodeToNodesResponse(cache.getNode());
                    case "version" -> versionToVersionResponse(cache.getVersion());
                    default -> {
                        if (command.startsWith("config")) {
                            yield dataFromCache(command, cache.getConfigData());
                        } else if (command.startsWith("fetch")) {
                            yield dataFromCache(command, cache.getFetchData());
                        }
                        yield "# unknown command " + command;
                    }
                };
                responses.add(response);
            }
            return responses;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    private String nodeToNodesResponse(String node) {
        return dataToResponse(node);
    }

    private String versionToVersionResponse(String version) {
        return version + "\n";
    }

    private String servicesToListResponse(List<String> services) {
        return String.join(" ", services) + '\n';
    }

    private String dataFromCache(String command, Map<String, String> cache) {
        final var service = serviceFromCommand(command);
        if (service.isEmpty()) {
            return "# unknown service";
        }
        final var data = cache.get(service.get());
        if (data == null) {
            return "# unknown service " + service.get();
        }
        return dataToResponse(data);
    }

    private static String dataToResponse(String data) {
        return data + "\n.\n";
    }

    private Optional<String> serviceFromCommand(String command) {
        final var split = command.split(" ", 2);
        if (split.length == 0) {
            return Optional.empty();
        }
        return Optional.of(split[1]);
    }

    public Object allResponses() throws MuninException {
        updateCache();
        cacheLock.readLock().lock();
        final Map<String, String> responses = new TreeMap<>();
        try {
            responses.put("list", servicesToListResponse(cache.getServices()));
            responses.put("nodes", nodeToNodesResponse(cache.getNode()));
            responses.put("version", versionToVersionResponse(cache.getVersion()));
            for (var service : cache.getServices()) {
                responses.put("config " + service, dataToResponse(cache.getConfigData().get(service)));
                responses.put("fetch " + service, dataToResponse(cache.getFetchData().get(service)));
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        return responses;
    }

    public MuninCache test() {
        return cache;
    }

}
