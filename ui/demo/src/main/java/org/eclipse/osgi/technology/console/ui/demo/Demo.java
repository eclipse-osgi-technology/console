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
package org.eclipse.osgi.technology.console.ui.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.jline.jansi.Ansi;
import org.jline.jansi.AnsiConsole;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.jni.JniTerminalProvider;
import org.jline.terminal.spi.SystemStream;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
public final class Demo {

    private Terminal terminal;
    private PrintWriter writer;
    private volatile boolean running = true;

    @Activate
    public Demo(BundleContext context) throws InterruptedException, IOException {
        System.out.println("=== JLine Terminal Demo Starting ===");
        
        // Initialize Jansi for ANSI support
        AnsiConsole.systemInstall();
        
        runComprehensiveDemo();
    }

    private void runComprehensiveDemo() throws IOException, InterruptedException {
        
        
        
        JniTerminalProvider jniProvider = new JniTerminalProvider();
//        jniProvider.current(SystemStream.Input);
        
        
        // Check if JNI native library is available
        Terminal jniTerminal = jniProvider.sysTerminal(
            "TestJNI",                              // name
            "xterm-256color",                       // type  
            false,                                  // ansiPassThrough
            java.nio.charset.StandardCharsets.UTF_8, // encoding
            java.nio.charset.StandardCharsets.UTF_8, // stdinEncoding
            java.nio.charset.StandardCharsets.UTF_8, // stdoutEncoding  
//            java.nio.charset.StandardCharsets.UTF_8, // stderrEncoding
            true,                                   // nativeSignals
            Terminal.SignalHandler.SIG_DFL,         // signalHandler
            false,                                  // paused
            SystemStream.Output                     // systemStream
        );
        
        
        System.out.println("\n1. Testing Terminal Providers...");
        testTerminalProviders();
        
        System.out.println("\n2. Creating Main Terminal...");
        createMainTerminal();
        
        System.out.println("\n3. Testing Terminal Capabilities...");
        testTerminalCapabilities();
        
        System.out.println("\n4. Testing Terminal Attributes...");
        testTerminalAttributes();
        
        System.out.println("\n5. Testing Colors and ANSI Support...");
        testColorsAndAnsi();
        
        System.out.println("\n6. Testing Cursor Operations...");
        testCursorOperations();
        
        System.out.println("\n7. Testing Input/Output...");
        testInputOutput();
        
        System.out.println("\n8. Testing Screen Management...");
        testScreenManagement();
        
        System.out.println("\n9. Testing Signal Handling...");
        testSignalHandling();
        
        System.out.println("\n10. Performance Tests...");
        performanceTests();
        
        System.out.println("\nDemo completed successfully!");
        
        // Keep demo running for interaction
        if (terminal != null && !terminal.getType().equals("dumb")) {
            interactiveMode();
        }
    }

    private void testTerminalProviders() {
        System.out.println("Testing different terminal providers:");
        
        String[] providers = {"system", "jni", "jansi", "exec", "dumb"};
        
        for (String provider : providers) {
            try {
                Terminal testTerminal = TerminalBuilder.builder()
                    .provider(provider)
                    .build();
                    
                System.out.println("  ✓ " + provider + ": " + testTerminal.getType() + 
                                   " (" + testTerminal.getClass().getSimpleName() + ")");
                testTerminal.close();
                
            } catch (Exception e) {
                System.out.println("  ✗ " + provider + ": " + e.getMessage());
            }
        }
    }

    private void createMainTerminal() throws IOException {
        terminal = TerminalBuilder.builder()
            .name("JLineDemo")
            .system(true)
            .jansi(true)
            .color(true)
            .encoding(java.nio.charset.StandardCharsets.UTF_8)
            .nativeSignals(true)
            .signalHandler(this::handleSignal)
            .build();
            
        writer = terminal.writer();
        
        System.out.println("Main terminal created:");
        System.out.println("  Type: " + terminal.getType());
        System.out.println("  Name: " + terminal.getName());
        System.out.println("  Size: " + terminal.getSize());
        System.out.println("  Encoding: " + terminal.encoding());
    }

    private void testTerminalCapabilities() throws IOException {
        System.out.println("Terminal capabilities:");
        
        // Test basic properties
        Size size = terminal.getSize();
        System.out.println("  Terminal size: " + size.getColumns() + "x" + size.getRows());
        System.out.println("  Can resize: " + (size.getColumns() > 0));
        System.out.println("  Terminal type: " + terminal.getType());
        System.out.println("  Terminal name: " + terminal.getName());
        System.out.println("  Encoding: " + terminal.encoding());
        
        // Test basic features by terminal type
        String termType = terminal.getType();
        if (termType.contains("ansi") || termType.contains("xterm") || termType.contains("color")) {
            System.out.println("  Likely supports: Colors, Cursor positioning, ANSI sequences");
        } else if (termType.equals("dumb")) {
            System.out.println("  Limited support: Basic text only, no colors or cursor control");
        } else {
            System.out.println("  Unknown terminal capabilities for type: " + termType);
        }
    }

    private void testTerminalAttributes() throws IOException {
        System.out.println("Testing terminal attributes:");
        
        // Get current attributes
        Attributes attrs = terminal.getAttributes();
        System.out.println("  Current attributes: " + attrs);
        
        // Test canonical mode
        System.out.println("  Canonical mode: " + attrs.getLocalFlag(Attributes.LocalFlag.ICANON));
        System.out.println("  Echo mode: " + attrs.getLocalFlag(Attributes.LocalFlag.ECHO));
        
        // Enter raw mode for better terminal control
        terminal.enterRawMode();
        System.out.println("  Entered raw mode");
        
        // Restore attributes
        terminal.setAttributes(attrs);
        System.out.println("  Restored original attributes");
    }

    private void testColorsAndAnsi() throws IOException {
        System.out.println("Testing colors and ANSI sequences:");
        
        if (writer != null) {
            // Test basic ANSI colors
            writer.println("\n  Basic ANSI Colors:");
            for (Ansi.Color color : Ansi.Color.values()) {
                writer.print(Ansi.ansi().fg(color).a("  " + color.name()).reset().a(" "));
            }
            writer.println();
            
            // Test bright colors
            writer.println("\n  Bright ANSI Colors:");
            for (Ansi.Color color : Ansi.Color.values()) {
                writer.print(Ansi.ansi().fgBright(color).a("  " + color.name()).reset().a(" "));
            }
            writer.println();
            
            // Test background colors
            writer.println("\n  Background Colors:");
            for (Ansi.Color color : Ansi.Color.values()) {
                writer.print(Ansi.ansi().bg(color).a("  " + color.name()).reset().a(" "));
            }
            writer.println();
            
            // Test RGB colors (if supported)
            writer.println("\n  RGB Colors (if supported):");
            writer.print(Ansi.ansi().fgRgb(255, 0, 0).a("  RED"));
            writer.print(Ansi.ansi().fgRgb(0, 255, 0).a("  GREEN"));
            writer.print(Ansi.ansi().fgRgb(0, 0, 255).a("  BLUE"));
            writer.println(Ansi.ansi().reset());
            
            // Test text attributes
            writer.println("\n  Text Attributes:");
            writer.println(Ansi.ansi().bold().a("  Bold text").reset());
            writer.println(Ansi.ansi().a(Ansi.Attribute.ITALIC).a("  Italic text").reset());
            writer.println(Ansi.ansi().a(Ansi.Attribute.UNDERLINE).a("  Underlined text").reset());
            writer.println(Ansi.ansi().a(Ansi.Attribute.STRIKETHROUGH_ON).a("  Strikethrough text").reset());
            
            writer.flush();
        }
    }

    private void testCursorOperations() throws IOException {
        System.out.println("Testing cursor operations:");
        
        if (writer != null && !terminal.getType().equals("dumb")) {
            Size size = terminal.getSize();
            
            // Save cursor position
            writer.print(Ansi.ansi().saveCursorPosition());
            
            // Move cursor to different positions
            writer.print(Ansi.ansi().cursor(5, 10).a("Position (5,10)"));
            writer.print(Ansi.ansi().cursor(10, 5).a("Position (10,5)"));
            writer.print(Ansi.ansi().cursor(15, 15).a("Position (15,15)"));
            
            // Test cursor movement
            writer.print(Ansi.ansi().cursor(20, 1));
            writer.print("Moving right: ");
            for (int i = 0; i < 10; i++) {
                writer.print(Ansi.ansi().cursorRight(1).a("*"));
                writer.flush();
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
            
            writer.print(Ansi.ansi().cursor(21, 1));
            writer.print("Moving down: ");
            for (int i = 0; i < 5; i++) {
                writer.print(Ansi.ansi().cursorDown(1).a("*"));
                writer.flush();
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
            
            // Hide and show cursor using raw ANSI sequences
            writer.print(Ansi.ansi().cursor(22, 1).a("Cursor will be hidden"));
            writer.print("\\u001B[?25l"); // Hide cursor
            writer.flush();
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            
            writer.print("\\u001B[?25h"); // Show cursor
            writer.print(Ansi.ansi().cursor(23, 1).a("Cursor shown again"));
            
            // Restore cursor position
            writer.print(Ansi.ansi().restoreCursorPosition());
            writer.flush();
        }
        
        System.out.println("  Cursor operations completed");
    }

    private void testInputOutput() throws IOException {
        System.out.println("Testing input/output operations:");
        
        if (terminal != null) {
            System.out.println("  Terminal reader available: " + (terminal.reader() != null));
            System.out.println("  Terminal writer available: " + (terminal.writer() != null));
            
            // Test input availability (non-blocking)
            try {
                boolean inputReady = terminal.reader().ready();
                System.out.println("  Input ready: " + inputReady);
            } catch (Exception e) {
                System.out.println("  Input test failed: " + e.getMessage());
            }
            
            // Test output
            if (writer != null) {
                writer.println("  Test output line");
                writer.flush();
                System.out.println("  Output test completed");
            }
        }
    }

    private void testScreenManagement() throws IOException {
        System.out.println("Testing screen management:");
        
        if (writer != null && !terminal.getType().equals("dumb")) {
            // Switch to alternate screen
            writer.print(Ansi.ansi().a("\\u001B[?1049h"));
            writer.flush();
            
            // Clear alternate screen
            writer.print(Ansi.ansi().eraseScreen().cursor(1, 1));
            writer.println("=== ALTERNATE SCREEN ===");
            writer.println("This is displayed on the alternate screen buffer");
            writer.println("Original screen content is preserved");
            writer.flush();
            
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
            
            // Switch back to main screen
            writer.print(Ansi.ansi().a("\\u001B[?1049l"));
            writer.flush();
            
            System.out.println("  Screen switching completed");
        } else {
            System.out.println("  Screen management not available for dumb terminal");
        }
    }

    private void testSignalHandling() {
        System.out.println("Testing signal handling:");
        System.out.println("  Signal handler installed: " + (terminal != null));
        System.out.println("  SIGINT (Ctrl+C) will be handled gracefully");
        System.out.println("  SIGTSTP (Ctrl+Z) suspension support available");
    }

    private void performanceTests() throws IOException {
        System.out.println("Running performance tests:");
        
        if (writer != null) {
            // Test output performance
            long startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                writer.print("*");
            }
            writer.flush();
            long endTime = System.nanoTime();
            
            double milliseconds = (endTime - startTime) / 1_000_000.0;
            System.out.println("  1000 character output: " + String.format("%.2f ms", milliseconds));
            
            // Test color changes performance
            startTime = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                writer.print(Ansi.ansi().fg(Ansi.Color.values()[i % Ansi.Color.values().length]).a("*"));
            }
            writer.print(Ansi.ansi().reset());
            writer.flush();
            endTime = System.nanoTime();
            
            milliseconds = (endTime - startTime) / 1_000_000.0;
            System.out.println("  100 color changes: " + String.format("%.2f ms", milliseconds));
        }
    }

    private void interactiveMode() throws IOException {
        if (writer == null) return;
        
        writer.println("\n" + Ansi.ansi().bold().fg(Ansi.Color.CYAN));
        writer.println("=== INTERACTIVE MODE ===");
        writer.println("Press any key to see key codes (ESC to exit)");
        writer.print(Ansi.ansi().reset());
        writer.flush();
        
        terminal.enterRawMode();
        
        try {
            int ch;
            while (running && (ch = terminal.reader().read()) != -1) {
                if (ch == 27) { // ESC key
                    writer.println("\nExiting interactive mode...");
                    break;
                }
                
                // Display key information
                writer.println(String.format("Key pressed: %d (0x%02X) '%c' at %s", 
                    ch, ch, 
                    (ch >= 32 && ch < 127) ? (char) ch : '?',
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
                ));
                writer.flush();
                
                // Handle special sequences
                if (ch == 3) { // Ctrl+C
                    writer.println("Ctrl+C detected - use ESC to exit");
                    writer.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("Interactive mode error: " + e.getMessage());
        }
        
        writer.print(Ansi.ansi().reset());
        writer.flush();
    }

    private void handleSignal(Terminal.Signal signal) {
        System.out.println("\nSignal received: " + signal);
        
        switch (signal) {
            case INT:
                System.out.println("SIGINT (Ctrl+C) - Graceful shutdown initiated");
                running = false;
                break;
            case TSTP:
                System.out.println("SIGTSTP (Ctrl+Z) - Terminal suspension");
                break;
            case WINCH:
                System.out.println("SIGWINCH - Terminal window size changed");
                if (terminal != null) {
                    System.out.println("New size: " + terminal.getSize());
                }
                break;
            default:
                System.out.println("Other signal: " + signal);
        }
    }

    @Deactivate
    public void stop() {
        System.out.println("=== JLine Terminal Demo Stopping ===");
        running = false;
        
        try {
            if (terminal != null) {
                // Restore terminal to normal state
                if (writer != null) {
                    writer.print(Ansi.ansi().reset().cursor(1, 1));
                    writer.flush();
                }
                terminal.close();
            }
            
            // Cleanup Jansi
            AnsiConsole.systemUninstall();
            
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
        
        System.out.println("Demo cleanup completed");
    }
}