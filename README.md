# Smart Campus IoT Management API

**Student Name:** Pamudu Jayathunge
**Student ID:** [20231551]
**Module:** Client-Server Architectures (5COSC022W)
**University of Westminster**

---

## Project Introduction

This is a REST API built to manage a university Smart Campus. It allows the university to track **Rooms** and **Sensors** (like temperature monitors, CO2 sensors, and occupancy trackers). The API is built using **JAX-RS (Jersey)** with an embedded **Grizzly HTTP server**. All data is stored in-memory using `ConcurrentHashMap` — no database is used.

---

## 1. How to Run the Project

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher

### Steps

1. **Clone the repository:**
```bash
git clone https://github.com/pamuduNethisa004/jaxrs-smart-campus-api.git
cd jaxrs-smart-campus-api
```

2. **Build the project:**
```bash
mvn clean package
```

3. **Start the server:**
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```

4. **Access the API:** The API is live at `http://localhost:8080/api/v1/`

---

## 2. API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/v1/` | API Discovery — lists all available resources |
| GET | `/api/v1/rooms` | List all rooms |
| POST | `/api/v1/rooms` | Create a new room |
| GET | `/api/v1/rooms/{roomId}` | Get a specific room by ID |
| DELETE | `/api/v1/rooms/{roomId}` | Delete a room (blocked if sensors exist) |
| GET | `/api/v1/sensors` | List all sensors (supports `?type=` filter) |
| POST | `/api/v1/sensors` | Register a new sensor (validates roomId) |
| GET | `/api/v1/sensors/{sensorId}` | Get a specific sensor by ID |
| GET | `/api/v1/sensors/{sensorId}/readings` | Get reading history for a sensor |
| POST | `/api/v1/sensors/{sensorId}/readings` | Add a new reading for a sensor |

---

## 3. Sample curl Commands

### 1. Discovery Endpoint
```bash
curl -X GET http://localhost:8080/api/v1/
```

### 2. Get All Rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```

### 3. Create a New Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id": "HALL-A1", "name": "Main Hall", "capacity": 200}'
```

### 4. Register a Sensor (with valid roomId)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id": "OCC-001", "type": "Occupancy", "status": "ACTIVE", "currentValue": 0, "roomId": "LIB-301"}'
```

### 5. Get Sensors Filtered by Type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=CO2"
```

### 6. Post a New Sensor Reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value": 24.7}'
```

### 7. Get Reading History
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

### 8. Delete a Room with Sensors (triggers 409 Conflict)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 9. Register Sensor with Invalid roomId (triggers 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"type": "Temperature", "roomId": "FAKE-999"}'
```

---

## 4. Technical Report

### 4.1 JAX-RS Lifecycle and Data Synchronization

In this project, JAX-RS uses a **"Request-scoped" lifecycle**. This means a new instance of the resource class is created for every single request. Because these instances don't stay alive between requests, data cannot be stored inside them.

I solved this by using a **static `DataStore` singleton** with `ConcurrentHashMap`. I chose this specifically to handle **Thread Safety** — when multiple requests arrive at the same time, `ConcurrentHashMap` ensures that two requests cannot corrupt the data by writing simultaneously. A regular `HashMap` would not be safe in this scenario and could silently lose data or crash.

---

### 4.2 HATEOAS and Hypermedia Design

**HATEOAS** (Hypermedia as the Engine of Application State) means that API responses include navigable links to related resources. My Discovery endpoint at `GET /api/v1/` returns links to `/api/v1/rooms` and `/api/v1/sensors`, so clients can explore the entire API from a single entry point without needing static documentation.

This benefits developers because:
- Clients **do not need to hardcode URLs** — they follow the links provided by the server.
- If paths change in future versions, clients that follow links automatically adapt.
- The API becomes **self-documenting** — it tells you what it can do and where to go next.

---

### 4.3 Sensor Validation Logic

To maintain data integrity, the API ensures that a sensor cannot be registered to a room that doesn't exist. Before saving a sensor, the code validates the `roomId`. If the ID is not found in the `DataStore`, the server returns a **422 Unprocessable Entity**. This prevents "orphan data" and ensures every sensor is correctly linked to a physical location.

---

### 4.4 IDs Only vs Full Objects in List Responses

When returning a list of rooms, there are two approaches:

- **Returning only IDs:** Very small payload, low bandwidth. But the client must make extra requests to get each room's details (the N+1 problem).
- **Returning full objects:** Larger payload, but the client gets everything it needs in one request — better for dashboards.

In this API I return full objects because the collections are small. For very large collections, **pagination** (`?page=&limit=`) would be the best practice to prevent unbounded responses.

---

### 4.5 Is DELETE Idempotent?

**Yes, DELETE is idempotent in this implementation.**

- **First DELETE** on a room with no sensors → removes it → returns `204 No Content`.
- **Second DELETE** on the same (now gone) room → still returns `204 No Content`.

The server state is the same after any number of DELETE calls: the room does not exist. This is correct REST design — clients can safely retry a DELETE without receiving an unexpected error.

---

### 4.6 @Consumes Mismatch Behaviour

The POST endpoints use `@Consumes(MediaType.APPLICATION_JSON)`. If a client sends data with `Content-Type: text/plain` or `application/xml`, JAX-RS **automatically rejects the request before the method even runs**, returning an **HTTP 415 Unsupported Media Type** response.

This is a key advantage — media type enforcement is declarative and handled at the framework level, keeping resource methods clean and focused purely on business logic.

---

### 4.7 @QueryParam vs Path-Based Filtering

**Path-based approach:** `GET /api/v1/sensors/type/CO2`
- Incorrectly implies that `CO2` is a separate resource, which it is not.
- Cannot combine multiple filters cleanly.

**Query parameter approach:** `GET /api/v1/sensors?type=CO2`
- Clearly represents filtering a collection.
- Easily extensible: `?type=CO2&status=ACTIVE`.
- The base resource `/api/v1/sensors` stays stable and canonical.

Query parameters are the standard for **filtering, searching, sorting, and pagination** in REST APIs.

---

### 4.8 Sub-Resource Locator Pattern

Instead of putting all readings logic inside `SensorResource`, I delegate to a separate `SensorReadingResource` class using the Sub-Resource Locator pattern.

**Benefits:**
- **Separation of concerns** — each class has one single responsibility.
- **Scalability** — adding `/sensors/{id}/alerts` later only requires a new class.
- **Readability** — `SensorResource` acts as a clean router, not a massive god-class.

---

### 4.9 Error Mapping: 422 vs 404

When a client POSTs a sensor with a `roomId` that doesn't exist, **404 would be misleading** — it suggests the `/sensors` endpoint itself is missing, which is not the case.

**HTTP 422 Unprocessable Entity** is more accurate: *"The request arrived fine and the JSON was valid, but the data inside references something that doesn't exist."* The server understood the request but could not process it due to a broken reference in the payload.

---

### 4.10 Cybersecurity: Stack Trace Risks

Exposing raw Java stack traces is dangerous because they reveal:

1. **Internal class and package names** — attackers learn your application structure.
2. **Library versions** — e.g., Jersey 2.41. Attackers cross-reference these against known CVEs to find exploits.
3. **Server file paths** — reveals OS, directory layout, and usernames.
4. **Application logic flow** — helps attackers find vulnerable code paths.

My `GlobalExceptionMapper` catches all `Throwable` errors and returns only a safe, generic **500 Internal Server Error** — no internal details are ever leaked.

---

### 4.11 JAX-RS Filters vs Manual Logging

**Using JAX-RS filters** (`ContainerRequestFilter` / `ContainerResponseFilter`):
- A **single filter class** automatically logs every request and response.
- New endpoints automatically get logging — no extra code needed.
- Keeps resource methods **pure** — business logic only.

**Manual `Logger.info()` in every method:**
- Developer must remember to add logging to every new method.
- Creates duplication repeated dozens of times.
- Mixes infrastructure concerns with business logic — violates the **Single Responsibility Principle**.

Filters follow the **AOP (Aspect-Oriented Programming)** principle: cross-cutting concerns like logging are declared once and applied everywhere automatically.