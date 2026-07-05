# Baroness Design System

## Colors

Custom app colors are defined in `Colors` object (`ui/theme/Color.kt`):

- `Colors.bg`: `#0A0A0A` — main screen background (used directly by screens)
- `Colors.accent`: `#66CC99` — primary green accent (alias of `greenAccent`)
- `Colors.greenAccent`: `#66CC99` — green accent (buttons, borders)
- `Colors.purpleAccent`: `#AA66FF` — purple accent (avatar borders, highlights)
- `Colors.pinkAccent`: `#FF66CC` — pink accent (loading indicators)
- `Colors.textDim`: `#CCCCCC` — secondary/dimmed text

MaterialTheme dark color scheme (`ui/theme/Theme.kt`) also defines:

- `background`: `#121212`
- `surface`: `#1E1E1E`

> **Note:** Screens use `Colors.bg` (`#0A0A0A`) directly rather than `MaterialTheme.colorScheme.background` (`#121212`), so `Colors.bg` is the effective screen background color.

The default Compose palette (`Purple80`, `PurpleGrey80`, `Pink80`, etc.) is defined in `Color.kt` but not used anywhere in the app.

## Spacing

Spacing is responsive — DashboardScreen computes padding dynamically using `clamp()` based on screen width:

- Screen outer padding: `clamp(10dp, screenWidth × 3%, 20dp)`
- Header inner padding: `clamp(15dp, screenWidth × 4%, 20dp)`
- Icon size: 24dp (standard)

Other screens use fixed values:

- PhotosScreen grid padding: 8dp horizontal/vertical
- WishlistScreen: 16dp horizontal padding, 16dp top padding

## Typography

Defined in `ui/theme/Type.kt`. Only one style is actively configured:

- `bodyLarge`: 16sp, Regular weight, 24sp line height, 0.5sp letter spacing

All other typography styles (`titleLarge`, `labelSmall`, etc.) are commented out. Screens use `MaterialTheme.typography` roles (e.g. `headlineMedium`, `titleMedium`, `bodySmall`) and also hardcoded dynamic font sizes via `clamp()` — for example, welcome text ranges from 19–26sp depending on screen width.

## Components

### Cards
Actual corner radii vary by context:
- Dashboard header card: **25dp**
- QuoteCard: **15dp**
- Photo thumbnails (PhotosScreen): **8dp**
- Modal containers (ConfirmModal, RatingModal): **24dp**

### Buttons
- Dashboard action buttons: **30dp** corner radius, pill-style (not full width)
- Buttons are `OutlinedButton` with transparent background and `Colors.greenAccent` border

### Modals
- Corner radius: **24dp** (ConfirmModal, RatingModal, CustomCalendar popup)
- ConfirmModal and RatingModal use `Dialog` composable (no system slide-up animation)
- CustomCalendar uses a `Popup` with spring/tween scale + fade entrance animation
