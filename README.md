# JBae

**JBae** is a desktop application written in Java that lets you browse thousands of video games using the [IGDB](https://www.igdb.com/) API. Search by title, read the details of any game and keep your own list of favorites, stored locally in a SQLite database so nothing is lost when you close the program.

![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)
![SQLite](https://img.shields.io/badge/storage-SQLite-003B57?logo=sqlite&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

## Features

- **A colorful, modern interface**: dark night theme, sunset-gradient header and a grid of game cards with cover art, rating rings and hover glow.
- **Browse popular games** from IGDB, with pagination (50 games per page).
- **Search by title** across the whole IGDB catalog.
- **Game details**: big cover, rating ring, colored genre tags, release date, developers, platforms and description, plus a button to open the game's page on IGDB.
- **Favorites saved locally** with SQLite: tap the heart on a card (or in the detail window) and find the game again in *Favorites*, even without an internet connection.
- **Responsive UI**: every network request runs in the background, so the window never freezes.
- **Automatic authentication**: the Twitch access token required by IGDB is requested, cached and renewed for you.

## Requirements

- [JDK 17](https://adoptium.net/) or newer
- [Apache Maven](https://maven.apache.org/) 3.8+
- A free Twitch developer application (IGDB uses Twitch authentication)

## Getting your IGDB / Twitch credentials

1. Sign in at the [Twitch Developer Console](https://dev.twitch.tv/console) (two-factor authentication must be enabled on your account).
2. Click **Register Your Application**.
3. Use any name, set the OAuth Redirect URL to `http://localhost` and choose a category such as *Application Integration*.
4. Open the application you just created, copy the **Client ID** and click **New Secret** to generate the **Client Secret**.

See the [IGDB API documentation](https://api-docs.igdb.com/#account-creation) for more information.

## Configuration

JBae looks for your credentials in this order:

1. **Environment variables**

   ```bash
   export TWITCH_CLIENT_ID=your_client_id
   export TWITCH_CLIENT_SECRET=your_client_secret
   ```

2. **A `config.properties` file** in the folder you run the program from. Start from the provided template:

   ```bash
   cp config.properties.example config.properties
   ```

   ```properties
   client_id=YOUR_CLIENT_ID
   client_secret=YOUR_CLIENT_SECRET
   ```

> **Never commit your credentials.** `config.properties` is already listed in `.gitignore`.

## Build and run

```bash
# Clone the repository
git clone https://github.com/LinarkDMC/jbae.git
cd jbae

# Build a single runnable jar
mvn clean package

# Run it
java -jar target/jbae-1.0.0.jar
```

## Usage

| Action | How |
| --- | --- |
| Browse popular games | Click the **Popular** pill |
| Search a game | Type a title and press `Enter` or click **Search** |
| Change page | Use **Prev** / **Next** at the bottom |
| See game details | Click a card (or select it with the arrow keys and press `Enter`) |
| Add or remove a favorite | Click the **heart** on a card, or use the button in the detail window |
| See your favorites | Click the **Favorites** pill |
| Close the detail window | Click **Close** or press `Esc` |

## Where is my data stored?

Favorites live in a SQLite file in your home directory:

| OS | Location |
| --- | --- |
| Linux / macOS | `~/.jbae/favorites.db` |
| Windows | `C:\Users\<you>\.jbae\favorites.db` |

You can back it up by copying that file, or delete it to clear all your favorites.

## Project structure

```
jbae
├── pom.xml
├── config.properties.example
└── src/main/java/com/jbae
    ├── Main.java                  # Entry point, loads credentials and starts the UI
    ├── api
    │   └── IgdbClient.java        # IGDB + Twitch OAuth client (java.net.http)
    ├── db
    │   └── FavoritesDatabase.java # SQLite persistence for favorites (JDBC)
    ├── model
    │   └── Game.java              # Immutable game record
    └── ui
        ├── MainFrame.java         # Main window: header, tabs, card grid, pagination, favorites
        ├── GameGrid.java          # Wrapping grid of game cards with hover effects
        ├── GameDetailDialog.java  # Detail window with big cover and rating ring
        ├── CoverCache.java        # Background cover downloads with an in-memory cache
        ├── Theme.java             # Colors, fonts and Look and Feel setup
        ├── GradientPanel.java     # Panel with a gradient background
        ├── PillButton.java        # Rounded, colorful button
        ├── RatingBadge.java       # Circular rating indicator
        └── Chip.java              # Small colored genre tag
```

## Built with

- [Java 17](https://openjdk.org/projects/jdk/17/) and Swing for the graphical interface
- [FlatLaf](https://www.formdev.com/flatlaf/) for the modern dark Look and Feel
- [Gson](https://github.com/google/gson) for JSON parsing
- [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) for local storage
- [IGDB API v4](https://api-docs.igdb.com/) as the data source

## Roadmap

- [ ] Filters by genre and platform
- [ ] Sorting options (rating, release date)
- [ ] Offline cover art for favorites
- [ ] Light theme toggle

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push the branch: `git push origin feature/my-feature`
5. Open a Pull Request

Please keep code and comments in English.

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.

## Acknowledgements

Game data is provided by [IGDB.com](https://www.igdb.com/). JBae is an independent project and is not affiliated with or endorsed by IGDB or Twitch.
