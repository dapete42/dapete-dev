package net.dapete.muninlite;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class MuninExecutor {

    private static final java.nio.file.Path MUNINLITE_PATH = Paths.get("/tmp/muninlite");

    private static final String MUNINLITE_RESOURCE = "/muninlite/muninlite";

    private void deployMuninlite() throws MuninException {
        try {
            // Copy muninlite binary from resources to a temporary location, if it is not there yet
            if (!Files.exists(MUNINLITE_PATH)) {
                try (var inputStream = getClass().getResourceAsStream(MUNINLITE_RESOURCE)) {
                    if (inputStream == null) {
                        throw new MuninException("muninlite resource " + MUNINLITE_RESOURCE + " not found");
                    }
                    Files.copy(inputStream, MUNINLITE_PATH);
                }
            }
            // Make muninlite binary executable, if it isn't already
            if (!Files.isExecutable(MUNINLITE_PATH)) {
                final var filePermissions = Files.getPosixFilePermissions(MUNINLITE_PATH);
                filePermissions.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(MUNINLITE_PATH, filePermissions);
            }
        } catch (IOException e) {
            throw new MuninException("Error deploying muninlite binary to " + MUNINLITE_PATH, e);
        }
    }

    List<String> execute(List<String> commands) throws MuninException {
        deployMuninlite();
        final String fullResponse = executeGetFullResponse(commands);
        final var responses = new ArrayList<String>(commands.size());
        // cut off welcome line at start
        var remainingResponse = fullResponse.replaceAll("^#(.*?)\n", "");
        for (int i = 0; i < commands.size(); i++) {
            final var command = commands.get(i);
            final var splitRegex = command.startsWith("list") || command.startsWith("version") ? "\\n" : "\\n\\.\\n";
            var split = remainingResponse.split(splitRegex, 2);
            final var response = split[0];
            if (split.length > 1) {
                remainingResponse = split[1];
            } else {
                remainingResponse = "";
            }
            responses.add(response);
        }

        return responses;
    }

    private String executeGetFullResponse(List<String> commands) throws MuninException {
        final String fullResponse;
        try {
            final Process muninProcess = new ProcessBuilder(MUNINLITE_PATH.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            try {
                try (var muninOutput = muninProcess.getInputStream()) {
                    try (var muninInput = muninProcess.getOutputStream()) {
                        for (var command : commands) {
                            muninInput.write(command.getBytes(StandardCharsets.US_ASCII));
                            muninInput.write('\n');
                        }
                    }
                    fullResponse = new String(muninOutput.readAllBytes(), StandardCharsets.UTF_8);
                }
            } finally {
                muninProcess.destroy();
            }
            Integer exitValue = null;
            while (exitValue == null) {
                try {
                    exitValue = muninProcess.exitValue();
                } catch (IllegalThreadStateException e) {
                    // still running
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException ee) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (exitValue != 0) {
                final var message = String.format("Exit value from munin is " + exitValue + " instead of zero");
                LOG.error(message);
                LOG.info(fullResponse);
                throw new MuninException(message);
            }
        } catch (IOException e) {
            throw new MuninException(e);
        }
        return fullResponse;
    }

}
