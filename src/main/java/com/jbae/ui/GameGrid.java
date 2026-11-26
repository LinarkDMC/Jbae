package com.jbae.ui;

import com.jbae.model.Game;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A wrapping grid of game cards. Each card shows the cover, a rating ring, a favorite heart,
 * the title, the release year and the main genre. Cards lift and glow when hovered.
 *
 * <p>Click a card to open its details, click the heart to toggle the favorite state.
 */
final class GameGrid extends JList<Game> {

    static final int CARD_W = 180;
    static final int CARD_H = 326;
    static final int GAP = 14;

    private static final int LIFT = 4;
    private static final int ARC = CoverCache.ARC;
    private static final int HEART_RADIUS = 16;

    private final DefaultListModel<Game> listModel = new DefaultListModel<>();
    private final GameDetailDialog.FavoriteHandler favorites;
    private final Consumer<Game> onOpen;
    private final CoverCache covers = CoverCache.getInstance();
    private final Runnable repaintAction = this::repaint;

    private int hoverIndex = -1;
    private boolean heartHover = false;
    private String emptyMessage = "";

    GameGrid(GameDetailDialog.FavoriteHandler favorites, Consumer<Game> onOpen) {
        this.favorites = favorites;
        this.onOpen = onOpen;

        setModel(listModel);
        setLayoutOrientation(HORIZONTAL_WRAP);
        setVisibleRowCount(-1);
        setFixedCellWidth(CARD_W + 2 * GAP);
        setFixedCellHeight(CARD_H + 2 * GAP);
        setCellRenderer(new CardRenderer());
        setOpaque(false);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        installMouseHandling();

        // Keyboard support: arrow keys move between cards, Enter opens the selected one.
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openSelected");
        getActionMap().put("openSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Game selected = getSelectedValue();
                if (selected != null) {
                    GameGrid.this.onOpen.accept(selected);
                }
            }
        });
    }

    /** Replaces the cards shown in the grid and scrolls back to the top. */
    void setGames(List<Game> games) {
        hoverIndex = -1;
        heartHover = false;
        listModel.clear();
        listModel.addAll(games);
        clearSelection();
        scrollRectToVisible(new Rectangle(0, 0, 1, 1));
        repaint();
    }

    /** Text shown in the middle of the grid while it has no cards. */
    void setEmptyMessage(String message) {
        this.emptyMessage = message == null ? "" : message;
        repaint();
    }

    // ------------------------------------------------------------ mouse input

    private void installMouseHandling() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                updateHover(null);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int index = indexAt(e.getPoint());
                if (index < 0) {
                    return;
                }
                Game game = listModel.get(index);
                if (isOverHeart(index, e.getPoint())) {
                    favorites.toggleFavorite(game);
                    repaintCell(index);
                } else {
                    onOpen.accept(game);
                }
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    private void updateHover(Point point) {
        int newIndex = point == null ? -1 : indexAt(point);
        boolean newHeart = newIndex >= 0 && isOverHeart(newIndex, point);
        if (newIndex != hoverIndex || newHeart != heartHover) {
            int oldIndex = hoverIndex;
            hoverIndex = newIndex;
            heartHover = newHeart;
            repaintCell(oldIndex);
            repaintCell(newIndex);
            setCursor(newIndex >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }
    }

    /** Index of the card under the point, or -1 when the point is outside any card cell. */
    private int indexAt(Point point) {
        int index = locationToIndex(point);
        if (index >= 0) {
            Rectangle bounds = getCellBounds(index, index);
            if (bounds != null && bounds.contains(point)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isOverHeart(int index, Point point) {
        Rectangle bounds = getCellBounds(index, index);
        if (bounds == null) {
            return false;
        }
        // The hovered card is lifted, so the heart sits LIFT pixels higher.
        double centerX = bounds.x + GAP + CARD_W - 24;
        double centerY = bounds.y + GAP - LIFT + 24;
        return point.distance(centerX, centerY) <= HEART_RADIUS + 2;
    }

    private void repaintCell(int index) {
        if (index < 0 || index >= listModel.size()) {
            return;
        }
        Rectangle bounds = getCellBounds(index, index);
        if (bounds != null) {
            repaint(bounds);
        }
    }

    // --------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (listModel.isEmpty() && !emptyMessage.isEmpty()) {
            Graphics2D g2 = Theme.aa(g);
            try {
                g2.setFont(Theme.font(Font.BOLD, 18f));
                g2.setColor(Theme.TEXT_MUTED);
                FontMetrics fm = g2.getFontMetrics();
                Rectangle visible = getVisibleRect();
                int x = visible.x + (visible.width - fm.stringWidth(emptyMessage)) / 2;
                int y = visible.y + visible.height / 3;
                g2.drawString(emptyMessage, x, y);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Cuts the text and adds an ellipsis until it fits in {@code maxWidth} pixels. */
    private static String ellipsize(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        String result = text;
        while (!result.isEmpty() && fm.stringWidth(result + ellipsis) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.stripTrailing() + ellipsis;
    }

    /** Paints one card. A single instance is reused for every cell (the "rubber stamp" pattern). */
    private final class CardRenderer extends JComponent implements ListCellRenderer<Game> {

        private Game game;
        private boolean hovered;
        private boolean focused;
        private boolean favorite;
        private boolean heartOver;

        @Override
        public Component getListCellRendererComponent(JList<? extends Game> list, Game value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            game = value;
            hovered = index == hoverIndex;
            focused = isSelected && cellHasFocus;
            favorite = favorites.isFavorite(value);
            heartOver = hovered && heartHover;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(CARD_W + 2 * GAP, CARD_H + 2 * GAP);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (game == null) {
                return;
            }
            Graphics2D g2 = Theme.aa(g);
            try {
                int x = GAP;
                int y = GAP - (hovered ? LIFT : 0);

                paintShadow(g2, x, y);

                Color body = hovered ? Theme.CARD_HOVER : Theme.CARD;
                g2.setColor(body);
                g2.fill(new RoundRectangle2D.Double(x, y, CARD_W, CARD_H, ARC, ARC));

                paintCover(g2, x, y, body);
                RatingBadge.paintRing(g2, game.rating(), x + 10, y + CoverCache.COVER_H - 26, 46);
                paintHeart(g2, x, y);
                paintTexts(g2, x, y);

                if (hovered || focused) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setPaint(new GradientPaint(x, y, Theme.PINK, x + CARD_W, y + CARD_H, Theme.CYAN));
                    g2.draw(new RoundRectangle2D.Double(x + 1, y + 1, CARD_W - 2, CARD_H - 2, ARC, ARC));
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintShadow(Graphics2D g2, int x, int y) {
            // Soft shadow made of stacked translucent rectangles; pink glow when hovered.
            Color shadow = hovered ? new Color(255, 77, 141, 16) : new Color(0, 0, 0, 14);
            int offset = hovered ? LIFT + 6 : 6;
            for (int i = 6; i >= 1; i--) {
                g2.setColor(shadow);
                g2.fill(new RoundRectangle2D.Double(x - i, y - i + offset, CARD_W + 2 * i, CARD_H + 2 * i,
                        ARC + 2 * i, ARC + 2 * i));
            }
        }

        private void paintCover(Graphics2D g2, int x, int y, Color body) {
            BufferedImage cover = covers.get(game, repaintAction);
            if (cover != null) {
                g2.drawImage(cover, x, y, null);
            } else {
                Shape shape = Theme.topRounded(x, y, CARD_W, CoverCache.COVER_H, ARC);
                Rectangle2D bounds = new Rectangle2D.Double(x, y, CARD_W, CoverCache.COVER_H);
                Theme.paintPlaceholder(g2, game, shape, bounds);
            }

            // Fade the bottom of the cover into the card body so the rating badge blends in.
            int fadeHeight = 70;
            int fadeTop = y + CoverCache.COVER_H - fadeHeight;
            Color transparent = new Color(body.getRed(), body.getGreen(), body.getBlue(), 0);
            g2.setPaint(new GradientPaint(0, fadeTop, transparent, 0, y + CoverCache.COVER_H, body));
            g2.fillRect(x, fadeTop, CARD_W, fadeHeight);
        }

        private void paintHeart(Graphics2D g2, int x, int y) {
            double centerX = x + CARD_W - 24;
            double centerY = y + 24;
            g2.setColor(heartOver ? new Color(255, 255, 255, 235) : new Color(15, 8, 40, 150));
            g2.fill(new Ellipse2D.Double(centerX - HEART_RADIUS, centerY - HEART_RADIUS,
                    HEART_RADIUS * 2, HEART_RADIUS * 2));

            Shape heart = Theme.heart(centerX, centerY + 1, 17);
            if (favorite) {
                g2.setColor(Theme.PINK);
                g2.fill(heart);
            } else {
                g2.setStroke(new BasicStroke(1.8f));
                g2.setColor(heartOver ? Theme.PINK : Color.WHITE);
                g2.draw(heart);
            }
        }

        private void paintTexts(Graphics2D g2, int x, int y) {
            int textX = x + 12;
            int maxWidth = CARD_W - 24;

            g2.setFont(Theme.font(Font.BOLD, 14f));
            g2.setColor(Theme.TEXT);
            String title = ellipsize(Objects.toString(game.name(), ""), g2.getFontMetrics(), maxWidth);
            g2.drawString(title, textX, y + CoverCache.COVER_H + 40);

            g2.setFont(Theme.font(Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            int baseline = y + CoverCache.COVER_H + 60;

            String date = game.releaseDateText();
            String year = date.length() >= 4 && Character.isDigit(date.charAt(0)) ? date.substring(0, 4) : "TBA";
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(year, textX, baseline);

            if (!game.genres().isEmpty()) {
                String genre = game.genres().get(0);
                int used = fm.stringWidth(year);
                String separator = "  \u2022  ";
                String label = ellipsize(separator + genre, fm, maxWidth - used);
                g2.setColor(Theme.colorFor(genre));
                g2.drawString(label, textX + used, baseline);
            }
        }
    }
}
