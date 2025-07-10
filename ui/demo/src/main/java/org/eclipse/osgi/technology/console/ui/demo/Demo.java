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

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;

import com.googlecode.lanterna.gui2.AsynchronousTextGUIThread;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Panels;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
public final class Demo {

    private Terminal terminal;
    private DefaultTerminalFactory terminalFactory;
    private Screen activeScreen;

    void activateScreen(Screen screen) {

        if (activeScreen != null) {
            try {
                activeScreen.stopScreen();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        activeScreen = screen;

        try {
            activeScreen.startScreen();

            MultiWindowTextGUI gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), activeScreen);
            final BasicWindow window = new BasicWindow("a Window");

            Panel mainPanel = new Panel();
            mainPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));

            Panel panel = new Panel();
            panel.addComponent(new Button("Button 1"));
            panel.addComponent(new Button("Button 2"));
            mainPanel.addComponent(panel.withBorder(Borders.singleLine()));

            window.setComponent(
                    Panels.vertical(mainPanel.withBorder(Borders.singleLine("Main")), new Button("OK", window::close)));
            gui.addWindow(window);

            AsynchronousTextGUIThread guiThread = (AsynchronousTextGUIThread) gui.getGUIThread();
            guiThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Activate
    public Demo(BundleContext context) throws IOException, InterruptedException {
        System.out.println("Starting");
        try {
            terminalFactory = new DefaultTerminalFactory();
            terminalFactory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG);
            terminal = terminalFactory.createTerminal();
//          tf.setForceTextTerminal(true);
            terminal = terminalFactory.createTerminal();
            Screen screen = terminalFactory.createScreen();

            activateScreen(screen);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Deactivate
    public void stop() {
        try {

            activeScreen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            terminal.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
