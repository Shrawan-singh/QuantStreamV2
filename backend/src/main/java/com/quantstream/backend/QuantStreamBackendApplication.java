/*
 * ==================================================================================
 * FILE: QuantStreamBackendApplication.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * This is the ENTRY POINT of the entire backend application — think of it as the
 * "power button" that starts up the whole QuantStream server.
 *
 * When you run "mvn spring-boot:run" or "java -jar backend.jar", Java looks for
 * this file's main() method and starts everything from here.
 *
 * HOW IT WORKS:
 * Spring Boot is a framework (a pre-built toolkit) that handles a LOT of complex
 * stuff for you automatically — setting up the web server, connecting to the
 * database, wiring all the pieces together, etc. This file just tells Spring Boot
 * to "scan" the entire project and set everything up.
 *
 * ANALOGY:
 * Think of this as turning the key in a car's ignition — it doesn't drive the car
 * itself, but it starts the engine, turns on the dashboard, and gets everything
 * running so the car is ready to go.
 * ==================================================================================
 */

package com.quantstream.backend;

// These are "imports" — bringing in tools from the Spring Boot toolkit
// Think of imports like grabbing specific tools from a toolbox before starting work
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/*
 * @SpringBootApplication — This is a special label (called an "annotation") that tells
 * Spring Boot: "Hey, THIS is the main class. Scan all the files in this project,
 * automatically configure the web server, database connections, Kafka messaging,
 * and everything else."
 *
 * It's basically three annotations combined into one:
 *   1. @Configuration      → "This class contains setup instructions"
 *   2. @EnableAutoConfiguration → "Automatically configure everything based on what
 *                                  libraries are in the project"
 *   3. @ComponentScan      → "Scan all the folders and find all the classes that need
 *                            to be managed (controllers, services, etc.)"
 *
 * @ConfigurationPropertiesScan — Tells Spring to automatically find and load
 * configuration settings from application.yml (like database URLs, API keys, etc.)
 * and map them into Java objects so we can use them easily in our code.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class QuantStreamBackendApplication {

    /*
     * main() is the universal starting point for any Java program.
     * SpringApplication.run() does all the heavy lifting:
     *   1. Creates a web server (Tomcat) on port 8080
     *   2. Connects to PostgreSQL database
     *   3. Sets up Kafka messaging (or in-process queue for local mode)
     *   4. Initializes all controllers, services, and the market data streamer
     *   5. Starts the WebSocket server for real-time updates to the frontend
     */
    public static void main(String[] args) {
        SpringApplication.run(QuantStreamBackendApplication.class, args);
    }
}
