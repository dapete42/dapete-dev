package org.toolforge.dapetedev;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/munin")
public class MuninResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String muninNode(String commmand) {
        return "Test";
    }

}
