import useThemeStore from "@/store/themeStore";
import { useMaterial3Theme } from "@pchmn/expo-material3-theme";
import {
  DarkTheme as NavigationDarkTheme,
  DefaultTheme as NavigationDefaultTheme,
} from "@react-navigation/native";
import merge from "deepmerge";
import { useColorScheme } from "react-native";
import {
  adaptNavigationTheme,
  MD3DarkTheme,
  MD3LightTheme,
} from "react-native-paper";

export function useTheme() {
  const themeMode = useThemeStore.use.themeMode();
  const systemColorScheme = useColorScheme();
  const { theme } = useMaterial3Theme({ fallbackSourceColor: "#663399" });

  // Determine the effective color scheme
  const colorScheme = themeMode === "system" ? systemColorScheme : themeMode;

  const customDarkTheme = { ...MD3DarkTheme, colors: theme.dark };
  const customLightTheme = { ...MD3LightTheme, colors: theme.light };

  const { LightTheme, DarkTheme } = adaptNavigationTheme({
    reactNavigationLight: NavigationDefaultTheme,
    reactNavigationDark: NavigationDarkTheme,
  });

  const CombinedDefaultTheme = merge(LightTheme, customLightTheme);
  const CombinedDarkTheme = merge(DarkTheme, customDarkTheme);

  const paperTheme =
    colorScheme === "dark" ? CombinedDarkTheme : CombinedDefaultTheme;

  return paperTheme;
}
