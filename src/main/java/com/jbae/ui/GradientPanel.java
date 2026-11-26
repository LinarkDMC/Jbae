package com.jbae.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Point2D;

/** A panel that paints a linear gradient (horizontal or vertical) as its background. */
final class GradientPanel extends JPanel {

    private final boolean horizontal;
    private final float[] fractions;
    private final Color[] colors;

    /** Gradient with several color stops; {@code fractions} must be increasing and match {@code colors}. */
    GradientPanel(boolean horizontal, float[] fractions, Color... colors) {
        this.horizontal = horizontal;
        this.fractions = fractions.clone();
        this.colors = colors.clone();
    }

    /** Simple two-color gradient. */
    GradientPanel(boolean horizontal, Color from, Color to) {
        this(horizontal, new float[] {0f, 1f}, from, to);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        Point2D start = new Point2D.Float(0, 0);
        Point2D end = horizontal ? new Point2D.Float(width, 0) : new Point2D.Float(0, height);
        g2.setPaint(new LinearGradientPaint(start, end, fractions, colors));
        g2.fillRect(0, 0, width, height);
        g2.dispose();
    }
}
