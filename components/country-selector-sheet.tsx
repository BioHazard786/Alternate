import useDebounce from "@/hooks/useDebounce";
import { COUNTRIES } from "@/lib/countries";
import { Country, PhoneNumberData } from "@/lib/types";
import { FlashList } from "@shopify/flash-list";
import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import ActionSheet, {
  SheetManager,
  SheetProps,
} from "react-native-actions-sheet";
import { List, Searchbar, Text, useTheme } from "react-native-paper";

function CountrySelectorSheet(props: SheetProps<"country-selector-sheet">) {
  const theme = useTheme();
  const [searchResults, setSearchResults] = useState<Country[]>(COUNTRIES);
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearchTerm, loading] = useDebounce(searchQuery, 300);
  const { selectedCountry, setSelectedCountry, onChange, currentValue } =
    props.payload!;

  const handleCountrySelect = (
    country: Country,
    onChange: (...event: any[]) => void,
    currentValue: any
  ) => {
    setSelectedCountry(country);

    // Update the form value with new country data
    const phoneData: PhoneNumberData = {
      number: currentValue?.number || "",
      countryCode: country.code,
      dialCode: country.dialCode,
    };
    onChange(phoneData);
    SheetManager.hide("country-selector-sheet");
  };

  const renderCountryItem = ({ item }: { item: Country }) => (
    <List.Item
      title={item.name}
      description={`+${item.dialCode}`}
      titleStyle={{ color: theme.colors.onSurface }}
      descriptionStyle={{ color: theme.colors.onSurfaceVariant }}
      onPress={() => handleCountrySelect(item, onChange, currentValue)}
      style={{
        backgroundColor:
          selectedCountry.code === item.code
            ? theme.colors.secondaryContainer
            : "transparent",
        paddingLeft: 16,
      }}
      left={() => (
        <View
          style={[
            styles.listFlagContainer,
            { backgroundColor: theme.colors.surfaceVariant },
          ]}
        >
          <Text style={[styles.flag, { color: theme.colors.onSurfaceVariant }]}>
            {item.flag}
          </Text>
        </View>
      )}
      right={() =>
        selectedCountry.code === item.code ? (
          <List.Icon icon="check" color={theme.colors.secondary} />
        ) : null
      }
    />
  );

  useEffect(() => {
    if (debouncedSearchTerm) {
      const lowerTerm = debouncedSearchTerm.toLowerCase();
      const results = COUNTRIES.filter((country) => {
        const name = country.name.toLowerCase();
        const code = country.code.toLowerCase();
        const dialCode = country.dialCode.toString();
        return (
          name.includes(lowerTerm) ||
          code.includes(lowerTerm) ||
          dialCode.includes(lowerTerm)
        );
      });
      setSearchResults(results);
    } else {
      setSearchResults(COUNTRIES);
    }
  }, [debouncedSearchTerm]);

  return (
    <ActionSheet
      id={props.sheetId}
      containerStyle={{
        ...styles.container,
        backgroundColor: theme.colors.elevation.level2,
      }}
      snapPoints={[90]}
      indicatorStyle={{
        backgroundColor: theme.colors.onSurfaceVariant,
        width: 30,
        height: 4,
        marginTop: 12,
      }}
      headerAlwaysVisible={true}
    >
      <View
        style={[
          styles.bottomSheetContainer,
          { backgroundColor: theme.colors.elevation.level2 },
        ]}
      >
        <View style={styles.searchContainer}>
          <Searchbar
            placeholder="Search country"
            value={searchQuery}
            onChangeText={setSearchQuery}
            style={{
              backgroundColor: theme.colors.background,
              borderColor: theme.colors.outline,
              borderWidth: 1,
              borderRadius: 50,
            }}
            loading={loading}
          />
        </View>
        <FlashList
          data={searchResults}
          renderItem={renderCountryItem}
          estimatedItemSize={48}
          keyExtractor={(item) => item.code}
          ListEmptyComponent={<EmptyCountryList />}
          contentContainerStyle={{ paddingBottom: 100 }}
        />
      </View>
    </ActionSheet>
  );
}

function EmptyCountryList() {
  const theme = useTheme();
  return (
    <View style={styles.emptyContainer}>
      <Text
        variant="bodyLarge"
        style={{ color: theme.colors.onSurfaceVariant }}
      >
        No countries found
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
  },
  bottomSheetContainer: {
    paddingVertical: 12,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    height: "100%",
  },
  indicatorStyle: {},
  listFlagContainer: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    marginRight: 8,
  },
  flag: {
    fontSize: 18,
  },
  searchContainer: {
    paddingHorizontal: 16,
    paddingVertical: 0,
    paddingBottom: 20,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 16,
  },
});

export default CountrySelectorSheet;
