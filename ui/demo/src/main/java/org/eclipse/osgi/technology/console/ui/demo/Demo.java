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

import org.jline.jansi.AnsiConsole;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.TextGUI;
import com.googlecode.lanterna.gui2.TextGUI.Listener;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
public final class Demo {

	private com.googlecode.lanterna.terminal.Terminal terminal;

	@Activate
	public Demo(@Reference com.googlecode.lanterna.terminal.Terminal terminal)
			throws InterruptedException, IOException {

		this.terminal = terminal;
		runComprehensiveDemo();
	}

	private void runComprehensiveDemo() throws IOException, InterruptedException {

		Screen screen = new TerminalScreen(terminal);

		screen.startScreen();

		// Add shutdown hook to clean up terminal on Ctrl+C
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				System.err.println("Shutdown hook triggered - cleaning up terminal...");

				// Exit private mode first to disable mouse tracking
				if (terminal != null) {
					try {
						terminal.exitPrivateMode();
						System.err.println("  Private mode exited");
					} catch (Exception e) {
						System.err.println("  Error exiting private mode: " + e.getMessage());
					}
				}

				if (screen != null) {
					screen.clear();
					screen.refresh();
					screen.stopScreen();
				}
				if (terminal != null) {
					terminal.close();
				}
			} catch (IOException e) {
				System.err.println("Error during shutdown cleanup: " + e.getMessage());
			}
		}));

		// Use default same-thread strategy (no separate GUI thread)
		MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

		// IMPORTANT: Disable blocking I/O for responsive UI!
		// Without this, processInput() eventually calls readInput() which blocks
		// for up to ~100ms (JLine's internal timeout), making the UI sluggish.
		// With non-blocking I/O, processInput() uses pollInput(1ms) and returns
		// much faster, making the counter and UI more responsive.
		gui.setBlockingIO(false);

		final BasicWindow window = new BasicWindow("OSGi Console UI Demo - Tab=Navigieren, Enter=Aktivieren");

		Panel mainPanel = new Panel();
		mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

		// Status label to show which action was triggered
		final Label statusLabel = new Label("Status: Bereit. Navigieren Sie mit Tab, aktivieren Sie mit Enter.");

		// Text input debug label - shows typed characters
		final Label textInputLabel = new Label("");

		// Action counter
		final int[] actionCounter = { 0 };

		// Live counter that updates every second
		final Label liveCounterLabel = new Label("Live Counter: 0 Sekunden");

		// DON'T START TIMERS YET - GUI doesn't exist yet!

		// Create an ActionListBox with various actions
		ActionListBox actionListBox = new ActionListBox();
		actionListBox.addItem("Aktion 1: Zähler erhöhen", () -> {
			actionCounter[0]++;
			statusLabel.setText("✓ Aktion 1 ausgelöst! Zähler: " + actionCounter[0]);
		});
		actionListBox.addItem("Aktion 2: Status zurücksetzen", () -> {
			actionCounter[0] = 0;
			statusLabel.setText("✓ Aktion 2 ausgelöst! Zähler zurückgesetzt.");
		});
		actionListBox.addItem("Aktion 3: Test-Nachricht", () -> {
			statusLabel.setText("✓ Aktion 3 ausgelöst! Dies ist eine Test-Nachricht.");
		});

		// Create buttons in a horizontal panel
		Panel buttonPanel = new Panel();
		buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));

		buttonPanel.addComponent(new Button("Info", () -> {
			statusLabel.setText("✓ Info-Button geklickt! Zähler: " + actionCounter[0]);
		}));

		buttonPanel.addComponent(new Button("Reset", () -> {
			actionCounter[0] = 0;
			statusLabel.setText("✓ Reset-Button geklickt! Zähler zurückgesetzt.");
		}));

		buttonPanel.addComponent(new Button("Test", () -> {
			actionCounter[0] += 10;
			statusLabel.setText("✓ Test-Button geklickt! +10 zum Zähler: " + actionCounter[0]);
		}));

		// Position label to show cursor/click position
		final Label positionLabel = new Label("Position: Keine Eingabe erkannt");

		// Arrow key test label
		final Label arrowKeyLabel = new Label("Pfeiltasten: Keine gedrückt");

		// Terminal resize label
		final Label terminalSizeLabel = new Label(String.format("Terminal-Größe: %dx%d",
			screen.getTerminalSize().getColumns(), screen.getTerminalSize().getRows()));

		// Mouse event labels
		final Label mousePositionLabel = new Label("Maus-Position: Keine Bewegung erkannt");
		final Label mouseButtonLabel = new Label("Maus-Button: Kein Klick erkannt");
		final Label mouseActionLabel = new Label("Maus-Aktion: Keine Aktion erkannt");
		final Label mouseEventTypeLabel = new Label("Event-Typ: -");

		// Add all components to main panel
		mainPanel.addComponent(terminalSizeLabel.withBorder(Borders.singleLine("Terminal-Größe")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(liveCounterLabel.withBorder(Borders.singleLine("Live Timer")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(arrowKeyLabel.withBorder(Borders.singleLine("Pfeiltasten Test")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(mousePositionLabel.withBorder(Borders.singleLine("Maus-Position")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(mouseButtonLabel.withBorder(Borders.singleLine("Maus-Button")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(mouseActionLabel.withBorder(Borders.singleLine("Maus-Aktion")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(mouseEventTypeLabel.withBorder(Borders.singleLine("Maus-Event-Typ")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(positionLabel.withBorder(Borders.singleLine("Input Debug")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(textInputLabel.withBorder(Borders.singleLine("Getippte Zeichen")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(statusLabel.withBorder(Borders.singleLine("Status")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(actionListBox.withBorder(Borders.singleLine("Aktionen (Enter drücken)")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(buttonPanel.withBorder(Borders.singleLine("Buttons")));
		mainPanel.addComponent(new EmptySpace(com.googlecode.lanterna.TerminalSize.ONE));
		mainPanel.addComponent(new Separator(Direction.HORIZONTAL));
		mainPanel.addComponent(new Button("Beenden (Exit)", window::close));

		window.setComponent(mainPanel);

		// Add resize listener to track terminal size changes
		terminal.addResizeListener((terminal1, newSize) -> {
			terminalSizeLabel.setText(String.format("Terminal-Größe: %dx%d (neu!)",
				newSize.getColumns(), newSize.getRows()));
			System.err.println("Terminal resized to: " + newSize);
		});

		System.err.println("Demo: About to add window and wait...");

		// Add window and wait until it's closed
		gui.addListener(new Listener() {

			@Override
			public boolean onUnhandledKeyStroke(TextGUI textGUI, KeyStroke keyStroke) {
				// Handle mouse events - but filter out simple MOVE events to reduce noise
				if (keyStroke instanceof com.googlecode.lanterna.input.MouseAction mouseAction) {
					var actionType = mouseAction.getActionType();

					// Only process significant mouse events (not simple moves)
					if (actionType != com.googlecode.lanterna.input.MouseActionType.MOVE) {
						// Update mouse position
						var pos = mouseAction.getPosition();
						mousePositionLabel.setText(String.format("Maus-Position: X=%d, Y=%d",
							pos.getColumn(), pos.getRow()));

						// Update mouse button
						int button = mouseAction.getButton();
						String buttonName = switch (button) {
							case 1 -> "Links";
							case 2 -> "Mitte";
							case 3 -> "Rechts";
							case 4 -> "Scroll-Up";
							case 5 -> "Scroll-Down";
							default -> "Button " + button;
						};
						mouseButtonLabel.setText("Maus-Button: " + buttonName);

						// Update action type
						String actionName = switch (actionType) {
							case CLICK_DOWN -> "CLICK_DOWN (Taste gedrückt)";
							case CLICK_RELEASE -> "CLICK_RELEASE (Taste losgelassen)";
							case DRAG -> "DRAG (Ziehen mit Taste)";
							case MOVE -> "MOVE (Bewegung ohne Taste)";
							case SCROLL_UP -> "SCROLL_UP (Rauf scrollen)";
							case SCROLL_DOWN -> "SCROLL_DOWN (Runter scrollen)";
							default -> actionType.toString();
						};
						mouseActionLabel.setText("Maus-Aktion: " + actionName);

						// Full event description
						mouseEventTypeLabel.setText(String.format("Event-Typ: %s @ (%d,%d) Button=%d",
							actionType, pos.getColumn(), pos.getRow(), button));
					}

					return true; // Event handled
				}

				// Handle regular key presses - display typed characters
				if (keyStroke != null && keyStroke.getKeyType() == KeyType.Character) {
					Character ch = keyStroke.getCharacter();
					if (ch != null) {
						textInputLabel.setText("Getipptes Zeichen: '" + ch + "' (Code: " + (int)ch + ")");
					}
					return false; // Let GUI handle it normally
				}

				// Check if there's an active window before accessing it
				com.googlecode.lanterna.gui2.Window activeWindow = gui.getActiveWindow();
				if (activeWindow != null) {
					TerminalPosition pos = activeWindow.getPosition();
					positionLabel.setText("Position: " + pos.toString());
				}

				// Handle Ctrl+C to exit cleanly
				if (keyStroke != null && keyStroke.isCtrlDown() &&
					keyStroke.getCharacter() != null && keyStroke.getCharacter() == 'c') {
					System.err.println("Ctrl+C detected - closing window...");
					window.close();
					return true;
				}

				// Test arrow keys - these should be recognized now!
				if (keyStroke != null && keyStroke.getKeyType() != null) {
					switch (keyStroke.getKeyType()) {
					case ArrowUp:
						arrowKeyLabel.setText("Pfeiltasten: ↑ Oben gedrückt!");
						return true;
					case ArrowDown:
						arrowKeyLabel.setText("Pfeiltasten: ↓ Unten gedrückt!");
						return true;
					case ArrowLeft:
						arrowKeyLabel.setText("Pfeiltasten: ← Links gedrückt!");
						return true;
					case ArrowRight:
						arrowKeyLabel.setText("Pfeiltasten: → Rechts gedrückt!");
						return true;
					default:
						break;
					}
				}

				return false;
			}
		});

		System.err.println("Demo: Adding window...");
		gui.addWindow(window);

		try {
			while (window.isVisible()) {

				// Process input and update screen
				if (gui.isPendingUpdate()) {
					gui.updateScreen();
				}

				// Process input - with non-blocking I/O this returns immediately
				gui.processInput();

				// Small sleep to avoid busy-waiting
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					System.err.println("Demo interrupted - cleaning up...");
					break;
				}
			}
		} finally {
			// Always clean up, even if interrupted
			System.err.println("Demo: Cleaning up terminal...");

			// Exit private mode first to disable mouse tracking
			try {
				terminal.exitPrivateMode();
				System.err.println("  Private mode exited");
			} catch (Exception e) {
				System.err.println("  Error exiting private mode: " + e.getMessage());
			}

			try {
				screen.clear();
				screen.refresh();
				screen.stopScreen();
			} catch (Exception e) {
				System.err.println("Error during screen cleanup: " + e.getMessage());
			}

			try {
				terminal.close();
			} catch (Exception e) {
				System.err.println("Error during terminal close: " + e.getMessage());
			}
			System.err.println("Demo: Cleanup complete.");
		}

	}

	@Deactivate
	public void stop() {

		// Cleanup Jansi
		AnsiConsole.systemUninstall();

	}
}
