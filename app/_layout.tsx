import ErrorBoundary from "@/components/ErrorBoundary";
import CustomNavigationBar from "@/components/navigation-bar";
import { useTheme } from "@/hooks/useTheme";
import "@/services/sheets"; // Ensure sheets are registered
import useContactStore from "@/store/contactStore";
import { ThemeProvider } from "@react-navigation/native";
import { SplashScreen, Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { SheetProvider } from "react-native-actions-sheet";
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
      <ErrorBoundary>
        <PaperProvider theme={paperTheme}>
          <ThemeProvider value={paperTheme}>
            <SheetProvider context="global">
              <Stack
                screenOptions={{
                  header: (props) => <CustomNavigationBar {...props} />,
                }}
              >
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
            </SheetProvider>
          </ThemeProvider>
          <StatusBar style={paperTheme.dark ? "light" : "dark"} />
        </PaperProvider>
      </ErrorBoundary>
    </GestureHandlerRootView>
  );
}
