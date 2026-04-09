package com.smartcampus.resource;

import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.DataStore;
import com.smartcampus.model.Room;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final DataStore store = DataStore.getInstance();

    // GET /api/v1/rooms — list all rooms
    @GET
    public Collection<Room> getAllRooms() {
        return store.getRooms().values();
    }

    // POST /api/v1/rooms — create a new room
    @POST
    public Response createRoom(Room room) {
        if (room.getId() == null || room.getId().isBlank()) {
            room.setId(UUID.randomUUID().toString());
        }
        if (store.getRooms().containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "CONFLICT", "message", "Room ID already exists."))
                    .build();
        }
        store.getRooms().put(room.getId(), room);
        URI location = URI.create("/api/v1/rooms/" + room.getId());
        return Response.created(location).entity(room).build();
    }

    // GET /api/v1/rooms/{roomId} — get room by ID
    @GET
    @Path("/{roomId}")
    public Response getRoomById(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) throw new NotFoundException("Room '" + roomId + "' not found.");
        return Response.ok(room).build();
    }

    // DELETE /api/v1/rooms/{roomId} — delete room (blocked if sensors exist)
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            // Idempotent: already gone, return 204
            return Response.noContent().build();
        }
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(roomId);
        }
        store.getRooms().remove(roomId);
        return Response.noContent().build();
    }
}
