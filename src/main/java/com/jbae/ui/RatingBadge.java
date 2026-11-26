package com.jbae.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * A round badge that shows a 0-100 rating inside a colored progress ring.
 * The color goes from green (great) to red (poor).
 */
final class RatingBadge extends JComponent {

    private final Double rating;
    private final int size;

    RatingBadge(Double rating, int size) {
        this.rating = rating;
        this.size = size;
        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.aa(g);
        try {
            paintRing(g2, rating, 0, 0, size);
        } finally {
            g2.dispose();
        }
    }

    /** Draws the badge with its top-left corner at (x, y) and the given diameter. */
    static void paintRing(Graphics2D target, Double rating, double x, double y, double diameter) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            double stroke = Math.max(3, diameter * 0.09);
            double inset = stroke / 2 + 1;
            double ringSize = diameter - 2 * inset;

            g.setColor(new Color(0x120B2B, false));
            g.fill(new Ellipse2D.Double(x, y, diameter, diameter));

            g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new Ellipse2D.Double(x + inset, y + inset, ringSize, ringSize));

            if (rating != null) {
                double sweep = -3.6 * Math.max(0, Math.min(100, rating));
                g.setColor(Theme.ratingColor(rating));
                g.draw(new Arc2D.Double(x + inset, y + inset, ringSize, ringSize, 90, sweep, Arc2D.OPEN));
            }

            String label = rating == null ? "N/A" : String.valueOf(Math.round(rating));
            float fontSize = (float) (rating == null ? diameter * 0.26 : diameter * 0.34);
            g.setFont(Theme.font(Font.BOLD, fontSize));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int textX = (int) (x + (diameter - fm.stringWidth(label)) / 2);
            int textY = (int) (y + (diameter - fm.getHeight()) / 2 + fm.getAscent());
            g.drawString(label, textX, textY);
        } finally {
            g.dispose();
        }
    }
}
