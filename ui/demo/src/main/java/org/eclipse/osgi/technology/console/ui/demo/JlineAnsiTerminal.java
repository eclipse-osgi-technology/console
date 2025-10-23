package org.eclipse.osgi.technology.console.ui.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.jline.jansi.Ansi;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.jni.JniTerminalProvider;
import org.jline.terminal.spi.SystemStream;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.AbstractTerminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;

public class JlineAnsiTerminal extends AbstractTerminal {

    private final Terminal jlineTerminal;
    private final PrintWriter writer;
    private final CopyOnWriteArrayList<TerminalResizeListener> resizeListeners;
    private TerminalPosition cursorPosition;

    public JlineAnsiTerminal() throws IOException {
        
        
        try {
            
            JniTerminalProvider jniProvider = new JniTerminalProvider();
//            jniProvider.current(SystemStream.Input);
            
            
            // Check if JNI native library is available
            Terminal jniTerminal = jniProvider.sysTerminal(
                "TestJNI",                              // name
                "xterm-256color",                       // type  
                false,                                  // ansiPassThrough
                java.nio.charset.StandardCharsets.UTF_8, // encoding
                java.nio.charset.StandardCharsets.UTF_8, // stdinEncoding
                java.nio.charset.StandardCharsets.UTF_8, // stdoutEncoding  
//                java.nio.charset.StandardCharsets.UTF_8, // stderrEncoding
                true,                                   // nativeSignals
                Terminal.SignalHandler.SIG_DFL,         // signalHandler
                false,                                  // paused
                SystemStream.Input                     // systemStream
            );
            System.out.println("JNI terminal created successfully");
            jniTerminal.close();
        } catch (Throwable t) {
            System.err.println("Error with JNI terminal: " + t.getMessage());
        }

        // For Java 22+
        try {
            // Check if FFM is available
            Terminal ffmTerminal = TerminalBuilder.builder()
                    .provider("ffm")
                    .build();
            System.out.println("FFM terminal created successfully");
            ffmTerminal.close();
        } catch (Throwable t) {
            System.err.println("Error with FFM terminal: " + t.getMessage());
        }
        
        this.jlineTerminal = TerminalBuilder.builder()
            .name("JlineAnsiTerminal")
            .system(true)
            .provider("jni")
            .jansi(true)                    // Force Jansi usage
            .color(true)                    // Enable colors
            .encoding(java.nio.charset.StandardCharsets.UTF_8)
            .nativeSignals(true)
            .signalHandler(Terminal.SignalHandler.SIG_IGN)
//            .size(new org.jline.terminal.Size(80, 25))  // Set default size for dumb terminals
            .build();
        this.writer = jlineTerminal.writer();

        // Check if we have a dumb terminal and log info
        System.out.println("JLine Terminal Info:");
        System.out.println("  Type: " + jlineTerminal.getType());
        System.out.println("  Size: " + jlineTerminal.getSize());
        System.out.println("  Encoding: " + jlineTerminal.encoding());
        System.out.println("  Name: " + jlineTerminal.getName());
        
        
        clearScreen();
        // Initialize terminal in normal mode
        jlineTerminal.resume();
        this.resizeListeners = new CopyOnWriteArrayList<>();
        this.cursorPosition = new TerminalPosition(0, 0);
        
        setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        setCursorPosition(3, 4);
    }

    @Override
    public KeyStroke pollInput() throws IOException {
        if (jlineTerminal.reader().ready()) {
            return readInput();
        }
        return null;
    }

    @Override
    public KeyStroke readInput() throws IOException {
        int ch = jlineTerminal.reader().read();

        System.err.println("Read character: " + ch);
        if (ch == -1) {
            return null;
        }

        // Handle special keys
        if (ch == 27) { // ESC sequence
            if (jlineTerminal.reader().ready()) {
                int next = jlineTerminal.reader().read();
                if (next == '[') {
                    int code = jlineTerminal.reader().read();
                    switch (code) {
                    case 'A':
                        return new KeyStroke(KeyType.ArrowUp);
                    case 'B':
                        return new KeyStroke(KeyType.ArrowDown);
                    case 'C':
                        return new KeyStroke(KeyType.ArrowRight);
                    case 'D':
                        return new KeyStroke(KeyType.ArrowLeft);
                    }
                }
            }
            return new KeyStroke(KeyType.Escape);
        }

        // Handle control characters
        if (ch < 32) {
            switch (ch) {
            case 13:
                return new KeyStroke(KeyType.Enter);
            case 8:
            case 127:
                return new KeyStroke(KeyType.Backspace);
            case 9:
                return new KeyStroke(KeyType.Tab);
            default:
                return new KeyStroke((char) ch, true, false, false);
            }
        }

        return new KeyStroke((char) ch, false, false, false);
    }

    @Override
    public void enterPrivateMode() throws IOException {
        jlineTerminal.enterRawMode();
        writer.print(Ansi.ansi().a("\\u001B[?1049h").toString()); // Switch to alternate screen
        writer.flush();
    }

    @Override
    public void exitPrivateMode() throws IOException {
        writer.print(Ansi.ansi().a("\\u001B[?1049l").toString()); // Switch back to main screen
        writer.flush();
        // Reset terminal to normal mode
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
            writer.print(Ansi.ansi().a("\\u001B[?25h").toString());
        } else {
            writer.print(Ansi.ansi().a("\\u001B[?25l").toString());
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
            ansi.a("\\u001B[7m");
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
            ansi.a("\\u001B[27m");
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
        org.jline.terminal.Size size = jlineTerminal.getSize();
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
        writer.print("\\u0007");
        writer.flush();
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (jlineTerminal != null) {
            jlineTerminal.close();
        }
    }

}