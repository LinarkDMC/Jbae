package com.jbae;

import com.jbae.api.IgdbClient;
import com.jbae.db.FavoritesDatabase;
import com.jbae.ui.MainFrame;
import com.jbae.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application entry point.
 *
 * <p>Twitch credentials are read from the environment variables TWITCH_CLIENT_ID and
 * TWITCH_CLIENT_SECRET, or, if those are missing, from a "config.properties" file in the
 * working directory with the keys "client_id" and "client_secret".
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        Theme.install();

        String[] credentials = loadCredentials();
        if (credentials == null) {
            JOptionPane.showMessageDialog(null,
                    """
                    Twitch credentials not found.

                    Set the environment variables TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET,
                    or create a config.properties file next to the program containing:

                        client_id=YOUR_CLIENT_ID
                        client_secret=YOUR_CLIENT_SECRET

                    You can create them for free at https://dev.twitch.tv/console
                    """,
                    "JBae - configuration required", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        try {
            FavoritesDatabase database = new FavoritesDatabase(FavoritesDatabase.defaultLocation());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    database.close();
                } catch (Exception ignored) {
                    // Nothing useful can be done while shutting down.
                }
            }));

            IgdbClient client = new IgdbClient(credentials[0], credentials[1]);
            new MainFrame(client, database).setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not start the application:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /** Returns {clientId, clientSecret}, or null when no credentials are configured. */
    private static String[] loadCredentials() {
        String id = System.getenv("TWITCH_CLIENT_ID");
        String secret = System.getenv("TWITCH_CLIENT_SECRET");
        if (isPresent(id) && isPresent(secret)) {
            return new String[] {id.trim(), secret.trim()};
        }

        Path file = Path.of("config.properties");
        if (Files.isRegularFile(file)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException e) {
                return null;
            }
            id = properties.getProperty("client_id");
            secret = properties.getProperty("client_secret");
            if (isPresent(id) && isPresent(secret)) {
                return new String[] {id.trim(), secret.trim()};
            }
        }
        return null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
