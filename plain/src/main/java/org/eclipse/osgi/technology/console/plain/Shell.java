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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.osgi.framework.BundleContext;

/**
 * The {@code Shell} class provides a "gosh" command for Gogo, plus a "history"
 * method, in an OSGi environment.
 */
public final class Shell {

    /**
     * The Gogo function names. Used for CommandProcessor.COMMAND_FUNCTION.
     */
    static final String[] functions = { "gosh" };

    private final CommandProcessor processor;
    private final HistoryService historyService;

    private BundleContext context;

    /**
     * Constructs a new Shell.
     *
     * @param context   the OSGi BundleContext
     * @param processor the CommandProcessor
     */
    public Shell(BundleContext context, CommandProcessor processor) {
        this.context = context;
        this.processor = processor;
        this.historyService = new HistoryService();
        motd(context);
    }

    private String motd(BundleContext context) {
        try {
        Enumeration<URL> urls = context.getBundle().getResources("motd");
            if (urls != null && urls.hasMoreElements()) {
                return new String(urls.nextElement().openStream().readAllBytes());
            }
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * Gogo shell command entrypoint. If "login" is present in argv, reuse the
     * session; otherwise create a new one.
     */
    public Object gosh(final CommandSession session, String[] argv) throws Exception {
        boolean login = Stream.of(argv).anyMatch("login"::equals);

        var newSession = login ? session
                : processor.createSession(session.getKeyboard(), session.getConsole(), System.err);
        newSession.getConsole().print(System.lineSeparator());

        String motd = motd(context);
        if (motd != null) {
            newSession.getConsole().print(motd);
            newSession.getConsole().print(System.lineSeparator());
        }
        return console(newSession);
    }

    private Object console(CommandSession session) {
        var console = new Console(session, historyService);
        console.run();
        return null;
    }

    /**
     * Returns formatted history lines with indices.
     */
    public String[] history() {
        List<String> all = historyService.getHistory();
        var lines = new ArrayList<String>(all.size());
        int i = 1;
        for (String cmd : all) {
            lines.add(String.format("%5d  %s", i++, cmd));
        }
        return lines.toArray(new String[0]);
    }
}
