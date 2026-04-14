package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", "Smart Campus Sensor & Room Management API");
        meta.put("version", "1.0");
        meta.put("description", "RESTful API for managing campus rooms and IoT sensors.");
        meta.put("contact", "admin@smartcampus.ac.uk");
        meta.put("resources", Map.of(
                "rooms",    "/api/v1/rooms",
                "sensors",  "/api/v1/sensors"
        ));
        meta.put("links", Map.of(
                "self",     "/api/v1",
                "rooms",    "/api/v1/rooms",
                "sensors",  "/api/v1/sensors"
        ));
        return Response.ok(meta).build();
    }
}
