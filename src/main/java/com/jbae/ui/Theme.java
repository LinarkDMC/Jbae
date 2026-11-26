package com.jbae.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import com.jbae.model.Game;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;

/**
 * Central place for the colors, fonts and drawing helpers that give JBae its look:
 * a deep purple night background with vivid pink, cyan, violet and amber accents.
 */
public final class Theme {

    static final Color BG_TOP = new Color(0x1B1038);
    static final Color BG_BOTTOM = new Color(0x0B0A1E);
    static final Color CARD = new Color(0x241B4B);
    static final Color CARD_HOVER = new Color(0x31246A);
    static final Color TEXT = new Color(0xF4F1FF);
    static final Color TEXT_MUTED = new Color(0xA9A3C9);

    static final Color PINK = new Color(0xFF4D8D);
    static final Color CYAN = new Color(0x22D3EE);
    static final Color VIOLET = new Color(0x8B5CF6);
    static final Color AMBER = new Color(0xFBBF24);
    static final Color GREEN = new Color(0x34D399);
    static final Color ORANGE = new Color(0xFB923C);
    static final Color BLUE = new Color(0x60A5FA);
    static final Color RED = new Color(0xF43F5E);

    /** Colors used to tint genres and placeholder covers. */
    private static final Color[] PALETTE = {PINK, CYAN, VIOLET, AMBER, GREEN, ORANGE, BLUE};

    private Theme() {
    }

    /** Installs the dark FlatLaf Look and Feel with JBae's accent color. Call before creating any window. */
    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", "#FF4D8D"));
        FlatDarkLaf.setup();

        UIManager.put("Component.arc", 18);
        UIManager.put("Button.arc", 999);
        UIManager.put("TextComponent.arc", 999);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 14);
        UIManager.put("ScrollBar.thumb", new Color(0x5B3FD0));
        UIManager.put("ScrollBar.hoverThumbColor", VIOLET);
        UIManager.put("ScrollBar.pressedThumbColor", PINK);
    }

    /** Returns the default UI font with the given style and size. */
    static Font font(int style, float size) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(style, size);
    }

    /**
     * Like {@link #font(int, float)}, but the size is multiplied by the screen scale factor.
     * Use it for regular Swing components (text fields...) whose padding and height are scaled
     * by the Look and Feel, so the text keeps the right proportions on any screen.
     */
    static Font uiFont(int style, float size) {
        return font(style, UIScale.scale(size));
    }

    /** Scales a pixel value by the screen scale factor. */
    static int scale(int value) {
        return UIScale.scale(value);
    }

    /** Creates a copy of the graphics context with antialiasing enabled. The caller must dispose it. */
    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Picks a stable accent color for any text (genre names, game titles...). */
    static Color colorFor(String key) {
        int hash = key == null ? 0 : key.hashCode();
        return PALETTE[Math.floorMod(hash, PALETTE.length)];
    }

    /** Green for acclaimed games, amber for good ones, orange and red for the rest. */
    static Color ratingColor(Double rating) {
        if (rating == null) {
            return TEXT_MUTED;
        }
        if (rating >= 80) {
            return GREEN;
        }
        if (rating >= 65) {
            return AMBER;
        }
        if (rating >= 50) {
            return ORANGE;
        }
        return RED;
    }

    /** Linear mix of two colors: 0 returns {@code a}, 1 returns {@code b}. */
    static Color mix(Color a, Color b, float t) {
        float inverse = 1f - t;
        return new Color(
                Math.round(a.getRed() * inverse + b.getRed() * t),
                Math.round(a.getGreen() * inverse + b.getGreen() * t),
                Math.round(a.getBlue() * inverse + b.getBlue() * t),
                a.getAlpha());
    }

    /** A rectangle whose top corners are rounded and whose bottom corners are square. */
    static Shape topRounded(double x, double y, double w, double h, double arc) {
        Area area = new Area(new RoundRectangle2D.Double(x, y, w, h + arc, arc, arc));
        area.intersect(new Area(new Rectangle2D.Double(x, y, w, h)));
        return area;
    }

    /** A heart shape centered on (cx, cy) with a width of roughly {@code size} pixels. */
    static Shape heart(double cx, double cy, double size) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(cx, cy + size * 0.40);
        path.curveTo(cx - size * 0.55, cy, cx - size * 0.45, cy - size * 0.45, cx, cy - size * 0.18);
        path.curveTo(cx + size * 0.45, cy - size * 0.45, cx + size * 0.55, cy, cx, cy + size * 0.40);
        path.closePath();
        return path;
    }

    /**
     * Paints a colorful gradient with the title's initial. It stands in for a cover that is
     * still loading, or that the game does not have.
     */
    static void paintPlaceholder(Graphics2D g, Game game, Shape shape, Rectangle2D bounds) {
        String name = game.name() == null || game.name().isBlank() ? "?" : game.name().trim();
        Color base = colorFor(name);
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        Color dark = Color.getHSBColor(hsb[0], 0.75f, 0.45f);
        Color light = Color.getHSBColor((hsb[0] + 0.12f) % 1f, 0.80f, 0.95f);

        g.setPaint(new GradientPaint(
                (float) bounds.getX(), (float) bounds.getY(), light,
                (float) bounds.getMaxX(), (float) bounds.getMaxY(), dark));
        g.fill(shape);

        String letter = name.substring(0, 1).toUpperCase();
        g.setFont(font(Font.BOLD, (float) (bounds.getHeight() * 0.35)));
        g.setColor(new Color(255, 255, 255, 200));
        FontMetrics fm = g.getFontMetrics();
        int x = (int) (bounds.getCenterX() - fm.stringWidth(letter) / 2.0);
        int y = (int) (bounds.getCenterY() - fm.getHeight() / 2.0 + fm.getAscent());
        g.drawString(letter, x, y);
    }
}
