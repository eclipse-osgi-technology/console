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
package org.eclipse.osgi.technology.console.ui.jline;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.jline.jansi.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal.MouseTracking;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.Terminal.SignalHandler;
import org.jline.terminal.spi.TerminalProvider;
import org.jline.utils.InfoCmp.Capability;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.terminal.AbstractTerminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;

@Component(scope = ServiceScope.SINGLETON, service = com.googlecode.lanterna.terminal.Terminal.class)
public class JlineAdapterTerminal extends AbstractTerminal {

	private static final Logger logger = LoggerFactory.getLogger(JlineAdapterTerminal.class);

	private Terminal terminal;
	private final PrintWriter writer;
	private final CopyOnWriteArrayList<TerminalResizeListener> resizeListeners;
	private TerminalPosition cursorPosition;
	private Attributes originalAttributes;
	private MouseTracking currentMouseTracking;

	public JlineAdapterTerminal() throws IOException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(TerminalProvider.class.getClassLoader());
		try {
			terminal = TerminalBuilder.builder().system(true).dumb(false).jansi(true)
					// .exec(true).jna(true).jni(true)
					.ffm(true).build();
		} catch (IOException e) {
			logger.error("Failed to build terminal", e);
		}
		Thread.currentThread().setContextClassLoader(cl);

		this.writer = terminal.writer();
		this.originalAttributes = terminal.getAttributes();
		this.resizeListeners = new CopyOnWriteArrayList<>();
		this.cursorPosition = new TerminalPosition(0, 0);

		logger.debug("JlineAdapterTerminal initialized");

		// Register resize signal handler
		terminal.handle(Signal.WINCH, new SignalHandler() {
			@Override
			public void handle(Signal signal) {
				logger.debug("SIGWINCH received - terminal resized");
				try {
					TerminalSize newSize = getTerminalSize();
					logger.debug("New size: {}x{}", newSize.getColumns(), newSize.getRows());
					// Notify all resize listeners
					for (TerminalResizeListener listener : resizeListeners) {
						listener.onResized(JlineAdapterTerminal.this, newSize);
					}
				} catch (IOException e) {
					logger.debug("Error getting terminal size: {}", e.getMessage());
				}
			}
		});
		logger.debug("Resize signal handler registered");
	}


	private int pollInputCallCount = 0;
	private long lastProcessInputTime = 0;
	private int pollInputPerProcessInput = 0;

	@Override
	public KeyStroke pollInput() throws IOException {
		pollInputCallCount++;
		pollInputPerProcessInput++;

		// Track how many pollInput calls happen per processInput cycle
		long now = System.currentTimeMillis();
		if (now - lastProcessInputTime > 100) {
			// New processInput() cycle
			if (pollInputPerProcessInput > 1) {
				logger.trace("pollInput() called {} times in last processInput cycle", pollInputPerProcessInput);
			}
			pollInputPerProcessInput = 0;
			lastProcessInputTime = now;
		}

		// Log every 100th call to reduce spam
		if (pollInputCallCount % 100 == 0) {
			logger.trace("pollInput() called {} times total", pollInputCallCount);
		}

		// CRITICAL: Use timeout > 0 for non-blocking behavior!
		// JLine's NonBlockingReaderImpl has special handling:
		// - timeout <= 0: does blocking in.read() on caller thread
		// - timeout > 0: uses separate thread for non-blocking read
		// Even 1ms timeout is enough to trigger the non-blocking path.
		int ch = terminal.reader().read(1L);

		if (ch == -2) {
			// Timeout (no input available)
			return null;
		}

		if (ch == -1) {
			logger.debug("pollInput() -> EOF");
			return new KeyStroke(KeyType.EOF);
		}

		// We got input! Parse it
		logger.trace("pollInput() -> got char: {} (0x{})", ch, Integer.toHexString(ch));
		return parseInput(ch);
	}

	@Override
	public KeyStroke readInput() throws IOException {
		logger.debug("readInput() called (blocking mode)");
		// This is called when gui.setBlockingIO(true) is set
		// We use blocking read here since the caller expects to wait for input
		int ch = terminal.reader().read();

		if (ch == -1) {
			logger.debug("-> EOF");
			return null;
		}

		logger.trace("read char: {} (0x{})", ch, Integer.toHexString(ch));
		return parseInput(ch);
	}

	/**
	 * Parse a character code into a KeyStroke
	 */
	private KeyStroke parseInput(int ch) throws IOException {

		// Handle special keys
		if (ch == 27) { // ESC sequence
			logger.trace("ESC sequence detected");

			// Use read with timeout to get the next byte of the escape sequence
			// 50ms timeout is enough for complete sequences but not too long for standalone
			// ESC
			int next = terminal.reader().read(50L);

			if (next == -2 || next == -1) {
				// Timeout or EOF - it's a standalone ESC key
				logger.trace("-> Standalone Escape key (timeout/EOF)");
				return new KeyStroke(KeyType.Escape);
			}

			logger.trace("ESC next: {} (0x{})", next, Integer.toHexString(next));

			if (next == '[') {
				int code = terminal.reader().read(50L);
				if (code == -2 || code == -1) {
					logger.trace("-> Incomplete CSI sequence");
					return null;
				}
				logger.trace("CSI code: {} (0x{})", code, Integer.toHexString(code));

				// Handle standard cursor keys
				switch (code) {
				case 'A':
					logger.trace("-> ArrowUp");
					return new KeyStroke(KeyType.ArrowUp);
				case 'B':
					logger.trace("-> ArrowDown");
					return new KeyStroke(KeyType.ArrowDown);
				case 'C':
					logger.trace("-> ArrowRight");
					return new KeyStroke(KeyType.ArrowRight);
				case 'D':
					logger.trace("-> ArrowLeft");
					return new KeyStroke(KeyType.ArrowLeft);
				case 'H':
					logger.trace("-> Home");
					return new KeyStroke(KeyType.Home);
				case 'F':
					logger.trace("-> End");
					return new KeyStroke(KeyType.End);
				default:
					// Check for SGR mouse events: ESC[<Cb;Cx;Cy(M|m)
					if (code == '<') {
						logger.trace("SGR Mouse event detected");
						return parseMouseEvent();
					}

					// For other sequences (like F-keys etc), consume until letter
					if (Character.isDigit(code) || code == ';' || code == '?') {
						logger.trace("CSI with params, consuming...");
						StringBuilder seq = new StringBuilder();
						seq.append((char) code);
						// CSI sequence with parameters - read until letter
						while (true) {
							int c = terminal.reader().read(50L);
							if (c == -2 || c == -1) {
								// Timeout or EOF - sequence incomplete
								logger.trace("Incomplete sequence: ESC[{}", seq);
								break;
							}
							seq.append((char) c);
							if (Character.isLetter(c) || c == '~') {
								logger.trace("Consumed sequence: ESC[{}", seq);
								break; // Sequence complete
							}
						}
					} else {
						logger.trace("Unknown CSI code: {}", (char) code);
					}
					// Sequence consumed, return null (no keystroke)
					logger.trace("Sequence consumed, returning null");
					return null;
				}
			} else if (next == 'O') {
				// SS3 sequences (some terminals use ESC O for function keys)
				int code = terminal.reader().read(50L);
				if (code == -2 || code == -1) {
					logger.trace("-> Incomplete SS3 sequence");
					return null;
				}
				logger.trace("SS3 code: {} (0x{})", code, Integer.toHexString(code));
				switch (code) {
				case 'P':
					logger.trace("-> F1");
					return new KeyStroke(KeyType.F1);
				case 'Q':
					logger.trace("-> F2");
					return new KeyStroke(KeyType.F2);
				case 'R':
					logger.trace("-> F3");
					return new KeyStroke(KeyType.F3);
				case 'S':
					logger.trace("-> F4");
					return new KeyStroke(KeyType.F4);
				default:
					logger.trace("Unknown SS3, ignoring");
					return null;
				}
			} else {
				// Unknown escape sequence, just consume it
				logger.trace("Unknown ESC sequence, ignoring");
				return null;
			}
		}

		// Handle control characters
		if (ch < 32) {
			switch (ch) {
			case 10: // Line feed (LF)
			case 13: // Carriage return (CR)
				logger.trace("-> Enter");
				return new KeyStroke(KeyType.Enter);
			case 8:
			case 127:
				logger.trace("-> Backspace");
				return new KeyStroke(KeyType.Backspace);
			case 9:
				logger.trace("-> Tab");
				return new KeyStroke(KeyType.Tab);
			default:
				logger.trace("-> Ctrl+{}", (char) (ch + 64));
				return new KeyStroke((char) ch, true, false, false);
			}
		}

		logger.trace("-> Character: '{}'", (char) ch);
		return new KeyStroke((char) ch, false, false, false);
	}

	/**
	 * Parse SGR mouse event: ESC[<Cb;Cx;Cy(M|m)
	 * Cb = button code
	 * Cx = column (1-based)
	 * Cy = row (1-based)
	 * M = button press/motion, m = button release
	 */
	private KeyStroke parseMouseEvent() throws IOException {
		StringBuilder params = new StringBuilder();

		// Read until we get M or m
		while (true) {
			int c = terminal.reader().read(50L);
			if (c == -2 || c == -1) {
				logger.trace("Incomplete mouse sequence");
				return null;
			}

			if (c == 'M' || c == 'm') {
				// Parse the parameters
				String[] parts = params.toString().split(";");
				if (parts.length != 3) {
					logger.trace("Invalid mouse sequence params: {}", params);
					return null;
				}

				try {
					int buttonCode = Integer.parseInt(parts[0]);
					int column = Integer.parseInt(parts[1]) - 1; // Convert to 0-based
					int row = Integer.parseInt(parts[2]) - 1; // Convert to 0-based
					boolean release = (c == 'm');

					MouseActionType actionType = determineMouseActionType(buttonCode, release);
					int button = determineButton(buttonCode);

					logger.trace("Mouse event: button={}, x={}, y={}, action={}",
						button, column, row, actionType);

					return new MouseAction(
						actionType,
						button,
						new TerminalPosition(column, row)
					);

				} catch (NumberFormatException e) {
					logger.trace("Error parsing mouse params: {}", e.getMessage());
					return null;
				}
			} else {
				params.append((char) c);
			}
		}
	}

	/**
	 * Determine MouseActionType from button code and release flag
	 */
	private MouseActionType determineMouseActionType(int buttonCode, boolean release) {
		// Button codes:
		// 0-2: left, middle, right button
		// 32: mouse move with no button
		// 64: scroll up
		// 65: scroll down
		// Add 32 for drag events (button held while moving)

		if (release) {
			return MouseActionType.CLICK_RELEASE;
		}

		// Check for scroll events
		if (buttonCode == 64) {
			return MouseActionType.SCROLL_UP;
		}
		if (buttonCode == 65) {
			return MouseActionType.SCROLL_DOWN;
		}

		// Check for drag (button code >= 32 and < 64 indicates drag)
		if ((buttonCode & 32) != 0 && buttonCode < 64) {
			return MouseActionType.DRAG;
		}

		// Check for move
		if (buttonCode == 35 || buttonCode == 32) {
			return MouseActionType.MOVE;
		}

		// Default to click down
		return MouseActionType.CLICK_DOWN;
	}

	/**
	 * Extract button number from button code
	 */
	private int determineButton(int buttonCode) {
		// Remove drag/move flag (bit 5)
		int btn = buttonCode & ~32;

		// Button mapping:
		// 0 = left (button 1)
		// 1 = middle (button 2)
		// 2 = right (button 3)
		// 64/65 = scroll (button 4/5)

		if (buttonCode == 64 || buttonCode == 65) {
			return buttonCode == 64 ? 4 : 5; // Scroll wheel
		}

		return (btn % 3) + 1;
	}

	@Override
	public void enterPrivateMode() throws IOException {
		logger.debug("enterPrivateMode() called");
		// Switch to alternate screen buffer
		writer.print("\u001B[?1049h");
		writer.flush();
		logger.debug("Alternate screen buffer enabled");

		// Enter raw mode with JLine
		terminal.enterRawMode();
		logger.debug("Raw mode activated");

		// Enable mouse tracking - track button events and drags, but not simple movements
		// MouseTracking.Normal = button clicks and drags only
		// MouseTracking.Any = includes every mouse movement (too noisy)
		currentMouseTracking = MouseTracking.Normal;
		terminal.trackMouse(MouseTracking.Normal);
		logger.debug("Mouse tracking enabled (Normal - buttons and drags only)");

		terminal.flush();
		logger.debug("enterPrivateMode() complete");
	}

	@Override
	public void exitPrivateMode() throws IOException {
		logger.debug("exitPrivateMode() called");

		// Disable mouse tracking FIRST - use explicit escape sequences
		writer.print("\u001B[?1000l"); // Normal mouse tracking off
		writer.print("\u001B[?1002l"); // Button event tracking off
		writer.print("\u001B[?1003l"); // Any event tracking off
		writer.print("\u001B[?1006l"); // SGR extended mouse mode off
		writer.flush();
		logger.debug("Mouse tracking escape sequences sent");

		// Also use JLine's API
		terminal.trackMouse(MouseTracking.Off);
		logger.debug("Mouse tracking disabled via API");

		// Reset all colors and attributes
		resetColorAndSGR();

		// Show cursor
		setCursorVisible(true);

		// Switch back to main screen buffer - this clears the alternate buffer
		writer.print("\u001B[?1049l");
		writer.flush();
		logger.debug("Alternate screen buffer disabled");

		// Clear the main screen
		terminal.puts(Capability.clear_screen);
		terminal.puts(Capability.cursor_home);
		writer.flush();

		// Restore original terminal attributes - this will exit raw mode
		if (originalAttributes != null) {
			try {
				terminal.setAttributes(originalAttributes);
				logger.debug("Terminal attributes restored (exited raw mode)");
			} catch (Exception e) {
				logger.debug("Error restoring attributes: {}", e.getMessage());
			}
		}

		logger.debug("exitPrivateMode() complete");
	}

	@Override
	public void clearScreen() throws IOException {
		writer.print(Ansi.ansi().eraseScreen().cursor(1, 1).toString());
		writer.flush();
		cursorPosition = new TerminalPosition(0, 0);
	}

	@Override
	public void setCursorPosition(int x, int y) throws IOException {
		writer.print(Ansi.ansi().cursor(y + 1, x + 1).toString());
		writer.flush();
		cursorPosition = new TerminalPosition(x, y);
	}

	@Override
	public void setCursorPosition(TerminalPosition position) throws IOException {
		setCursorPosition(position.getColumn(), position.getRow());
	}

	@Override
	public TerminalPosition getCursorPosition() throws IOException {
		return cursorPosition;
	}

	@Override
	public void setCursorVisible(boolean visible) throws IOException {
		if (visible) {
			terminal.puts(Capability.cursor_visible);
		} else {
			terminal.puts(Capability.cursor_invisible);
		}
		writer.flush();
	}

	@Override
	public void putCharacter(char c) throws IOException {
		writer.print(c);
		writer.flush();
		cursorPosition = new TerminalPosition(cursorPosition.getColumn() + 1, cursorPosition.getRow());
	}

	@Override
	public void putString(String string) throws IOException {
		writer.print(string);
		writer.flush();
		cursorPosition = new TerminalPosition(cursorPosition.getColumn() + string.length(), cursorPosition.getRow());
	}

	@Override
	public TextGraphics newTextGraphics() throws IOException {
		return super.newTextGraphics();
	}

	@Override
	public void enableSGR(SGR sgr) throws IOException {
		Ansi ansi = Ansi.ansi();
		switch (sgr) {
		case BOLD:
			ansi.bold();
			break;
		case REVERSE:
			ansi.a(Ansi.Attribute.NEGATIVE_ON);
			break;
		case UNDERLINE:
			ansi.a(Ansi.Attribute.UNDERLINE);
			break;
		case BLINK:
			ansi.a(Ansi.Attribute.BLINK_SLOW);
			break;
		case ITALIC:
			ansi.a(Ansi.Attribute.ITALIC);
			break;
		default:
			break;
		}
		writer.print(ansi.toString());
		writer.flush();
	}

	@Override
	public void disableSGR(SGR sgr) throws IOException {
		Ansi ansi = Ansi.ansi();
		switch (sgr) {
		case BOLD:
			ansi.a(Ansi.Attribute.INTENSITY_BOLD_OFF);
			break;
		case REVERSE:
			ansi.a(Ansi.Attribute.NEGATIVE_OFF);
			break;
		case UNDERLINE:
			ansi.a(Ansi.Attribute.UNDERLINE_OFF);
			break;
		case BLINK:
			ansi.a(Ansi.Attribute.BLINK_OFF);
			break;
		case ITALIC:
			ansi.a(Ansi.Attribute.ITALIC_OFF);
			break;
		default:
			break;
		}
		writer.print(ansi.toString());
		writer.flush();
	}

	@Override
	public void resetColorAndSGR() throws IOException {
		writer.print(Ansi.ansi().reset().toString());
		writer.flush();
	}

	@Override
	public void setForegroundColor(TextColor color) throws IOException {
		Ansi ansi = Ansi.ansi();
		if (color instanceof TextColor.ANSI) {
			TextColor.ANSI ansiColor = (TextColor.ANSI) color;
			if (ansiColor == TextColor.ANSI.BLACK)
				ansi.fg(Ansi.Color.BLACK);
			else if (ansiColor == TextColor.ANSI.RED)
				ansi.fg(Ansi.Color.RED);
			else if (ansiColor == TextColor.ANSI.GREEN)
				ansi.fg(Ansi.Color.GREEN);
			else if (ansiColor == TextColor.ANSI.YELLOW)
				ansi.fg(Ansi.Color.YELLOW);
			else if (ansiColor == TextColor.ANSI.BLUE)
				ansi.fg(Ansi.Color.BLUE);
			else if (ansiColor == TextColor.ANSI.MAGENTA)
				ansi.fg(Ansi.Color.MAGENTA);
			else if (ansiColor == TextColor.ANSI.CYAN)
				ansi.fg(Ansi.Color.CYAN);
			else if (ansiColor == TextColor.ANSI.WHITE)
				ansi.fg(Ansi.Color.WHITE);
		} else if (color instanceof TextColor.RGB) {
			TextColor.RGB rgb = (TextColor.RGB) color;
			ansi.fgRgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
		}
		writer.print(ansi.toString());
		writer.flush();
	}

	@Override
	public void setBackgroundColor(TextColor color) throws IOException {
		Ansi ansi = Ansi.ansi();
		if (color instanceof TextColor.ANSI) {
			TextColor.ANSI ansiColor = (TextColor.ANSI) color;
			if (ansiColor == TextColor.ANSI.BLACK)
				ansi.bg(Ansi.Color.BLACK);
			else if (ansiColor == TextColor.ANSI.RED)
				ansi.bg(Ansi.Color.RED);
			else if (ansiColor == TextColor.ANSI.GREEN)
				ansi.bg(Ansi.Color.GREEN);
			else if (ansiColor == TextColor.ANSI.YELLOW)
				ansi.bg(Ansi.Color.YELLOW);
			else if (ansiColor == TextColor.ANSI.BLUE)
				ansi.bg(Ansi.Color.BLUE);
			else if (ansiColor == TextColor.ANSI.MAGENTA)
				ansi.bg(Ansi.Color.MAGENTA);
			else if (ansiColor == TextColor.ANSI.CYAN)
				ansi.bg(Ansi.Color.CYAN);
			else if (ansiColor == TextColor.ANSI.WHITE)
				ansi.bg(Ansi.Color.WHITE);
		} else if (color instanceof TextColor.RGB) {
			TextColor.RGB rgb = (TextColor.RGB) color;
			ansi.bgRgb(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
		}
		writer.print(ansi.toString());
		writer.flush();
	}

	@Override
	public void addResizeListener(TerminalResizeListener listener) {
		resizeListeners.add(listener);
	}

	@Override
	public void removeResizeListener(TerminalResizeListener listener) {
		resizeListeners.remove(listener);
	}

	@Override
	public TerminalSize getTerminalSize() throws IOException {
		org.jline.terminal.Size size = terminal.getSize();
		// Fallback to reasonable defaults if size is 0x0 (dumb terminal)
		int columns = size.getColumns() > 0 ? size.getColumns() : 80;
		int rows = size.getRows() > 0 ? size.getRows() : 25;
		return new TerminalSize(columns, rows);
	}

	@Override
	public byte[] enquireTerminal(int timeout, TimeUnit timeoutUnit) throws IOException {
		// Simple implementation - return basic terminal identification
		return "JlineAnsiTerminal".getBytes();
	}

	@Override
	public void bell() throws IOException {
		terminal.puts(Capability.bell);
		writer.flush();
	}

	@Override
	public void flush() throws IOException {
		writer.flush();
	}

	@Override
	public void close() throws IOException {
		logger.debug("close() called");
		if (terminal != null) {
			// Disable mouse tracking FIRST - use explicit escape sequences
			writer.print("\u001B[?1000l"); // Normal mouse tracking off
			writer.print("\u001B[?1002l"); // Button event tracking off
			writer.print("\u001B[?1003l"); // Any event tracking off
			writer.print("\u001B[?1006l"); // SGR extended mouse mode off
			writer.flush();
			terminal.trackMouse(MouseTracking.Off);
			logger.debug("Mouse tracking disabled");

			// Switch back to main screen buffer if we were in alternate
			writer.print("\u001B[?1049l");
			writer.flush();
			logger.debug("Alternate screen buffer disabled");

			// Clear the screen one final time using JLine's capability API
			terminal.puts(Capability.clear_screen);
			terminal.puts(Capability.cursor_home);
			terminal.puts(Capability.exit_attribute_mode); // Reset all attributes
			writer.flush();
			logger.debug("Screen cleared");

			// Restore original terminal attributes
			if (originalAttributes != null) {
				try {
					terminal.setAttributes(originalAttributes);
					logger.debug("Terminal attributes restored");
				} catch (Exception e) {
					logger.debug("Error restoring attributes: {}", e.getMessage());
				}
			}

			// Send a final flush to make sure everything is sent
			writer.flush();

			terminal.close();
			logger.debug("Terminal closed");
		}
		logger.debug("close() complete");
	}

	/**
	 * Clear screen and reset terminal using JLine's capability API
	 */
	public void clearAndResetTerminal() {
		try {
			terminal.puts(Capability.clear_screen);
			terminal.puts(Capability.cursor_home);
			terminal.puts(Capability.exit_attribute_mode);
			writer.flush();
		} catch (Exception e) {
			logger.debug("Error clearing terminal: {}", e.getMessage());
		}
	}

}