package com.smartcampus.application;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
    // Jersey uses Main.java + ResourceConfig for the embedded server
    // This class satisfies the JAX-RS Application requirement
}