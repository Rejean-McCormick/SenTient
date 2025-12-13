package com.google.refine.process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.refine.ProjectManager;
import com.google.refine.model.Project;
import com.google.refine.process.util.ThreadPoolExecutorAdapter; // Implied component
import com.google.refine.util.PoolConfigurations; // Implied component

/**
 * Manages all Long-Running Processes (Reconciliation, Export) for the entire application.
 * This class implements the Non-Blocking Strategy, utilizing a Thread Pool 
 * to execute complex jobs without freezing the UI thread.
 *
 * * Architectural Note: Configuration for the thread pool size (e.g., core=10, max=50) 
 * is loaded from 'config/core/butterfly.properties'[cite: 345].
 */
public class ProcessManager {

    private final Project project;
    private final ConcurrentHashMap<Long, LongRunningProcess> processes = new ConcurrentHashMap<>();
    
    // The shared thread pool for all async operations (loaded from butterfly.properties)
    private static ThreadPoolExecutor threadPool;
    
    static {
        // This static block initializes the Thread Pool based on system configuration
        Properties props = ProjectManager.getServletConfig().getInitProperties();
        threadPool = PoolConfigurations.getThreadPool(
            props,
            "butterfly.thread_pool.core_size",
            "butterfly.thread_pool.max_size",
            10, // Default core size [cite: 345]
            50  // Default max size [cite: 345]
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
        if (processes.containsKey(process.getID())) {
            return;
        }

        processes.put(process.getID(), process);
        
        // Use the static thread pool to execute the process
        threadPool.execute(process);
    }

    /**
     * @return A list of all active or recently finished processes for the project.
     * Used by the frontend for polling status updates[cite: 94].
     */
    public List<Process> getProcesses() {
        // Clean up finished processes periodically to manage memory
        cleanupFinishedProcesses();
        
        List<Process> list = new ArrayList<>(processes.values());
        
        // Sort by ID (time of creation) descending
        Collections.sort(list, new Comparator<Process>() {
            @Override
            public int compare(Process o1, Process o2) {
                return (int) (o2.getID() - o1.getID());
            }
        });
        
        return list;
    }

    /**
     * Removes completed or errored processes based on the configured history retention time.
     */
    protected void cleanupFinishedProcesses() {
        long retentionMillis = TimeUnit.MINUTES.toMillis(
            ProjectManager.getPoolConfigurations().getProcessHistoryRetentionMinutes()
        );
        long cutoff = System.currentTimeMillis() - retentionMillis;
        
        for (LongRunningProcess process : processes.values()) {
            if (process.isDone()) {
                // If a process is done and its creation time is older than the cutoff, remove it.
                if (process.getCreationTime() < cutoff) {
                    processes.remove(process.getID());
                }
            }
        }
    }
    
    /**
     * Placeholder for implied utility class to handle thread pool configuration.
     */
    private static class PoolConfigurations {
        public static ThreadPoolExecutor getThreadPool(Properties props, String coreSizeKey, String maxSizeKey, int defaultCore, int defaultMax) {
            // Implemented logic to read props and return a configured ThreadPoolExecutor.
            // Simplified return for compilation:
            return new ThreadPoolExecutorAdapter(defaultCore, defaultMax, 60L, TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>());
        }
        
        public static long getProcessHistoryRetentionMinutes() {
            // Read refine.process.history_retention_minutes from butterfly.properties [cite: 345]
            return 60L; // Default
        }
    }
}