package com.jbae.ui;

import com.jbae.api.IgdbClient;
import com.jbae.db.FavoritesDatabase;
import com.jbae.model.Game;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Main window: a colorful header with the search box, tab-like pills to switch between popular
 * games and favorites, and a grid of game cards. Click a card to open its details.
 */
public class MainFrame extends JFrame implements GameDetailDialog.FavoriteHandler {

    private enum Mode { POPULAR, SEARCH, FAVORITES }

    private static final int PAGE_SIZE = 50;

    private final IgdbClient client;
    private final FavoritesDatabase database;

    private final GameGrid grid = new GameGrid(this, this::showDetails);
    /** Search box whose size follows its font, so the typed text is never squeezed or cut. */
    private final JTextField searchField = new JTextField(26) {
        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            return new Dimension(Theme.scale(360), metrics.getHeight() + Theme.scale(24));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    };
    private final PillButton searchButton = new PillButton("Search", new Color(0x1B1038), false);
    private final PillButton popularButton = new PillButton("Popular", Theme.VIOLET, true);
    private final PillButton favoritesButton = new PillButton("\u2665 Favorites", Theme.PINK, true);
    private final PillButton previousButton = new PillButton("\u25C0 Prev", Theme.CYAN, true);
    private final PillButton nextButton = new PillButton("Next \u25B6", Theme.CYAN, true);
    private final JLabel pageLabel = new JLabel("Page 1");
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();

    /** Ids of the favorite games, kept in memory to paint the hearts quickly. */
    private final Set<Long> favoriteIds = new HashSet<>();

    private Mode mode = Mode.POPULAR;
    private String currentQuery = "";
    private int page = 0;
    private boolean pageIsFull = false;

    public MainFrame(IgdbClient client, FavoritesDatabase database) {
        super("JBae");
        this.client = client;
        this.database = database;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(createAppIcon());

        GradientPanel background = new GradientPanel(false, Theme.BG_TOP, Theme.BG_BOTTOM);
        background.setLayout(new BorderLayout());
        setContentPane(background);

        background.add(buildTopArea(), BorderLayout.NORTH);
        background.add(buildScrollPane(), BorderLayout.CENTER);
        background.add(buildFooter(), BorderLayout.SOUTH);

        wireActions();
        loadFavoriteIds();
        refreshPills();

        setMinimumSize(new Dimension(760, 520));
        setSize(1180, 780);
        setLocationRelativeTo(null);

        loadCurrentView();
    }

    // ---------------------------------------------------------------- layout

    private JPanel buildTopArea() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(buildHeader(), BorderLayout.NORTH);
        top.add(buildToolbar(), BorderLayout.CENTER);

        progressBar.setOpaque(false);
        progressBar.setBorder(null);
        progressBar.setForeground(Theme.CYAN);
        progressBar.setPreferredSize(new Dimension(10, 4));
        top.add(progressBar, BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildHeader() {
        // A sunset gradient: violet -> pink -> orange.
        GradientPanel header = new GradientPanel(true, new float[] {0f, 0.55f, 1f},
                new Color(0x7C3AED), new Color(0xDB2777), new Color(0xFB923C));
        header.setLayout(new BorderLayout(16, 0));
        header.setBorder(BorderFactory.createEmptyBorder(14, 24, 14, 24));

        JLabel logo = new JLabel("JBae");
        logo.setFont(Theme.font(Font.BOLD, 36f));
        logo.setForeground(Color.WHITE);

        JLabel tagline = new JLabel("Discover \u2022 Play \u2022 Collect");
        tagline.setFont(Theme.font(Font.PLAIN, 13f));
        tagline.setForeground(new Color(255, 255, 255, 215));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(logo);
        titleBox.add(tagline);

        searchField.putClientProperty("JTextField.placeholderText", "Search any game...");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.putClientProperty("JTextField.padding", new Insets(8, 18, 8, 18));
        searchField.setFont(Theme.uiFont(Font.PLAIN, 16f));
        searchField.setBackground(new Color(0x1B1038));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Theme.CYAN);

        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        searchBox.setOpaque(false);
        searchBox.add(searchField);
        searchBox.add(searchButton);

        // GridBagLayout keeps the search box vertically centered next to the two-line logo.
        JPanel searchHolder = new JPanel(new GridBagLayout());
        searchHolder.setOpaque(false);
        searchHolder.add(searchBox);

        header.add(titleBox, BorderLayout.WEST);
        header.add(searchHolder, BorderLayout.EAST);
        return header;
    }

    private JPanel buildToolbar() {
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pills.setOpaque(false);
        pills.add(popularButton);
        pills.add(favoritesButton);

        statusLabel.setForeground(Theme.TEXT_MUTED);
        statusLabel.setFont(Theme.font(Font.PLAIN, 13f));

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(14, 14, 2, 24));
        toolbar.add(pills, BorderLayout.WEST);
        toolbar.add(statusLabel, BorderLayout.EAST);
        return toolbar;
    }

    private JScrollPane buildScrollPane() {
        JScrollPane scroll = new JScrollPane(grid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        // Simple scrolling avoids painting glitches over the transparent, gradient background.
        scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scroll.getVerticalScrollBar().setUnitIncrement(40);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        return scroll;
    }

    private JPanel buildFooter() {
        pageLabel.setForeground(Theme.TEXT);
        pageLabel.setFont(Theme.font(Font.BOLD, 13f));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 12, 14, 12));
        footer.add(previousButton);
        footer.add(pageLabel);
        footer.add(nextButton);
        return footer;
    }

    private void wireActions() {
        searchButton.addActionListener(e -> startSearch());
        searchField.addActionListener(e -> startSearch());
        popularButton.addActionListener(e -> switchMode(Mode.POPULAR, ""));
        favoritesButton.addActionListener(e -> switchMode(Mode.FAVORITES, ""));
        previousButton.addActionListener(e -> {
            if (page > 0) {
                page--;
                loadCurrentView();
            }
        });
        nextButton.addActionListener(e -> {
            page++;
            loadCurrentView();
        });
    }

    /** Draws a small gradient app icon with the letter J, so the window does not use the Java cup. */
    private static Image createAppIcon() {
        int size = 64;
        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x7C3AED), size, size, new Color(0xFB923C)));
            g.fill(new RoundRectangle2D.Double(2, 2, size - 4, size - 4, 18, 18));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38));
            FontMetrics fm = g.getFontMetrics();
            String letter = "J";
            g.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g.dispose();
        }
        return icon;
    }

    // ------------------------------------------------------------ navigation

    private void startSearch() {
        String text = searchField.getText().trim();
        if (!text.isEmpty()) {
            switchMode(Mode.SEARCH, text);
        }
    }

    private void switchMode(Mode newMode, String query) {
        mode = newMode;
        currentQuery = query;
        page = 0;
        refreshPills();
        loadCurrentView();
    }

    /** Loads the games of the current mode/page in a background thread and fills the grid. */
    private void loadCurrentView() {
        final Mode requestedMode = mode;
        final String query = currentQuery;
        final int offset = page * PAGE_SIZE;

        setBusy(true);
        statusLabel.setText(requestedMode == Mode.FAVORITES ? "Loading favorites..." : "Loading games from IGDB...");
        grid.setEmptyMessage("Loading...");

        new SwingWorker<List<Game>, Void>() {
            @Override
            protected List<Game> doInBackground() throws Exception {
                return switch (requestedMode) {
                    case FAVORITES -> database.findAll();
                    case SEARCH -> client.search(query, PAGE_SIZE, offset);
                    case POPULAR -> client.browsePopular(PAGE_SIZE, offset);
                };
            }

            @Override
            protected void done() {
                try {
                    List<Game> games = get();
                    grid.setGames(games);
                    grid.setEmptyMessage(emptyText(requestedMode));
                    pageIsFull = games.size() == PAGE_SIZE;
                    statusLabel.setText(describeResult(requestedMode, query, games.size()));
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    grid.setEmptyMessage("Something went wrong");
                    statusLabel.setText("Error while loading games");
                    showError("Could not load games:\n" + cause.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    setBusy(false);
                    updateNavigation();
                }
            }
        }.execute();
    }

    private static String emptyText(Mode mode) {
        return mode == Mode.FAVORITES
                ? "No favorites yet - tap the heart on any game to save it"
                : "No games found";
    }

    private static String describeResult(Mode mode, String query, int count) {
        return switch (mode) {
            case FAVORITES -> count + " favorite(s) saved on this computer";
            case SEARCH -> count + " result(s) on this page for \"" + query + "\"";
            case POPULAR -> "Popular games \u2022 " + count + " on this page";
        };
    }

    private void setBusy(boolean busy) {
        searchButton.setEnabled(!busy);
        popularButton.setEnabled(!busy);
        favoritesButton.setEnabled(!busy);
        progressBar.setIndeterminate(busy);
        if (!busy) {
            progressBar.setValue(0);
        } else {
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
        }
    }

    private void updateNavigation() {
        boolean paged = mode != Mode.FAVORITES;
        previousButton.setEnabled(paged && page > 0);
        nextButton.setEnabled(paged && pageIsFull);
        pageLabel.setText(paged ? "Page " + (page + 1) : "Your collection");
    }

    /** Highlights the active tab and shows how many favorites the user has. */
    private void refreshPills() {
        popularButton.setActive(mode == Mode.POPULAR);
        favoritesButton.setActive(mode == Mode.FAVORITES);
        favoritesButton.setText("\u2665 Favorites (" + favoriteIds.size() + ")");
    }

    // ------------------------------------------------------------- favorites

    private void loadFavoriteIds() {
        try {
            favoriteIds.clear();
            favoriteIds.addAll(database.findAllIds());
        } catch (SQLException e) {
            showError("Could not read the favorites database:\n" + e.getMessage());
        }
    }

    @Override
    public boolean isFavorite(Game game) {
        return favoriteIds.contains(game.id());
    }

    @Override
    public void toggleFavorite(Game game) {
        try {
            if (favoriteIds.contains(game.id())) {
                database.remove(game.id());
                favoriteIds.remove(game.id());
            } else {
                database.add(game);
                favoriteIds.add(game.id());
            }
        } catch (SQLException e) {
            showError("Could not update the favorites database:\n" + e.getMessage());
            return;
        }
        refreshPills();
        if (mode == Mode.FAVORITES) {
            // Reload so removed games disappear from the list.
            SwingUtilities.invokeLater(this::loadCurrentView);
        } else {
            grid.repaint();
        }
    }

    // --------------------------------------------------------------- dialogs

    private void showDetails(Game game) {
        new GameDetailDialog(this, game, this).setVisible(true);
        grid.repaint();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
