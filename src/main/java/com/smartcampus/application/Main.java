package com.smartcampus.application;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;
import java.util.logging.Logger;

public class Main {

    private static final String BASE_URI = "http://0.0.0.0:8080/api/v1/";
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        // Use package scanning — Jersey auto-discovers all @Path, @Provider, @Provider-annotated classes
        ResourceConfig config = new ResourceConfig()
                .register(JacksonFeature.class)
                .packages(
                    "com.smartcampus.resource",
                    "com.smartcampus.exception",
                    "com.smartcampus.filter"
                );

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);

        LOG.info("=================================================");
        LOG.info(" Smart Campus API started!");
        LOG.info(" Base URL: http://localhost:8080/api/v1/");
        LOG.info(" Press CTRL+C to stop.");
        LOG.info("=================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        Thread.currentThread().join();
    }
}
