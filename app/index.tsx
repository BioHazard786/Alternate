import { ContactItem } from "@/components/contact-item";
import { EmptyContactsList } from "@/components/empty-contactsList";
import { ErrorState } from "@/components/error-state";
import CustomNavigationBar from "@/components/navigation-bar";
import { SectionHeader } from "@/components/section-header";
import { getSectionedContacts } from "@/lib/avatar-utils";
import { requestAndroidPermissions } from "@/lib/permissions";
import { ListItem } from "@/lib/types";
import { shareContact } from "@/lib/vcf-utils";
import useContactStore from "@/store/contactStore";
import useSelectedContactStore from "@/store/selectedContactStore";
import { FlashList } from "@shopify/flash-list";
import * as Clipboard from "expo-clipboard";
import { router, Stack } from "expo-router";
import React, { useEffect, useState } from "react";
import { BackHandler, StyleSheet, ToastAndroid, View } from "react-native";
import {
  Button,
  Dialog,
  FAB,
  Portal,
  Snackbar,
  Text,
  useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export default function ContactsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const contacts = useContactStore.use.contacts();
  const isRefreshing = useContactStore.use.isLoading();
  const error = useContactStore.use.fetchContactError();
  const fetchContacts = useContactStore.use.fetchContacts();
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [visible, setVisible] = useState(false);
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const selectionMode = useSelectedContactStore.use.selectionMode();
  const toggleSelectionMode = useSelectedContactStore.use.toggleSelectionMode();
  const selectedContacts = useSelectedContactStore.use.selectedContacts();
  const clearSelection = useSelectedContactStore.use.clearSelection();
  const deleteMultipleContacts = useContactStore.use.deleteMultipleContacts();
  const clearDeleteMultipleContactsError =
    useContactStore.use.clearDeleteMultipleError();
  const deleteMultipleContactsError =
    useContactStore.use.deleteMultipleContactsError();

  // Cancel selection mode on Android back button
  useEffect(() => {
    if (!selectionMode) return;
    const onBackPress = () => {
      if (selectionMode) {
        toggleSelectionMode(false);
        clearSelection();
        return true; // prevent default
      }
      return true;
    };
    const backHandler = BackHandler.addEventListener(
      "hardwareBackPress",
      onBackPress
    );
    return () => {
      backHandler.remove();
    };
  }, [selectionMode, toggleSelectionMode]);

  // Only show error state if there's an error
  if (error) {
    return (
      <ErrorState
        error={error}
        onRetry={fetchContacts}
        permissionGranted={permissionGranted}
        onRequestPermissions={requestAndroidPermissions}
      />
    );
  }

  // Create sectioned data for FlashList
  const sectionedData = getSectionedContacts(contacts);

  const onDismissSnackBar = () => setVisible(false);

  const showDialog = () => setOpen(true);

  const hideDialog = () => setOpen(false);

  const copyContactsToClipboard = async () => {
    const contactStrings = selectedContacts
      .map((contact) => {
        const fullName = [contact.prefix, contact.name, contact.suffix]
          .filter(Boolean)
          .join(" ");
        const fields = [
          { label: "name", value: fullName },
          {
            label: "number",
            value: contact.fullPhoneNumber
              ? `+${contact.fullPhoneNumber}`
              : undefined,
          },
          { label: "email", value: contact.email },
          { label: "appointment", value: contact.appointment },
          { label: "location", value: contact.location },
          { label: "notes", value: contact.notes },
          { label: "nickname", value: contact.nickname },
          { label: "website", value: contact.website },
          { label: "birthday", value: contact.birthday },
          { label: "labels", value: contact.labels },
          { label: "prefix", value: contact.prefix },
          { label: "suffix", value: contact.suffix },
        ];
        return fields
          .filter((f) => f.value && String(f.value).trim() !== "")
          .map((f) => `${f.label} - ${f.value}`)
          .join("\n");
      })
      .join("\n\n");

    await Clipboard.setStringAsync(contactStrings);
    ToastAndroid.show(
      `${selectedContacts.length} contact${
        selectedContacts.length === 1 ? "" : "s"
      } copied to clipboard`,
      ToastAndroid.SHORT
    );
    clearSelection();
    toggleSelectionMode(false);
  };

  const handleDelete = async () => {
    hideDialog();
    setIsDeleting(true);
    const success = await deleteMultipleContacts(
      selectedContacts.map((c) => c.fullPhoneNumber)
    );
    setIsDeleting(false);

    if (success) {
      clearSelection();
      toggleSelectionMode(false);
    } else {
      setVisible(true);
    }
  };

  // Flatten sectioned data into single array with headers and items
  const listData: ListItem[] = sectionedData.flatMap((section) => [
    { type: "header" as const, letter: section.title },
    ...section.data.map(
      (contact, index): ListItem => ({
        type: "item" as const,
        contact,
        index: index,
        isFirst: index === 0,
        isLast: index === section.data.length - 1,
      })
    ),
  ]);

  // Render function for FlashList
  const renderItem = ({ item }: { item: ListItem }) => {
    if (item.type === "header") {
      return <SectionHeader title={item.letter} />;
    } else {
      return (
        <ContactItem
          contact={item.contact}
          index={item.index}
          isFirst={item.isFirst}
          isLast={item.isLast}
        />
      );
    }
  };

  useEffect(() => {
    const initializePermission = async () => {
      try {
        await requestAndroidPermissions();
        setPermissionGranted(true);
      } catch (error) {
        console.error("Error during asking permissions:", error);
        setPermissionGranted(false);
      }
    };

    initializePermission();
  }, []);

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      {selectionMode ? (
        <Stack.Screen
          name="index"
          options={{
            title: `${selectedContacts.length} selected`,
            header: (props) => (
              <CustomNavigationBar
                {...props}
                mode="small"
                elevated={true}
                actions={[
                  {
                    icon: "content-copy",
                    onPress: copyContactsToClipboard,
                    disabled: isDeleting,
                  },
                  {
                    icon: "delete-outline",
                    onPress: showDialog,
                    disabled: isDeleting,
                  },
                ]}
              />
            ),
          }}
        />
      ) : (
        <Stack.Screen
          name="index"
          options={{
            title: "Contacts",
            header: (props) => (
              <CustomNavigationBar
                actions={[
                  {
                    icon: "magnify",
                    onPress: () => router.push("/search"),
                  },
                  {
                    icon: "cog-outline",
                    onPress: () => router.push("/settings"),
                  },
                ]}
                {...props}
              />
            ),
          }}
        />
      )}

      <View style={styles.content}>
        <FlashList
          data={listData}
          renderItem={renderItem}
          estimatedItemSize={60}
          keyExtractor={(item) => {
            if (item.type === "header") {
              return `header-${item.letter}`;
            } else {
              return `contact-${item.contact.fullPhoneNumber}`;
            }
          }}
          extraData={selectedContacts}
          contentContainerStyle={styles.listContainer}
          ListEmptyComponent={<EmptyContactsList />}
          onRefresh={fetchContacts}
          refreshing={isRefreshing}
        />
      </View>

      {selectionMode ? (
        <FAB
          icon="share-variant-outline"
          customSize={60}
          style={[styles.fab, { bottom: insets.bottom + 16 }]}
          label="Share"
          mode="flat"
          onPress={async () => {
            await shareContact(selectedContacts);
            clearSelection();
            toggleSelectionMode(false);
          }}
        />
      ) : (
        <FAB
          icon="plus"
          customSize={60}
          style={[styles.fab, { bottom: insets.bottom + 16 }]}
          onPress={() => router.push("/new-contact")}
          mode="flat"
        />
      )}
      <Portal>
        <Snackbar
          visible={visible}
          onDismiss={onDismissSnackBar}
          action={{
            label: "Dismiss",
            onPress: () => {
              clearDeleteMultipleContactsError();
              onDismissSnackBar();
            },
          }}
        >
          {deleteMultipleContactsError || "An error occurred"}
        </Snackbar>

        <Dialog visible={open} onDismiss={hideDialog}>
          <Dialog.Title>Delete contacts?</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">
              These contacts will be permanently deleted from your device
            </Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={hideDialog}>Cancel</Button>
            <Button onPress={handleDelete} textColor={theme.colors.error}>
              Delete
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
  },
  listContainer: {
    padding: 16,
  },
  fab: {
    position: "absolute",
    right: 20,
  },
});
