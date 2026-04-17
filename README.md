# Smart Campus — Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey)** and an embedded **Grizzly HTTP server** for managing campus rooms and IoT sensors. All data is held in-memory using `ConcurrentHashMap` and `ArrayList`. No database is used.

---

## API Design Overview

The API follows REST principles with a versioned base path `/api/v1`.

| Resource | Path |
|----------|------|
| Discovery | `GET /api/v1` |
| Rooms | `/api/v1/rooms` |
| Sensors | `/api/v1/sensors` |
| Sensor Readings | `/api/v1/sensors/{id}/readings` |

**Tech Stack:** Java 11 · Jersey 2.41 · Grizzly HTTP · Jackson · Maven

---

## Build & Run

### Prerequisites
- Java 11+
- Maven 3.6+

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/smart-campus-api.git
cd smart-campus-api

# 2. Build the fat JAR
mvn clean package

# 3. Run the server
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```

The server starts at: **http://localhost:8080/api/v1/**

---

## Sample curl Commands

### 1. Discovery Endpoint
```bash
curl -X GET http://localhost:8080/api/v1/
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id": "HALL-A1", "name": "Main Hall", "capacity": 200}'
```

### 3. Get All Sensors Filtered by Type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=CO2"
```

### 4. Register a Sensor (with valid roomId)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id": "OCC-001", "type": "Occupancy", "status": "ACTIVE", "currentValue": 0, "roomId": "LIB-301"}'
```

### 5. Post a New Sensor Reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value": 24.7}'
```

### 6. Delete a Room (conflict example — room has sensors)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 7. Get Reading History for a Sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

### 8. Trigger 422 — Invalid roomId on sensor registration
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id": "BAD-001", "type": "Temperature", "roomId": "DOES-NOT-EXIST"}'
```

---

## Report: Question Answers

### Part 1.1 — JAX-RS Resource Lifecycle

By default, JAX-RS creates a **new instance of a resource class for every incoming HTTP request** (request-scoped lifecycle). This means each request gets its own object, so instance variables are never shared between concurrent requests — which is safe from a threading perspective for the object itself.

However, this creates a critical challenge for **in-memory data management**. If each resource instance held its own `HashMap`, every request would see an empty, isolated store. To work around this, shared state must live **outside** the resource class — either in a static field, a singleton class, or a dependency-injected application-scoped component.

In this implementation, all data is stored in a **singleton `DataStore`** class accessed via `DataStore.getInstance()`. The maps use `ConcurrentHashMap`, which is thread-safe and prevents race conditions (e.g., two simultaneous POST requests both inserting a sensor with the same ID). Without `ConcurrentHashMap`, concurrent writes could corrupt the map's internal structure or silently drop entries.

---

### Part 1.2 — HATEOAS & Hypermedia Design

**HATEOAS** (Hypermedia as the Engine of Application State) means that an API response includes navigable links to related resources, making the API self-documenting and discoverable at runtime.

Benefits over static documentation:
- **Clients don't need to hardcode URLs** — they follow links returned in responses, so if paths change, clients that follow links adapt automatically.
- **Reduced coupling** — the client only needs to know the entry point (`/api/v1`); all other paths are discovered dynamically.
- **Living documentation** — the API itself tells consumers what actions are available and where, unlike static docs that can go out of date.

For example, the discovery endpoint at `GET /api/v1` returns a `links` map pointing to `/api/v1/rooms` and `/api/v1/sensors`, so a client can bootstrap its entire interaction from a single known URL.

---

### Part 2.1 — IDs Only vs Full Objects in List Responses

**Returning only IDs:**
- Minimal payload → very low bandwidth usage.
- Client must make N additional requests to fetch details for each ID (N+1 problem).
- Appropriate when clients only need identifiers (e.g., for a dropdown selector).

**Returning full objects:**
- Larger payload → higher bandwidth, especially with thousands of rooms.
- Client has everything it needs in a single round-trip — better for dashboards and list views.
- More data transferred than the client may need.

**Best practice:** Return full objects for moderately-sized collections (as done here). For very large collections, use **pagination** with a `?page=` and `?limit=` pattern so clients never receive unbounded lists.

---

### Part 2.2 — Is DELETE Idempotent?

**Yes, DELETE is idempotent in this implementation.**

Idempotency means that making the same request multiple times produces the same server state as making it once. In this API:

- **First DELETE** on a room that exists and has no sensors → removes it, returns `204 No Content`.
- **Second DELETE** on the same (now non-existent) room → the resource is not found, but instead of throwing a 404, the implementation still returns `204 No Content`.

This means the server state after any number of DELETE calls is the same: the room does not exist. The response code is consistent (`204`), which satisfies idempotency from both a state and a client-contract perspective. This is the correct REST design — clients should be able to retry a DELETE safely without worrying about error handling for already-deleted resources.

---

### Part 3.1 — @Consumes Mismatch Behaviour

When a POST endpoint is annotated with `@Consumes(MediaType.APPLICATION_JSON)` and a client sends a request with `Content-Type: text/plain` or `application/xml`, JAX-RS immediately rejects the request **before the method is even invoked**.

The framework returns an **HTTP 415 Unsupported Media Type** response automatically. Jersey inspects the `Content-Type` header of the incoming request and compares it against all registered resource methods. If no method matches the content type, Jersey throws a `NotSupportedException`, which results in a 415 response.

This is a key advantage of the JAX-RS content negotiation model: media type enforcement is declarative and handled at the framework level, keeping resource methods clean and focused on business logic rather than defensive content-type checking.

---

### Part 3.2 — @QueryParam vs Path-Based Filtering

**Path-based approach:** `GET /api/v1/sensors/type/CO2`
- Implies `type/CO2` is a distinct resource, which it is not — it is a filter on a collection.
- Makes it impossible to combine multiple filters cleanly (e.g., `type=CO2&status=ACTIVE`).
- Breaks REST's resource hierarchy — `CO2` is not a child of `type`.

**Query parameter approach:** `GET /api/v1/sensors?type=CO2`
- Clearly represents "filter the `/sensors` collection by type".
- Easily extensible: `?type=CO2&status=ACTIVE&roomId=LIB-301`.
- The base resource `/api/v1/sensors` remains stable and canonical; filters are optional modifiers.
- Aligns with HTTP's intended use of query strings for search/filter semantics.

Query parameters are universally preferred for **filtering, searching, sorting, and pagination** because they do not fragment the resource space.

---

### Part 4.1 — Sub-Resource Locator Pattern Benefits

The Sub-Resource Locator pattern delegates handling of a URL sub-tree to a separate class rather than defining all nested paths in one controller. In this API, `SensorResource` handles `/sensors/{sensorId}` and delegates `/sensors/{sensorId}/readings` to `SensorReadingResource`.

**Benefits:**
- **Separation of concerns** — reading history logic is completely encapsulated in `SensorReadingResource`, making it independently testable and maintainable.
- **Scalability** — in a large API (e.g., also having `/sensors/{id}/alerts`, `/sensors/{id}/config`), each sub-concern gets its own class rather than one 1000-line controller.
- **Contextual injection** — the sub-resource receives the `sensorId` at construction time, so every method within it already has its context without re-parsing path parameters repeatedly.
- **Readability** — the top-level resource class stays concise; it acts as a router rather than a god-class.

---

### Part 5.1 — Why 422 Is More Accurate Than 404

When a client POSTs a sensor with a `roomId` that does not exist, the issue is **not** that the URL `/api/v1/sensors` was not found (404 would be misleading — the endpoint exists and is perfectly valid). The problem is that the **payload references a resource that does not exist**.

**HTTP 422 Unprocessable Entity** means: "The request was well-formed (valid JSON, correct Content-Type), but it could not be processed because the semantics are invalid." The server understood the request but could not act on it because of a broken reference inside the payload.

Using 404 here would confuse clients into thinking the `/sensors` endpoint itself is missing. 422 precisely signals: "Your request arrived fine, but the data inside it refers to something that doesn't exist."

---

### Part 5.2 — Cybersecurity Risks of Stack Traces

Exposing raw Java stack traces to API consumers reveals:

1. **Internal package and class names** — attackers learn the application's structure (e.g., `com.smartcampus.resource.SensorResource`), making targeted attacks easier.
2. **Library names and versions** — a stack trace may reveal Jersey 2.41, Jackson 2.15, or Java 11. Attackers can cross-reference these against **known CVEs** (Common Vulnerabilities and Exposures) in those exact versions.
3. **File paths on the server** — absolute paths (e.g., `/home/deploy/app/...`) reveal the OS, directory layout, and potentially the username.
4. **Application logic flaws** — the sequence of method calls exposes the control flow, helping attackers identify null-pointer-prone paths or unvalidated branches to exploit.
5. **Database/query information** — if a DB exception propagates, it may include the SQL query, table names, or connection string fragments.

The `ExceptionMapper<Throwable>` catch-all prevents all of this by intercepting every unexpected error and returning only a generic `500` with a safe, opaque message.

---

### Part 5.5 — JAX-RS Filters vs Manual Logging

**Using JAX-RS filters (`ContainerRequestFilter` / `ContainerResponseFilter`):**
- Logging is a **cross-cutting concern** — it applies to every endpoint without being part of any endpoint's business logic.
- A single filter class handles all requests/responses automatically via the `@Provider` annotation.
- Adding a new endpoint automatically gets logging — no developer action required.
- Filters can be globally registered, enabled/disabled, or replaced without touching resource classes.
- Keeps resource methods **pure** — they only contain business logic, improving readability and testability.

**Manual `Logger.info()` in every method:**
- Requires a developer to remember to add logging to every new method.
- Creates duplication — the same log pattern repeated dozens of times.
- Mixing infrastructure concerns (logging) with domain logic (create sensor, delete room) violates the **Single Responsibility Principle**.
- To change the log format, you must update every resource method individually.

Filters exemplify the **AOP (Aspect-Oriented Programming)** philosophy: infrastructure concerns are declared once and applied everywhere.
