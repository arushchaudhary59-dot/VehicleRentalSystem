package vrs.ui.comp;

import vrs.App;
import vrs.ui.Theme;

/** Sun / moon button that switches between dark and light mode (the choice is remembered). */
public final class ThemeToggle {
    private ThemeToggle() {}

    public static Btn create() {
        boolean light = Theme.isLight();
        Btn b = new Btn("", Btn.Kind.GHOST, light ? "moon" : "sun").small().tip(light ? "Switch to dark mode" : "Switch to light mode");
        b.addActionListener(e -> App.toggleTheme());
        return b;
    }
}
