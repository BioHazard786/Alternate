import useContactStore from "@/store/contactStore";
import React, { useState } from "react";
import { Alert } from "react-native";
import { IconButton, Menu } from "react-native-paper";

interface VCFActionsMenuProps {
  iconColor?: string;
}

export function VCFActionsMenu({ iconColor }: VCFActionsMenuProps) {
  const [visible, setVisible] = useState(false);

  const importContacts = useContactStore.use.importContacts();
  const exportContacts = useContactStore.use.exportContacts();
  const isImporting = useContactStore.use.isImporting();
  const isExporting = useContactStore.use.isExporting();
  const importError = useContactStore.use.importError();
  const exportError = useContactStore.use.exportError();
  const clearImportError = useContactStore.use.clearImportError();
  const clearExportError = useContactStore.use.clearExportError();
  const contacts = useContactStore.use.contacts();

  const openMenu = () => setVisible(true);
  const closeMenu = () => setVisible(false);

  const handleImportContacts = async () => {
    closeMenu();
    clearImportError();
    const success = await importContacts();
    if (success) {
      Alert.alert(
        "Import Successful",
        "Contacts have been imported successfully from the VCF file."
      );
    } else if (importError) {
      Alert.alert("Import Failed", importError);
    }
  };

  const handleExportContacts = async () => {
    closeMenu();
    clearExportError();
    if (contacts.length === 0) {
      Alert.alert("No Contacts", "You don't have any contacts to export.");
      return;
    }

    const success = await exportContacts();
    if (success) {
      Alert.alert(
        "Export Successful",
        `${contacts.length} contacts have been exported to a VCF file.`
      );
    } else if (exportError) {
      Alert.alert("Export Failed", exportError);
    }
  };

  const isLoading = isImporting || isExporting;

  return (
    <Menu
      visible={visible}
      onDismiss={closeMenu}
      anchor={
        <IconButton
          icon="dots-vertical"
          onPress={openMenu}
          iconColor={iconColor}
          disabled={isLoading}
        />
      }
    >
      <Menu.Item
        onPress={handleImportContacts}
        title="Import VCF"
        leadingIcon="download"
        disabled={isLoading}
      />
      <Menu.Item
        onPress={handleExportContacts}
        title="Export VCF"
        leadingIcon="upload"
        disabled={isLoading || contacts.length === 0}
      />
    </Menu>
  );
}
