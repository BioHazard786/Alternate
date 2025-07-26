import { ContactItem } from "@/components/contact-item";
import { EmptyContactsList } from "@/components/empty-contactsList";
import { ErrorState } from "@/components/error-state";
import { SectionHeader } from "@/components/section-header";
import { getSectionedContacts } from "@/lib/avatar-utils";
import { requestAndroidPermissions } from "@/lib/permissions";
import { ListItem } from "@/lib/types";
import useContactStore from "@/store/contactStore";
import { FlashList } from "@shopify/flash-list";
import { router } from "expo-router";
import React, { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { FAB } from "react-native-paper";

export default function ContactsScreen() {
  const contacts = useContactStore.use.contacts();
  const isRefreshing = useContactStore.use.isLoading();
  const error = useContactStore.use.fetchContactError();
  const fetchContacts = useContactStore.use.fetchContacts();
  const [permissionGranted, setPermissionGranted] = useState(false);

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

  // Create sectioned data for LegendList
  const sectionedData = getSectionedContacts(contacts);

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

  // Render function for LegendList
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
    <View style={styles.container}>
      <View style={styles.content}>
        <FlashList
          data={listData}
          renderItem={renderItem}
          estimatedItemSize={60}
          keyExtractor={(item) => {
            if (item.type === "header") {
              return `header-${item.letter}`;
            } else {
              return `contact-${item.contact.phoneNumber}`;
            }
          }}
          contentContainerStyle={styles.listContainer}
          ListEmptyComponent={<EmptyContactsList />}
          onRefresh={fetchContacts}
          refreshing={isRefreshing}
        />
      </View>

      <FAB
        icon="plus"
        customSize={60}
        style={styles.fab}
        onPress={() => router.push("/new-contact")}
      />
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
    bottom: 20,
  },
});
