import { MaterialSwitchListItem } from "@/components/material-switch-list-item";
import CallerIdModule from "@/modules/caller-id";
import useContactStore from "@/store/contactStore";
import useThemeStore from "@/store/themeStore";
import React from "react";
import { StyleSheet, View } from "react-native";
import {
  Button,
  Card,
  Dialog,
  List,
  Portal,
  SegmentedButtons,
  Text,
  useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export default function SettingsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const themeMode = useThemeStore.use.themeMode();
  const setThemeMode = useThemeStore.use.setThemeMode();
  const [open, setOpen] = React.useState(false);
  const [openDialog, setOpenDialog] = React.useState(false);
  const [dialogState, setDialogState] = React.useState({
    title: "",
    content: "",
  });

  const [popupState, setPopupState] = React.useState(
    CallerIdModule.getShowPopup()
  );

  // VCF import/export functionality
  const importContacts = useContactStore.use.importContacts();
  const exportContacts = useContactStore.use.exportContacts();
  const isImporting = useContactStore.use.isImporting();
  const isExporting = useContactStore.use.isExporting();
  const importError = useContactStore.use.importError();
  const exportError = useContactStore.use.exportError();
  const clearImportError = useContactStore.use.clearImportError();
  const clearExportError = useContactStore.use.clearExportError();
  const contacts = useContactStore.use.contacts();

  const showDialog = (title: string, content: string) => {
    setOpenDialog(true);
    setDialogState({ title, content });
  };

  const hideDialog = () => {
    setOpenDialog(false);
    setDialogState({ title: "", content: "" });
  };

  const handleShowPopupToggle = (value: boolean) => {
    const success = CallerIdModule.setShowPopup(value);
    if (!success) {
      showDialog("Error", "Failed to update popup setting. Please try again");
      // Alert.alert("Error", "Failed to update popup setting");
    }
    setPopupState(value);
  };

  const handleImportContacts = async () => {
    clearImportError();
    const success = await importContacts();
    if (success) {
      showDialog(
        "Import Successful",
        `${success} Contacts have been imported successfully from the VCF file`
      );
      // Alert.alert(
      //   "Import Successful",
      //   `${success} Contacts have been imported successfully from the VCF file.`
      // );
    } else if (importError) {
      // Alert.alert("Import Failed", importError);
      showDialog("Import Failed", importError);
    }
  };

  const handleExportContacts = async () => {
    clearExportError();
    if (contacts.length === 0) {
      // Alert.alert("No Contacts", "You don't have any contacts to export.");
      showDialog("No Contacts", "You don't have any contacts to export.");
      return;
    }

    const success = await exportContacts();
    if (success) {
      // Alert.alert(
      //   "Export Successful",
      //   `${contacts.length} contacts have been exported to a VCF file.`
      // );
      showDialog(
        "Export Successful",
        `${contacts.length} contacts have been exported to a VCF file.`
      );
    } else if (exportError) {
      // Alert.alert("Export Failed", exportError);
      showDialog("Export Failed", exportError);
    }
  };

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      <MaterialSwitchListItem
        title={"Show Caller ID Popup"}
        titleStyle={{ fontSize: 18 }}
        listStyle={[
          styles.listItem,
          {
            backgroundColor: theme.colors.elevation.level1,
            marginBottom: 2,
            borderBottomLeftRadius: 5,
            borderBottomRightRadius: 5,
          },
        ]}
        switchOnIcon={"check"}
        selected={popupState}
        onPress={() => handleShowPopupToggle(!popupState)}
      />
      {/* <MaterialSwitchListItem
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
        listStyle={[
          styles.listItem,
          {
            backgroundColor: theme.colors.elevation.level1,
            borderTopLeftRadius: 5,
            borderTopRightRadius: 5,
          },
        ]}
        title={"Dark mode"}
      /> */}
      <View
        style={[
          styles.listItem,
          {
            backgroundColor: theme.colors.elevation.level1,
            paddingVertical: 5,
            borderTopLeftRadius: 5,
            borderTopRightRadius: 5,
          },
        ]}
      >
        <List.Accordion
          title="Theme"
          titleStyle={{ fontSize: 18 }}
          expanded={open}
          onPress={() => setOpen((state) => !state)}
          background={{ color: "transparent" }}
          style={{
            backgroundColor: theme.colors.elevation.level1,
          }}
        >
          <View
            style={[
              styles.themeContainer,
              { backgroundColor: theme.colors.elevation.level1 },
            ]}
          >
            <SegmentedButtons
              value={themeMode}
              onValueChange={setThemeMode}
              buttons={[
                {
                  value: "light",
                  label: "Light",
                  icon: "white-balance-sunny",
                  showSelectedCheck: true,
                  style: [
                    styles.segmentedButton,
                    styles.segmentedButtonLeft,
                    {
                      backgroundColor:
                        themeMode === "light"
                          ? theme.colors.secondaryContainer
                          : theme.colors.surfaceVariant,
                    },
                  ],
                },
                {
                  value: "dark",
                  label: "Dark",
                  icon: "moon-waning-crescent",
                  showSelectedCheck: true,
                  style: [
                    styles.segmentedButton,
                    styles.segmentedButtonMiddle,
                    {
                      backgroundColor:
                        themeMode === "dark"
                          ? theme.colors.secondaryContainer
                          : theme.colors.surfaceVariant,
                    },
                  ],
                },
                {
                  value: "system",
                  label: "System",
                  icon: "laptop",
                  showSelectedCheck: true,
                  style: [
                    styles.segmentedButton,
                    styles.segmentedButtonRight,
                    {
                      backgroundColor:
                        themeMode === "system"
                          ? theme.colors.secondaryContainer
                          : theme.colors.surfaceVariant,
                    },
                  ],
                },
              ]}
            />
          </View>
        </List.Accordion>
      </View>

      {/* VCF Import/Export Section */}
      <Card
        style={[
          styles.vcfCard,
          { backgroundColor: theme.colors.elevation.level1 },
        ]}
        mode="contained"
      >
        <Card.Content>
          <Text style={[styles.vcfTitle, { color: theme.colors.onSurface }]}>
            Contacts Backup
          </Text>
          <Text variant="bodyMedium" style={styles.vcfDescription}>
            Import and export your contacts as VCF files
          </Text>

          <View style={styles.vcfButtonContainer}>
            <Button
              mode="outlined"
              onPress={handleImportContacts}
              loading={isImporting}
              disabled={isImporting || isExporting}
              icon="download"
              style={styles.vcfButton}
            >
              Import VCF
            </Button>

            <Button
              mode="contained"
              onPress={handleExportContacts}
              loading={isExporting}
              disabled={isImporting || isExporting || contacts.length === 0}
              icon="upload"
              style={styles.vcfButton}
            >
              Export VCF
            </Button>
          </View>

          {contacts.length > 0 && (
            <Text variant="bodySmall" style={styles.contactCount}>
              {contacts.length} contact{contacts.length !== 1 ? "s" : ""}{" "}
              available
            </Text>
          )}
        </Card.Content>
      </Card>

      {/* Alert Dialog */}
      <Portal>
        <Dialog visible={openDialog} onDismiss={hideDialog}>
          <Dialog.Title>{dialogState.title}</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">{dialogState.content}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={hideDialog}>Cancel</Button>
            <Button onPress={hideDialog}>Ok</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 20,
  },
  listItem: {
    width: "100%",
    padding: 16,
    borderRadius: 16,
    paddingVertical: 15,
  },
  touchableContainer: {
    marginTop: 24,
    paddingHorizontal: 20,
    borderRadius: 16,
  },
  textContainer: {
    flex: 1,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 10,
  },
  title: {
    fontSize: 18,
  },
  vcfCard: {
    marginTop: 24,
    borderRadius: 16,
  },
  vcfTitle: {
    marginBottom: 8,
    fontWeight: "600",
    fontSize: 18,
  },
  vcfDescription: {
    marginBottom: 16,
    opacity: 0.8,
  },
  vcfButtonContainer: {
    flexDirection: "row",
    gap: 12,
    marginVertical: 12,
  },
  vcfButton: {
    flex: 1,
  },
  contactCount: {
    textAlign: "center",
    opacity: 0.7,
  },
  themeContainer: {
    padding: 20,
    borderRadius: 16,
    borderTopLeftRadius: 0,
    borderTopRightRadius: 0,
  },
  segmentedButton: {
    borderStyle: undefined,
    borderWidth: 0,
  },
  segmentedButtonLeft: {
    borderTopRightRadius: 3,
    borderBottomRightRadius: 3,
    borderRadius: 50,
  },
  segmentedButtonMiddle: {
    marginHorizontal: 2,
    borderRadius: 3,
  },
  segmentedButtonRight: {
    borderTopLeftRadius: 3,
    borderBottomLeftRadius: 3,
    borderRadius: 50,
  },
});
