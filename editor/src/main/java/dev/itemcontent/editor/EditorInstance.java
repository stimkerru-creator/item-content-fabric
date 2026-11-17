package dev.itemcontent.editor;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One interactive editor per OS user, shared across installations and versions. */
final class EditorInstance implements AutoCloseable {
    private static final Map<Path, EditorInstance> LOCAL = new ConcurrentHashMap<>();
    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private final Path endpoint;
    private final String token = UUID.randomUUID().toString();
    private final List<String[]> pending = new ArrayList<>();
    private Consumer<String[]> handler;
    private boolean closed;

    static Path directory() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(System.getProperty("user.home"), ".cache");
        return base.resolve("ItemContent").resolve("editor-instance");
    }

    /** Returns null after the existing editor acknowledges the request. */
    static synchronized EditorInstance open(Path directory, String[] arguments) throws IOException {
        if (arguments.length > 32) throw new IOException("Слишком много параметров запуска редактора.");
        Files.createDirectories(directory);
        directory = directory.toRealPath();
        Path endpoint = directory.resolve("endpoint");
        if (LOCAL.containsKey(endpoint)) { forward(endpoint, arguments); return null; }
        FileChannel channel = FileChannel.open(directory.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            IOException last = null;
            for (int attempt = 0; attempt < 30; attempt++) {
                FileLock lock;
                try { lock = channel.tryLock(); }
                catch (OverlappingFileLockException busy) { lock = null; }
                if (lock != null) {
                    try {
                        var owner = new EditorInstance(channel, lock, endpoint);
                        LOCAL.put(endpoint, owner); return owner;
                    } catch (IOException failure) { lock.release(); throw failure; }
                }
                try { forward(endpoint, arguments); channel.close(); return null; }
                catch (IOException failure) { last = failure; }
                try { Thread.sleep(100); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            throw new IOException("Редактор уже открыт, но не отвечает. Закройте его окно и попробуйте снова.", last);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    private EditorInstance(FileChannel channel, FileLock lock, Path endpoint) throws IOException {
        this.channel = channel; this.lock = lock; this.endpoint = endpoint;
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        try { Files.writeString(endpoint, server.getLocalPort() + "\n" + token, StandardCharsets.UTF_8); }
        catch (IOException failure) { server.close(); throw failure; }
        Thread listener = new Thread(this::listen, "item-content-editor-activation");
        listener.setDaemon(true); listener.start();
    }

    synchronized void onActivation(Consumer<String[]> callback) {
        handler = callback;
        pending.forEach(callback); pending.clear();
    }

    private synchronized void deliver(String[] arguments) {
        if (handler == null) { if (pending.size() < 32) pending.add(arguments); }
        else handler.accept(arguments);
    }

    private void listen() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(2000);
                DataInputStream input = new DataInputStream(client.getInputStream());
                if (!token.equals(input.readUTF())) continue;
                int count = input.readInt();
                if (count < 0 || count > 32) continue;
                String[] arguments = new String[count];
                for (int i = 0; i < count; i++) arguments[i] = input.readUTF();
                deliver(arguments);
                client.getOutputStream().write(1);
            } catch (IOException ignored) {
                // A failed local connection must not close the editor or release its lock.
            }
        }
    }

    private static void forward(Path endpoint, String[] arguments) throws IOException {
        List<String> address = Files.readAllLines(endpoint, StandardCharsets.UTF_8);
        if (address.size() != 2) throw new IOException("Редактор ещё запускается.");
        int port;
        try { port = Integer.parseInt(address.get(0)); }
        catch (NumberFormatException failure) { throw new IOException("Редактор ещё запускается.", failure); }
        if (port < 1 || port > 65535) throw new IOException("Неверный адрес редактора.");
        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 500);
            client.setSoTimeout(2000);
            DataOutputStream output = new DataOutputStream(client.getOutputStream());
            output.writeUTF(address.get(1)); output.writeInt(arguments.length);
            for (String argument : arguments) output.writeUTF(argument);
            output.flush();
            if (client.getInputStream().read() != 1) throw new IOException("Нет подтверждения от редактора.");
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        try { Files.deleteIfExists(endpoint); } catch (IOException ignored) { }
        try { lock.release(); } catch (IOException ignored) { }
        try { channel.close(); } catch (IOException ignored) { }
        LOCAL.remove(endpoint, this);
    }
}
