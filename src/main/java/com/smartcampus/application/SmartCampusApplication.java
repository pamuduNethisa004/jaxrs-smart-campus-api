package com.smartcampus.application;

import com.smartcampus.exception.*;
import com.smartcampus.filter.LoggingFilter;
import com.smartcampus.resource.DiscoveryResource;
import com.smartcampus.resource.RoomResource;
import com.smartcampus.resource.SensorResource;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

@ApplicationPath("/api/v1")
public class SmartCampusApplication extends ResourceConfig {

    public SmartCampusApplication() {
        // Register all resources
        register(DiscoveryResource.class);
        register(RoomResource.class);
        register(SensorResource.class);

        // Register exception mappers
        register(RoomNotEmptyMapper.class);
        register(LinkedResourceNotFoundMapper.class);
        register(SensorUnavailableMapper.class);
        register(NotFoundMapper.class);
        register(GlobalExceptionMapper.class);

        // Register filter
        register(LoggingFilter.class);

        // Register Jackson JSON support
        register(JacksonFeature.class);
    }
}
