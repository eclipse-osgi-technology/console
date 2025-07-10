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

import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Converter;
import org.apache.felix.service.command.Function;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * The {@code Console} class implements an interactive loop reading from the
 * session's input and writing to its output.
 *
 * <p>
 * Checks for history expansions (lines starting with ! or ^) and executes
 * commands in the underlying Gogo environment.
 * </p>
 */
public final class Console implements Runnable {

    private final CommandSession session;
    private final InputStream in;
    private final PrintStream out;
    private final HistoryService historyService;

    private volatile boolean quit;

    /**
     * Creates a new interactive console bound to the given session and history.
     */
    public Console(CommandSession session, HistoryService historyService) {
        this.session = session;
        this.in = session.getKeyboard();
        this.out = session.getConsole();
        this.historyService = historyService;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && !quit) {
                var line = getLine(getPrompt());
                if (line == null) {
                    // EOF or user quit
                    break;
                }

                var lineToExecute = line;
                try {
                    // Check for history
                    if (!line.isEmpty() && (line.charAt(0) == '!' || line.charAt(0) == '^')) {
                        lineToExecute = historyService.evaluate(line);
                        System.out.println(lineToExecute);
                    }

                    var result = session.execute(lineToExecute);
                    session.put("_", result);

                    if (result != null && !Boolean.FALSE.equals(session.get(".Gogo.format"))) {
                        out.println(session.format(result, Converter.INSPECT));
                    }
                } catch (Throwable e) {
                    handleError(e);
                } finally {
                    // Always append final line to history
                    historyService.append(lineToExecute);
                }
            }
        } catch (Exception e) {
            if (!quit) {
                e.printStackTrace(out);
            }
        }
    }

    /**
     * Determines the prompt text. If the session stores a 'prompt' as a Function,
     * call it to retrieve the prompt; otherwise uses "g! ".
     */
    private String getPrompt() {
        var promptObj = session.get("prompt");
        if (promptObj instanceof Function func) {
            try {
                promptObj = func.execute(session, null);
            } catch (Exception e) {
                out.println(promptObj + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                promptObj = null;
            }
        }
        if (promptObj == null) {
            promptObj = "g! ";
        }
        return promptObj.toString();
    }

    /**
     * Reads a line from {@link #in}, handles basic line editing (newline,
     * backspace).
     *
     * @param prompt the prompt to display
     * @return the line read or null if EOF/quit
     * @throws IOException if reading fails
     */
    private CharSequence getLine(String prompt) throws IOException {
        var sb = new StringBuilder();
        out.print(prompt);

        while (!quit) {
            out.flush();
            int c;
            try {
                c = in.read();
            } catch (IOException e) {
                if ("Stream closed".equals(e.getMessage())) {
                    quit = true;
                } else {
                    throw e;
                }
                c = -1;
            }

            switch (c) {
            case -1, 4 -> // -1 = EOF, 4 = EOT
                quit = true;
            case '\r' -> {
                /* ignore carriage return */ }
            case '\n' -> {
                if (sb.length() > 0) {
                    return sb;
                }
                // If empty, re-display prompt
                out.print(prompt);
            }
            case '\b' -> {
                if (sb.length() > 0) {
                    out.print("\b \b");
                    sb.deleteCharAt(sb.length() - 1);
                }
            }
            default -> {
                if (c >= 0) {
                    sb.append((char) c);
                }
            }
            }
            if (quit) {
                return null;
            }
        }
        return null;
    }

    /**
     * Handles exceptions from the command execution loop.
     */
    private void handleError(Throwable e) {
        final String SESSION_CLOSED = "session is closed";
        if ((e instanceof IllegalStateException) && SESSION_CLOSED.equals(e.getMessage())) {
            out.println("gosh: " + e);
            quit = true;
        }

        if (!quit) {
            session.put("exception", e);
            var loc = session.get(".location");
            if (loc == null || !loc.toString().contains(":")) {
                loc = "gogo";
            }
            out.println(loc + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
