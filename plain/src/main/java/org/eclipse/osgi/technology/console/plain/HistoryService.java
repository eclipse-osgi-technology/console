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

import java.util.List;

/**
 * A high-level API for managing command history and evaluating shell-style
 * history expansions.
 */
public final class HistoryService {

    private final HistoryStore store;
    private final HistoryEvaluator evaluator;

    /**
     * Creates a new HistoryService with default settings.
     */
    public HistoryService() {
        this.store = new HistoryStore();
        this.evaluator = new HistoryEvaluator(store);
    }

    /**
     * Appends a command to the history.
     *
     * @param command the command line to store
     */
    public void append(CharSequence command) {
        store.append(command);
    }

    /**
     * Evaluates an input line for history expansions (e.g. !, !!, !-1,
     * ^pattern^replacement, etc.).
     *
     * @param input the input line
     * @return the expanded string
     * @throws IllegalArgumentException if expansion fails
     */
    public String evaluate(CharSequence input) {
        return evaluator.evaluate(input);
    }

    /**
     * Returns an immutable copy of the entire stored history, in chronological
     * order (oldest first).
     *
     * @return the command history
     */
    public List<String> getHistory() {
        return store.getHistory();
    }
}
