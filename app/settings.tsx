import { MaterialSwitchListItem } from "@/components/material-switch-list-item";
import { useTheme } from "@/hooks/useTheme";
import CallerIdModule from "@/modules/caller-id";
import useThemeStore from "@/store/themeStore";
import Ionicons from "@expo/vector-icons/Ionicons";
import React from "react";
import { Alert, StyleSheet, View } from "react-native";

export default function SettingsScreen() {
  const theme = useTheme();

  const setThemeMode = useThemeStore.use.setThemeMode();
  const [popupState, setPopupState] = React.useState(
    CallerIdModule.getShowPopup()
  );

  const handleShowPopupToggle = (value: boolean) => {
    const success = CallerIdModule.setShowPopup(value);
    if (!success) {
      Alert.alert("Error", "Failed to update popup setting");
    }
    setPopupState(value);
  };

  return (
    <View style={styles.container}>
      <MaterialSwitchListItem
        title={"Show Caller ID Popup"}
        titleStyle={{ fontSize: 18 }}
        listStyle={styles.listItem}
        switchOnIcon={"check"}
        selected={popupState}
        onPress={() => handleShowPopupToggle(!popupState)}
      />
      <MaterialSwitchListItem
        fluid={false}
        titleStyle={{ fontSize: 18 }}
        switchOnIcon={() => (
          <Ionicons
            name="moon"
            size={14}
            color={theme.colors.onPrimaryContainer}
          />
        )}
        selected={theme.dark}
        onPress={() => setThemeMode(theme.dark ? "light" : "dark")}
        listStyle={styles.listItem}
        title={"Dark mode"}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingVertical: 16,
  },
  listItem: {
    width: "100%",
    padding: 16,
  },
});
