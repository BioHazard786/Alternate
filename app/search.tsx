import { ContactItem } from "@/components/contact-item";
import { EmptyContactsList } from "@/components/empty-contactsList";
import { SectionHeader } from "@/components/section-header";
import useDebounce from "@/hooks/useDebounce";
import { getSectionedContacts } from "@/lib/avatar-utils";
import { Contact, ListItem } from "@/lib/types";
import useContactStore from "@/store/contactStore";
import { NativeStackHeaderProps } from "@react-navigation/native-stack";
import { FlashList } from "@shopify/flash-list";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { Platform, StyleSheet, View } from "react-native";
import { Searchbar, useTheme } from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export default function SearchScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const [searchResults, setSearchResults] = useState<Contact[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const contacts = useContactStore.use.contacts();
  const [debouncedSearchTerm, loading] = useDebounce(searchQuery, 300);

  // Create sectioned data for FlashList
  const sectionedData = getSectionedContacts(searchResults);

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
    if (debouncedSearchTerm) {
      const lowerTerm = debouncedSearchTerm.toLowerCase();
      const results = contacts.filter((contact) => {
        // Build full name from prefix, name, suffix
        const fullName = [contact.prefix, contact.name, contact.suffix]
          .filter(Boolean)
          .join(" ")
          .toLowerCase();

        // Check other fields
        const location = contact.location?.toLowerCase() || "";
        const appointment = contact.appointment?.toLowerCase() || "";
        const nickname = contact.nickname?.toLowerCase() || "";
        const email = contact.email?.toLowerCase() || "";

        return (
          fullName.includes(lowerTerm) ||
          location.includes(lowerTerm) ||
          appointment.includes(lowerTerm) ||
          nickname.includes(lowerTerm) ||
          email.includes(lowerTerm)
        );
      });
      setSearchResults(results);
    } else {
      setSearchResults([]);
    }
  }, [debouncedSearchTerm, contacts]);

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      {Platform.OS === "ios" && (
        <StatusBar
          style={theme.dark ? "light" : "dark"}
          backgroundColor={theme.colors.elevation.level3}
          animated={true}
        />
      )}
      <Stack.Screen
        name="search"
        options={{
          title: "",
          header: (props) => (
            <CustomSearchNavigationBar
              {...props}
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              loading={loading}
            />
          ),
        }}
      />
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
          contentContainerStyle={styles.listContainer}
          ListEmptyComponent={
            <EmptyContactsList description="Type a name or email to search" />
          }
        />
      </View>
    </View>
  );
}

type CustomNavigationBarProps = NativeStackHeaderProps & {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  loading: boolean;
};

function CustomSearchNavigationBar({
  navigation,
  searchQuery,
  setSearchQuery,
  loading,
}: CustomNavigationBarProps) {
  const insets = useSafeAreaInsets();
  const theme = useTheme();

  return (
    <>
      <View
        style={{
          height: insets.top,
          backgroundColor: theme.colors.elevation.level3,
        }}
      />
      <View>
        <Searchbar
          placeholder="Search"
          onChangeText={(query) => setSearchQuery(query)}
          value={searchQuery}
          mode="view"
          autoFocus={true}
          icon="arrow-left"
          onIconPress={() => navigation.goBack()}
          loading={loading}
        />
      </View>
    </>
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
});
