/**
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Stefan Bischof - initial
 */
package org.eclipse.osgi.technology.console.plain;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Activator that registers the {@link Shell} service and starts the Gogo shell
 * in a background thread.
 */
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public final class Activator implements BundleActivator {

    private BundleContext context;
    private ServiceTracker<CommandProcessor, CommandProcessor> commandProcessorTracker;
    private final Set<ServiceRegistration<?>> regs = new HashSet<>();

    private ExecutorService executor;
    private StartShellJob shellJob;

    @Override
    public void start(BundleContext context) {
        this.context = context;
        this.commandProcessorTracker = createCommandProcessorTracker();
        this.commandProcessorTracker.open();
    }

    @Override
    public void stop(BundleContext context) {
        // Unregister any registered services
        Set<ServiceRegistration<?>> currentRegs;
        synchronized (regs) {
            currentRegs = new HashSet<>(regs);
            regs.clear();
        }
        currentRegs.forEach(ServiceRegistration::unregister);

        // Close the tracker
        if (commandProcessorTracker != null) {
            commandProcessorTracker.close();
        }

        // Stop the shell
        stopShell();
    }

    private ServiceTracker<CommandProcessor, CommandProcessor> createCommandProcessorTracker() {
        return new ServiceTracker<>(context, CommandProcessor.class, null) {
            @Override
            public CommandProcessor addingService(ServiceReference<CommandProcessor> reference) {
                var processor = super.addingService(reference);
                startShell(context, processor);
                return processor;
            }

            @Override
            public void removedService(ServiceReference<CommandProcessor> reference, CommandProcessor service) {
                stopShell();
                super.removedService(reference, service);
            }
        };
    }

    /**
     * Registers the {@link Shell} as an OSGi service, then starts a background
     * thread for the shell.
     */
    private void startShell(BundleContext context, CommandProcessor processor) {
        var dict = new Hashtable<String, Object>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "gogo");
        dict.put(CommandProcessor.COMMAND_FUNCTION, Shell.functions);

        var shell = new Shell(context, processor);
        var reg = context.registerService(Shell.class.getName(), shell, dict);

        synchronized (regs) {
            regs.add(reg);
        }

        // Start a single-thread executor for the shell job
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Shell"));
        shellJob = new StartShellJob(context, processor);
        executor.submit(shellJob);
    }

    /**
     * Shuts down the background shell thread if running.
     */
    private void stopShell() {
        if (executor != null && !executor.isShutdown()) {
            if (shellJob != null) {
                shellJob.terminate();
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("!!! FAILED TO STOP EXECUTOR !!!");
                    // For debugging: list all stack traces
                    Thread.getAllStackTraces().forEach((thread, trace) -> System.err.printf("Thread: %s (%s): %s%n",
                            thread.getName(), thread.getState(), Arrays.toString(trace)));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    /**
     * Internal job that sets up a Gogo session and runs "gosh --login".
     */
    private static final class StartShellJob implements Runnable {
        private static final Logger logger = LoggerFactory.getLogger(StartShellJob.class);

        private final BundleContext context;
        private final CommandProcessor processor;
        private volatile CommandSession session;
        private volatile Thread shellThread;

        StartShellJob(BundleContext context, CommandProcessor processor) {
            this.context = context;
            this.processor = processor;
        }

        @Override
        public void run() {
            shellThread = Thread.currentThread();
            session = processor.createSession(new FileInputStream(FileDescriptor.in),
                    new FileOutputStream(FileDescriptor.out), new FileOutputStream(FileDescriptor.err));

            try {
                var args = context.getProperty("gosh.args");
                if (args == null) {
                    args = "";
                }
                session.execute("gosh --login " + args);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

            } catch (Exception e) {
                logger.error("Error executing gosh shell", e);
            } finally {
                terminate();
            }
        }

        void terminate() {
            if (session != null) {
                session.close();
                session = null;
            }
            if (shellThread != null) {
                shellThread.interrupt();
            }
        }
    }
}
