package dev.itemcontent.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorInstanceTest {
    @TempDir Path temp;

    @Test void repeatedLaunchIsForwardedToOwner() throws Exception {
        try (var owner = EditorInstance.open(temp, new String[0])) {
            assertNotNull(owner);
            var received = new LinkedBlockingQueue<String[]>(); owner.onActivation(received::add);
            for (int i = 0; i < 3; i++)
                assertNull(EditorInstance.open(temp, new String[]{"--open", "Папка с пробелами/руда.icproject"}));
            for (int i = 0; i < 3; i++)
                assertArrayEquals(new String[]{"--open", "Папка с пробелами/руда.icproject"}, received.poll(3, TimeUnit.SECONDS));
            assertNull(received.poll());
        }
    }

    @Test void activationWhileWindowIsStartingIsRetained() throws Exception {
        try (var owner = EditorInstance.open(temp, new String[0])) {
            assertNull(EditorInstance.open(temp, new String[]{"--behavior"}));
            var received = new LinkedBlockingQueue<String[]>(); owner.onActivation(received::add);
            assertArrayEquals(new String[]{"--behavior"}, received.poll(3, TimeUnit.SECONDS));
        }
    }

    @Test void closeReleasesLockAndOldEndpointDoesNotPreventRestart() throws Exception {
        var owner = EditorInstance.open(temp, new String[0]); owner.close(); owner.close();
        Files.writeString(temp.resolve("endpoint"), "stale endpoint");
        try (var replacement = EditorInstance.open(temp, new String[0])) { assertNotNull(replacement); }
    }

    @Test void unauthenticatedLocalConnectionCannotActivateEditor() throws Exception {
        try (var owner = EditorInstance.open(temp, new String[0])) {
            var received = new LinkedBlockingQueue<String[]>(); owner.onActivation(received::add);
            int port = Integer.parseInt(Files.readAllLines(temp.resolve("endpoint")).get(0));
            try (Socket client = new Socket("127.0.0.1", port)) {
                client.setSoTimeout(2000);
                new DataOutputStream(client.getOutputStream()).writeUTF("wrong token");
                assertEquals(-1, client.getInputStream().read());
            }
            assertNull(received.poll());
            assertNull(EditorInstance.open(temp, new String[]{"--open", "valid.icproject"}));
            assertNotNull(received.poll(3, TimeUnit.SECONDS));
        }
    }

    @Test void osLockSurvivesDuplicateProcessAndRecoversAfterCrash() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = Path.of(EditorInstance.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator + Path.of(EditorInstanceTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child = new ProcessBuilder(java, "-cp", classpath, Probe.class.getName(), temp.toString()).redirectErrorStream(true).start();
        try {
            var reader = new BufferedReader(new InputStreamReader(child.getInputStream()));
            var ready = CompletableFuture.supplyAsync(() -> { try { return reader.readLine(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            assertEquals("READY", ready.get(10, TimeUnit.SECONDS));
            assertNull(EditorInstance.open(temp, new String[0]));
            child.destroyForcibly(); assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            try (var replacement = EditorInstance.open(temp, new String[0])) { assertNotNull(replacement); }
        } finally { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            try (var owner = EditorInstance.open(Path.of(args[0]), new String[0])) {
                if (owner == null) throw new AssertionError("Not owner");
                System.out.println("READY"); System.out.flush();
                System.in.read();
            }
        }
    }
}
