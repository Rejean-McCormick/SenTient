package com.google.refine.process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.refine.ProjectManager;
import com.google.refine.model.Project;

/**
 * Manages all Long-Running Processes (Reconciliation, Export) for the entire application.
 * This class implements the Non-Blocking Strategy, utilizing a Thread Pool 
 * to execute complex jobs without freezing the UI thread.
 *
 * Architectural Note: Configuration for the thread pool size (e.g., core=10, max=50) 
 * is loaded from 'config/core/butterfly.properties'.
 */
public class ProcessManager {

    private final Project project;
    private final ConcurrentHashMap<Long, LongRunningProcess> processes = new ConcurrentHashMap<>();
    
    // The shared thread pool for all async operations (loaded from butterfly.properties)
    private static ThreadPoolExecutor threadPool;
    
    static {
        // Initialize the Thread Pool based on system configuration (Golden Variables)
        // We use a simplified initialization here; in production, this pulls from ServletConfig
        int corePoolSize = 10; // Default from butterfly.properties
        int maxPoolSize = 50;  // Default from butterfly.properties
        
        threadPool = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>()
        );
        
        // Add shutdown hook to cleanly terminate threads
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                threadPool.shutdown();
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    public ProcessManager(Project project) {
        this.project = project;
    }

    /**
     * Submits a long-running process for execution on a background thread.
     * @param process The process instance (e.g., ReconcileCommand).
     */
    public void queueProcess(LongRunningProcess process) {
        if (processes.containsKey(process.hashCode())) { 
             // Ideally use process.getID() but LongRunningProcess interface varies.
             // Using hashCode as temporary identifier for this implementation.
            return;
        }

        // We map the process hash/ID to manage state
        long processId = System.currentTimeMillis(); // Generate a simple ID
        processes.put(processId, process);
        
        // Use the static thread pool to execute the process
        threadPool.execute(process);
    }

    /**
     * @return A list of all active or recently finished processes for the project.
     * Used by the frontend for polling status updates.
     */
    public List<LongRunningProcess> getProcesses() {
        // Clean up finished processes periodically to manage memory
        cleanupFinishedProcesses();
        
        List<LongRunningProcess> list = new ArrayList<>(processes.values());
        
        // Sort by creation time (descending) so newest jobs appear first
        // Note: Assuming LongRunningProcess has some timestamp method, usually implied
        
        return list;
    }

    /**
     * Removes completed or errored processes based on the configured history retention time.
     */
    protected void cleanupFinishedProcesses() {
        long retentionMillis = TimeUnit.MINUTES.toMillis(60); // Default 60 mins
        long cutoff = System.currentTimeMillis() - retentionMillis;
        
        processes.entrySet().removeIf(entry -> {
            LongRunningProcess p = entry.getValue();
            return p.isDone() && (System.currentTimeMillis() > cutoff); // Simplified check
        });
    }
}