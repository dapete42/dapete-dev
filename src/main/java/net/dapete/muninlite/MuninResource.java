package net.dapete.muninlite;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/munin")
public final class MuninResource {

    private final MuninService muninService;

    @Inject
    public MuninResource(MuninService muninService) {
        this.muninService = muninService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        try {
            final var result = muninService.allResponses();
            return Response.ok(result).build();
        } catch (MuninException e) {
            return Response.serverError().entity(e).build();
        }
    }

    @Path("runCommand")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response runCommand(@QueryParam("command") List<String> commands) {
        try {
            final var result = muninService.execute(commands).getFirst();
            return Response.ok(result).build();
        } catch (MuninException e) {
            return Response.serverError().entity(e).build();
        }
    }

    @Path("runCommands")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response runCommands(@QueryParam("command") List<String> commands) {
        try {
            final var result = muninService.execute(commands);
            return Response.ok(result).build();
        } catch (MuninException e) {
            return Response.serverError().entity(e).build();
        }
    }

}
