import { CountrySelectorProvider } from "@/components/country-selector-provider";
import CustomNavigationBar from "@/components/navigation-bar";
import { useTheme } from "@/hooks/useTheme";
import useContactStore from "@/store/contactStore";
import { ThemeProvider } from "@react-navigation/native";
import { router, SplashScreen, Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { PaperProvider } from "react-native-paper";

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const paperTheme = useTheme();
  const fetchContacts = useContactStore.use.fetchContacts();
  const [isReady, setIsReady] = useState(false);

  // Start fetching contacts during app initialization (splash screen time)
  useEffect(() => {
    const initializeApp = async () => {
      try {
        await fetchContacts();
      } catch (error) {
        console.error("Error during app initialization:", error);
      } finally {
        setIsReady(true);
        await SplashScreen.hideAsync();
      }
    };

    initializeApp();
  }, []);

  if (!isReady) {
    return null; // Keep showing splash screen
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <PaperProvider theme={paperTheme}>
        <ThemeProvider value={paperTheme}>
          <CountrySelectorProvider>
            <Stack
              screenOptions={{
                header: (props) => <CustomNavigationBar {...props} />,
              }}
            >
              <Stack.Screen
                name="index"
                options={{
                  title: "Contacts",
                  header: (props) => (
                    <CustomNavigationBar
                      action={{
                        icon: "cog",
                        onPress: () => router.push("/settings"),
                      }}
                      {...props}
                    />
                  ),
                }}
              />
              <Stack.Screen
                name="new-contact"
                options={{
                  title: "Add Contact",
                  header: (props) => (
                    <CustomNavigationBar {...props} mode="small" />
                  ),
                }}
              />
              <Stack.Screen
                name="edit-contact"
                options={{
                  title: "Edit Contact",
                  header: (props) => (
                    <CustomNavigationBar
                      {...props}
                      mode="small"
                      popToTop={true}
                    />
                  ),
                }}
              />
              <Stack.Screen
                name="settings"
                options={{
                  title: "Settings",
                }}
              />
            </Stack>
          </CountrySelectorProvider>
        </ThemeProvider>
        <StatusBar style={paperTheme.dark ? "light" : "dark"} />
      </PaperProvider>
    </GestureHandlerRootView>
  );
}
