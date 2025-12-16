package com.google.refine;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.commands.Command;
import com.google.refine.process.ProcessManager;
import com.google.refine.storage.DuckDBStore;

/**
 * The Main Entry Point (Layer 3 Gateway).
 * Role:
 * 1. Bootstraps the SenTient components (Orchestrator, DuckDB, ProcessManager).
 * 2. Routes HTTP requests (Commands) to the appropriate handlers.
 * 3. Enforces CORS and Security policies defined in 'butterfly.properties'.
 */
public class RefineServlet extends HttpServlet {

    private static final long serialVersionUID = 20251213L;
    private static final Logger logger = LoggerFactory.getLogger("RefineServlet");

    // =========================================================================
    // Global Singletons (The Hybrid Architecture Stack)
    // =========================================================================
    
    // The Sidecar DB for offloading heavy vectors
    private static DuckDBStore sidecarStore;
    
    // The "Brain" that manages Solr/Falcon communication
    private static SenTientOrchestrator orchestrator;
    
    // The standard Project Manager
    private static ProjectManager projectManager;
    
    // The Command Registry (Mapping URLs to Classes)
    private final ConcurrentMap<String, Command> commands = new ConcurrentHashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        logger.info("Bootstrapping SenTient Core...");

        try {
            // 1. Load Configuration
            // 'butterfly.properties' is loaded by the container/servlet context usually,
            // but here we ensure access to specific keys for the Orchestrator.
            Properties butterflyProps = (Properties) config.getServletContext().getAttribute("butterfly.properties");
            if (butterflyProps == null) {
                // Fallback for standalone testing or if loaded externally
                butterflyProps = new Properties(); 
                // In production, this would act as a fallback or reload
            }

            // 2. Initialize Project Manager (Legacy Refine)
            projectManager = new ProjectManager(); 
            // In a real implementation, ProjectManager.initialize() handles workspace loading.

            // 3. Initialize Sidecar Storage (DuckDB)
            // Path derived from environment.json or defaults to data/workspace/sidecar_db
            String storagePath = "./data/workspace/sidecar_db"; 
            logger.info("Initializing DuckDB Sidecar at: " + storagePath);
            sidecarStore = new DuckDBStore(storagePath);
            sidecarStore.init(); // Creates schema and enables WAL mode

            // 4. Initialize The Core Orchestrator (Layer 3 Logic)
            // Wires the config and storage into the main logic engine
            logger.info("Initializing SenTient Orchestrator...");
            orchestrator = new SenTientOrchestrator(butterflyProps, sidecarStore);

            // 5. Register Core Commands
            // API: POST /command/core/reconcile -> ReconcileCommand
            // API: POST /command/core/get-rows  -> GetRowsCommand
            // Note: Actual implementations of Command classes would be in com.google.refine.commands.*
            // registerCommand("reconcile", new com.google.refine.commands.recon.ReconcileCommand());
            // registerCommand("get-rows", new com.google.refine.commands.row.GetRowsCommand());
            
            logger.info("SenTient Core Bootstrapped Successfully.");

        } catch (Exception e) {
            logger.error("FATAL: Failed to initialize SenTient Core", e);
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
        // Graceful Shutdown
        if (sidecarStore != null) {
            sidecarStore.close(); 
        }
        super.destroy();
    }

    // =========================================================================
    // HTTP Routing (The Command Pattern)
    // =========================================================================

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // 1. CORS & Security Headers
        // Allow the React Frontend (127.0.0.1:3000) to communicate per System Alignment
        String origin = request.getHeader("Origin");
        if ("http://127.0.0.1:3000".equals(origin) || "http://localhost:3000".equals(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // 2. Resolve Command
        // Path format: /command/{module}/{action}
        // Example: /command/core/reconcile
        String path = request.getPathInfo();
        if (path == null) path = "/";
        
        // Strip leading slash
        if (path.startsWith("/")) path = path.substring(1);
        
        // Basic routing logic
        Command command = commands.get(path);

        // 3. Execute Command
        if (command != null) {
            try {
                command.doPost(request, response);
            } catch (Exception e) {
                logger.error("Command execution failed: " + path, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Command not found: " + path);
        }
    }

    /**
     * Registers a command handling class to a specific path.
     */
    public void registerCommand(String name, Command command) {
        // Map "core/name" to the command
        commands.put("core/" + name, command);
    }

    // =========================================================================
    // Static Accessors (Dependency Injection for Commands)
    // =========================================================================

    public static SenTientOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public static DuckDBStore getSidecarStore() {
        return sidecarStore;
    }
    
    public static ProjectManager getProjectManager() {
        return projectManager;
    }
}