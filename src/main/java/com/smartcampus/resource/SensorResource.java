package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.DataStore;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final DataStore store = DataStore.getInstance();

    // GET /api/v1/sensors — list all sensors, optionally filtered by ?type=
    @GET
    public Collection<Sensor> getSensors(@QueryParam("type") String type) {
        Collection<Sensor> all = store.getSensors().values();
        if (type == null || type.isBlank()) return all;
        return all.stream()
                .filter(s -> s.getType().equalsIgnoreCase(type))
                .collect(Collectors.toList());
    }

    // POST /api/v1/sensors — register a new sensor (validates roomId exists)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor) {
        if (sensor.getRoomId() == null || !store.getRooms().containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                    "The roomId '" + sensor.getRoomId() + "' does not exist. Cannot register sensor.");
        }
        if (sensor.getId() == null || sensor.getId().isBlank()) {
            sensor.setId(UUID.randomUUID().toString());
        }
        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }
        store.getSensors().put(sensor.getId(), sensor);

        // Link sensor ID into the parent room
        Room room = store.getRooms().get(sensor.getRoomId());
        if (!room.getSensorIds().contains(sensor.getId())) {
            room.getSensorIds().add(sensor.getId());
        }

        URI location = URI.create("/api/v1/sensors/" + sensor.getId());
        return Response.created(location).entity(sensor).build();
    }

    // GET /api/v1/sensors/{sensorId} — get sensor by ID
    @GET
    @Path("/{sensorId}")
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) throw new NotFoundException("Sensor '" + sensorId + "' not found.");
        return Response.ok(sensor).build();
    }

    // Sub-resource locator: delegates to SensorReadingResource
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}
