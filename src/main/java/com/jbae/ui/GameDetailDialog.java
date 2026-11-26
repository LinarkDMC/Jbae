package com.jbae.ui;

import com.jbae.model.Game;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;

/** Modal dialog with the big cover, rating ring, genres and full description of a game. */
public class GameDetailDialog extends JDialog {

    /** Lets the dialog read and change the favorite state without knowing about the database. */
    public interface FavoriteHandler {
        boolean isFavorite(Game game);

        void toggleFavorite(Game game);
    }

    private static final int COVER_W = 264;
    private static final int COVER_H = 374;
    private static final int COVER_ARC = 24;
    private static final int COVER_PADDING = 20;

    private final Game game;
    private final FavoriteHandler favoriteHandler;
    private final PillButton favoriteButton = new PillButton("", Theme.PINK, true);
    private final CoverView coverView;

    public GameDetailDialog(JFrame owner, Game game, FavoriteHandler favoriteHandler) {
        super(owner, game.name(), true);
        this.game = game;
        this.favoriteHandler = favoriteHandler;
        this.coverView = new CoverView();

        GradientPanel content = new GradientPanel(false, Theme.BG_TOP, Theme.BG_BOTTOM);
        content.setLayout(new BorderLayout(20, 0));
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 28));
        setContentPane(content);

        JPanel coverHolder = new JPanel(new BorderLayout());
        coverHolder.setOpaque(false);
        coverHolder.add(coverView, BorderLayout.NORTH);

        content.add(coverHolder, BorderLayout.WEST);
        content.add(buildInfoPanel(), BorderLayout.CENTER);
        content.add(buildButtonPanel(), BorderLayout.SOUTH);

        updateFavoriteButton();
        loadCoverAsync();

        // Escape closes the dialog.
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setMinimumSize(new Dimension(820, 540));
        setSize(960, 620);
        setLocationRelativeTo(owner);
    }

    // ----------------------------------------------------------------- layout

    private JPanel buildInfoPanel() {
        Box top = Box.createVerticalBox();

        JLabel title = new JLabel("<html><body style='width:480px'>" + escape(game.name()) + "</body></html>");
        title.setFont(Theme.font(Font.BOLD, 28f));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(14));

        top.add(buildRatingRow());
        top.add(Box.createVerticalStrut(14));

        if (!game.genres().isEmpty()) {
            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            chips.setOpaque(false);
            chips.setAlignmentX(LEFT_ALIGNMENT);
            for (String genre : game.genres()) {
                chips.add(new Chip(genre, Theme.colorFor(genre)));
            }
            top.add(chips);
            top.add(Box.createVerticalStrut(12));
        }

        JLabel platforms = new JLabel("<html><body style='width:480px'><span style='color:#22D3EE'>PLATFORMS</span>"
                + "&nbsp;&nbsp;" + escape(joinOrUnknown(game.platforms(), "  \u2022  ")) + "</body></html>");
        platforms.setFont(Theme.font(Font.PLAIN, 13f));
        platforms.setForeground(Theme.TEXT_MUTED);
        platforms.setAlignmentX(LEFT_ALIGNMENT);
        top.add(platforms);
        top.add(Box.createVerticalStrut(18));

        JLabel about = new JLabel("ABOUT");
        about.setFont(Theme.font(Font.BOLD, 12f));
        about.setForeground(Theme.PINK);
        about.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JTextArea summary = new JTextArea(game.summary() == null || game.summary().isBlank()
                ? "No description available."
                : game.summary());
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setOpaque(false);
        summary.setForeground(Theme.TEXT);
        summary.setFont(Theme.font(Font.PLAIN, 15f));
        summary.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        summary.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(summary);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        JPanel aboutPanel = new JPanel(new BorderLayout());
        aboutPanel.setOpaque(false);
        aboutPanel.add(about, BorderLayout.NORTH);
        aboutPanel.add(scroll, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(top, BorderLayout.NORTH);
        panel.add(aboutPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRatingRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new RatingBadge(game.rating(), 68));

        String muted = "<span style='color:#A9A3C9'>";
        JLabel meta = new JLabel("<html>" + muted + "RELEASED</span>&nbsp;&nbsp;<b>"
                + escape(game.releaseDateText()) + "</b><br>"
                + muted + "DEVELOPER</span>&nbsp;&nbsp;<b>"
                + escape(joinOrUnknown(game.developers(), ", ")) + "</b></html>");
        meta.setFont(Theme.font(Font.PLAIN, 14f));
        meta.setForeground(Theme.TEXT);
        row.add(meta);
        return row;
    }

    private JPanel buildButtonPanel() {
        PillButton igdbButton = new PillButton("Open on IGDB", Theme.CYAN, true);
        igdbButton.setEnabled(game.url() != null && Desktop.isDesktopSupported());
        igdbButton.addActionListener(e -> openInBrowser());

        PillButton closeButton = new PillButton("Close", Theme.VIOLET, true);
        closeButton.addActionListener(e -> dispose());

        favoriteButton.addActionListener(e -> {
            favoriteHandler.toggleFavorite(game);
            updateFavoriteButton();
        });

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        panel.add(favoriteButton);
        panel.add(igdbButton);
        panel.add(closeButton);
        return panel;
    }

    // ---------------------------------------------------------------- actions

    private void updateFavoriteButton() {
        boolean favorite = favoriteHandler.isFavorite(game);
        favoriteButton.setActive(favorite);
        favoriteButton.setText(favorite ? "\u2665 Remove from favorites" : "\u2665 Add to favorites");
    }

    private void openInBrowser() {
        try {
            Desktop.getDesktop().browse(URI.create(game.url()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open the browser: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Downloads the cover in a background thread so the dialog opens instantly. */
    private void loadCoverAsync() {
        String coverUrl = game.coverUrl("t_cover_big");
        if (coverUrl == null) {
            return;
        }
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return CoverCache.download(coverUrl);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage image = get();
                    if (image != null) {
                        coverView.setImage(CoverCache.render(image, COVER_W, COVER_H, COVER_ARC, true));
                    }
                } catch (Exception ex) {
                    // Keep the colorful placeholder if the cover cannot be loaded.
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------- helpers

    private static String joinOrUnknown(List<String> values, String separator) {
        return values == null || values.isEmpty() ? "Unknown" : String.join(separator, values);
    }

    /** Escapes the characters that have a special meaning in the HTML used by Swing labels. */
    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------- cover view

    /** Paints the cover with a soft colored glow around it. */
    private final class CoverView extends JComponent {

        private BufferedImage image;

        CoverView() {
            setOpaque(false);
            setPreferredSize(new Dimension(COVER_W + 2 * COVER_PADDING, COVER_H + 2 * COVER_PADDING));
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            try {
                int x = COVER_PADDING;
                int y = COVER_PADDING;

                String glowKey = game.genres().isEmpty() ? game.name() : game.genres().get(0);
                Color glow = Theme.colorFor(glowKey);
                for (int i = 14; i >= 1; i--) {
                    g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 7));
                    g2.fill(new RoundRectangle2D.Double(x - i, y - i, COVER_W + 2 * i, COVER_H + 2 * i,
                            COVER_ARC + 2 * i, COVER_ARC + 2 * i));
                }

                if (image != null) {
                    g2.drawImage(image, x, y, null);
                } else {
                    RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, COVER_W, COVER_H, COVER_ARC, COVER_ARC);
                    Theme.paintPlaceholder(g2, game, shape, new Rectangle2D.Double(x, y, COVER_W, COVER_H));
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
