package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.DataStore;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;
    private final DataStore store = DataStore.getInstance();

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    // GET /api/v1/sensors/{sensorId}/readings — fetch reading history
    @GET
    public Response getReadings() {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) throw new NotFoundException("Sensor '" + sensorId + "' not found.");

        List<SensorReading> history = store.getReadings().getOrDefault(sensorId, List.of());
        return Response.ok(history).build();
    }

    // POST /api/v1/sensors/{sensorId}/readings — append a new reading
    @POST
    public Response addReading(SensorReading reading) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) throw new NotFoundException("Sensor '" + sensorId + "' not found.");

        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(sensorId);
        }

        SensorReading newReading = new SensorReading(reading.getValue());

        // Side effect: update parent sensor's currentValue
        sensor.setCurrentValue(newReading.getValue());

        store.getReadings()
             .computeIfAbsent(sensorId, k -> new ArrayList<>())
             .add(newReading);

        URI location = URI.create("/api/v1/sensors/" + sensorId + "/readings/" + newReading.getId());
        return Response.created(location).entity(newReading).build();
    }
}
