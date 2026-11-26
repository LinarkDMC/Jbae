package com.jbae.ui;

import javax.swing.JButton;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * A fully rounded, colorful button painted by hand so it looks the same on every platform.
 *
 * <p>A button is drawn solid when it is "filled" or "active", and as a translucent outlined pill
 * otherwise. The active state is handy for tab-like buttons.
 */
final class PillButton extends JButton {

    private static final int PADDING_X = 18;
    private static final int PADDING_Y = 8;

    private final Color accent;
    private final boolean outlined;
    private boolean active;

    /**
     * @param text     button label
     * @param accent   main color of the button
     * @param outlined {@code true} for an outlined pill that becomes solid when active,
     *                 {@code false} for an always solid pill
     */
    PillButton(String text, Color accent, boolean outlined) {
        super(text);
        this.accent = accent;
        this.outlined = outlined;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(Font.BOLD, 13f));
        setForeground(Color.WHITE);
    }

    /** Marks the button as selected: an outlined pill is drawn solid while active. */
    void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(getText()) + 2 * PADDING_X, fm.getHeight() + 2 * PADDING_Y);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.aa(g);
        try {
            int width = getWidth();
            int height = getHeight();
            if (!isEnabled()) {
                g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
            }

            boolean solid = !outlined || active;
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();

            if (solid) {
                Color fill = accent;
                if (pressed) {
                    fill = Theme.mix(fill, Color.BLACK, 0.20f);
                } else if (hover) {
                    fill = Theme.mix(fill, Color.WHITE, 0.18f);
                }
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, width - 1, height - 1, height, height);
            } else {
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), hover ? 70 : 28));
                g2.fillRoundRect(0, 0, width - 1, height - 1, height, height);
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(accent);
                g2.drawRoundRect(1, 1, width - 3, height - 3, height - 2, height - 2);
            }

            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int x = (width - fm.stringWidth(text)) / 2;
            int y = (height - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, x, y);
        } finally {
            g2.dispose();
        }
    }
}
