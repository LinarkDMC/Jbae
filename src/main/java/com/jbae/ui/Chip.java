package com.jbae.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** A small rounded tag, tinted with a color, used to show genres. */
final class Chip extends JComponent {

    private final String text;
    private final Color color;

    Chip(String text, Color color) {
        this.text = text;
        this.color = color;
        setOpaque(false);
        setFont(Theme.font(Font.BOLD, 12f));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 26, fm.getHeight() + 10);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.aa(g);
        try {
            int width = getWidth();
            int height = getHeight();
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            g2.fillRoundRect(0, 0, width - 1, height - 1, height, height);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 190));
            g2.drawRoundRect(0, 0, width - 1, height - 1, height, height);

            g2.setFont(getFont());
            g2.setColor(Theme.mix(color, Color.WHITE, 0.55f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (width - fm.stringWidth(text)) / 2, (height - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
