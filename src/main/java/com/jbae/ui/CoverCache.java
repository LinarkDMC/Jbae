package com.jbae.ui;

import com.jbae.model.Game;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads game covers in background threads and keeps the most recent ones in memory,
 * already scaled and with rounded corners, ready to be painted on a card.
 */
final class CoverCache {

    /** Size of a cover as painted on a card. */
    static final int COVER_W = 180;
    static final int COVER_H = 255;
    static final int ARC = 18;

    private static final int MAX_ENTRIES = 400;
    private static final CoverCache INSTANCE = new CoverCache();

    /** Least-recently-used map: the oldest covers are dropped when the limit is reached. */
    private final Map<String, BufferedImage> covers = new LinkedHashMap<String, BufferedImage>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private final Map<String, Set<Runnable>> waiting = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "cover-loader");
        thread.setDaemon(true);
        return thread;
    });

    private CoverCache() {
    }

    static CoverCache getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the card-sized cover of the game if it is already loaded; otherwise returns
     * {@code null} and starts loading it. When the download finishes, {@code onLoaded} runs
     * on the Swing event thread (the same callback is never registered twice).
     */
    synchronized BufferedImage get(Game game, Runnable onLoaded) {
        String id = game.coverImageId();
        if (id == null) {
            return null;
        }
        BufferedImage cached = covers.get(id);
        if (cached != null) {
            return cached;
        }
        if (failed.contains(id)) {
            return null;
        }
        Set<Runnable> callbacks = waiting.get(id);
        if (callbacks != null) {
            if (onLoaded != null) {
                callbacks.add(onLoaded);
            }
            return null;
        }
        callbacks = new LinkedHashSet<>();
        if (onLoaded != null) {
            callbacks.add(onLoaded);
        }
        waiting.put(id, callbacks);
        String url = game.coverUrl("t_cover_big");
        executor.execute(() -> load(id, url));
        return null;
    }

    private void load(String id, String url) {
        BufferedImage card = null;
        try {
            BufferedImage original = download(url);
            if (original != null) {
                card = render(original, COVER_W, COVER_H, ARC, false);
            }
        } catch (Exception ignored) {
            // A missing cover is not fatal: the card keeps its colorful placeholder.
        }

        Set<Runnable> callbacks;
        synchronized (this) {
            if (card != null) {
                covers.put(id, card);
            } else {
                failed.add(id);
            }
            callbacks = waiting.remove(id);
        }
        if (callbacks != null) {
            SwingUtilities.invokeLater(() -> callbacks.forEach(Runnable::run));
        }
    }

    /** Downloads an image from the given URL. Returns {@code null} if the format is unsupported. */
    static BufferedImage download(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "JBae/1.0");
        try (InputStream in = connection.getInputStream()) {
            return ImageIO.read(in);
        }
    }

    /**
     * Scales the image to fill {@code w} x {@code h} (cropping the overflow) and rounds its corners
     * with a smooth, antialiased edge.
     *
     * @param roundBottom {@code true} to round all four corners, {@code false} to round only the top ones
     */
    static BufferedImage render(BufferedImage source, int w, int h, int arc, boolean roundBottom) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // The mask: whatever is painted afterwards is kept only where the mask is opaque.
            Shape mask = roundBottom
                    ? new RoundRectangle2D.Double(0, 0, w, h, arc, arc)
                    : new RoundRectangle2D.Double(0, 0, w, h + arc, arc, arc);
            g.setColor(Color.WHITE);
            g.fill(mask);

            g.setComposite(AlphaComposite.SrcIn);
            double scale = Math.max((double) w / source.getWidth(), (double) h / source.getHeight());
            int drawW = (int) Math.ceil(source.getWidth() * scale);
            int drawH = (int) Math.ceil(source.getHeight() * scale);
            g.drawImage(source, (w - drawW) / 2, (h - drawH) / 2, drawW, drawH, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
