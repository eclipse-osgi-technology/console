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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores commands in a FIFO list with a maximum size. Provides methods to
 * retrieve commands by index, pattern, etc.
 */
final class HistoryStore {

    private static final int DEFAULT_LIMIT = 100;

    private final LinkedList<String> commands = new LinkedList<>();

    /**
     * Appends a new command to the history, removing the oldest entry if limit is
     * exceeded.
     *
     * @param commandLine the command to store
     */
    public void append(CharSequence commandLine) {
        commands.add(commandLine.toString());
        if (commands.size() > DEFAULT_LIMIT) {
            commands.removeFirst();
        }
    }

    /**
     * Returns an immutable copy of the history (oldest first).
     */
    public List<String> getHistory() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * Returns the last (most recent) command or throws if empty.
     */
    public String getLast() {
        if (commands.isEmpty()) {
            throw new IllegalStateException("No commands in history.");
        }
        return commands.getLast();
    }

    /**
     * Retrieves a command by numeric index: - positive n = zero-based index from
     * the start - negative n = relative index from the end
     */
    public String getByIndex(int index) {
        int actualIndex = (index < 0) ? commands.size() + index : index;
        if (actualIndex < 0 || actualIndex >= commands.size()) {
            throw new IllegalArgumentException("!" + index + ": event not found.");
        }
        return commands.get(actualIndex);
    }

    /**
     * Searches backwards for a command containing the given substring.
     *
     * @param part the substring to match
     */
    public String findContaining(String part) {
        for (int i = commands.size() - 1; i >= 0; i--) {
            var cmd = commands.get(i);
            if (cmd.contains(part)) {
                return cmd;
            }
        }
        throw new IllegalArgumentException("No command containing '" + part + "' in history.");
    }

    /**
     * Searches backwards for a command that starts with the given prefix.
     *
     * @param prefix the prefix to match
     */
    public String findStartingWith(String prefix) {
        for (int i = commands.size() - 1; i >= 0; i--) {
            var cmd = commands.get(i);
            if (cmd.startsWith(prefix)) {
                return cmd;
            }
        }
        throw new IllegalArgumentException("No command starting with '" + prefix + "' in history.");
    }
}
