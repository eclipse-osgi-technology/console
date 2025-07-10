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

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and evaluates shell-style history expansions (e.g. !, ?-search,
 * ^subst).
 */
final class HistoryEvaluator {

    private final HistoryStore store;

    HistoryEvaluator(HistoryStore store) {
        this.store = store;
    }

    /**
     * Evaluates a command line for history expansions:
     * <ul>
     * <li>!! (last command)</li>
     * <li>!n or !-n (command by index)</li>
     * <li>!string or !?string?</li>
     * <li>^old^new (substitution on last command)</li>
     * <li>:s/pattern/replacement (substitution on retrieved command)</li>
     * </ul>
     *
     * @param input the raw input line
     * @return the expanded result
     */
    public String evaluate(CharSequence input) {
        var it = new StringCharacterIterator(input.toString());
        char first = it.current();
        String event;

        if (first == '!') {
            event = parseExclamation(it);
        } else if (first == '^') {
            event = substitute(it, '^', false, store.getLast());
        } else {
            throw new IllegalArgumentException("Unsupported history expansion: " + input);
        }

        // Check for :s or :gs etc.
        if (it.current() == ':') {
            char next = it.next();
            boolean global = (next == 'a' || next == 'g');
            if (global) {
                next = it.next();
            }
            if (next == 's') {
                event = substitute(it, it.next(), global, event);
            }
        }
        return event;
    }

    private String parseExclamation(CharacterIterator it) {
        char c = it.next();
        if (c == '!') {
            it.next(); // consume second '!'
            return store.getLast();
        } else if (c == '?') {
            // !?substring?
            var substring = readUntil(it, '?');
            it.next(); // consume '?'
            return store.findContaining(substring);
        } else if (Character.isDigit(c) || c == '-') {
            // !n or !-n
            return store.getByIndex(parseNumber(it, c));
        } else {
            // !prefix...
            it.previous();
            var prefix = readUntil(it, ':'); // read up to ':' or end
            return store.findStartingWith(prefix);
        }
    }

    private int parseNumber(CharacterIterator it, char firstChar) {
        var sb = new StringBuilder().append(firstChar);
        for (char c = it.next(); Character.isDigit(c); c = it.next()) {
            sb.append(c);
        }
        return Integer.parseInt(sb.toString());
    }

    private String substitute(CharacterIterator it, char delimiter, boolean global, String source) {
        var pattern = readUntil(it, delimiter);
        var replacement = readUntil(it, delimiter);

        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Empty pattern for substitution.");
        }

        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(source);
        var sb = new StringBuffer();

        if (!matcher.find()) {
            throw new IllegalArgumentException("Pattern not found in: " + source);
        }
        do {
            matcher.appendReplacement(sb, replacement);
        } while (global && matcher.find());
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String readUntil(CharacterIterator it, char delimiter) {
        var sb = new StringBuilder();
        for (char c = it.next(); c != CharacterIterator.DONE && c != delimiter; c = it.next()) {
            if (c == '\\') {
                c = it.next(); // consume escape
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
