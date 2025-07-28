import { additionalFields } from "@/constants/AdditionalFields";
import { StyleSheet, View } from "react-native";
import ActionSheet, {
  ScrollView,
  SheetManager,
  SheetProps,
} from "react-native-actions-sheet";
import { List, Text, useTheme } from "react-native-paper";

function AdditionalFieldSheet(props: SheetProps<"additional-field-sheet">) {
  const theme = useTheme();
  const { visibleFields, setVisibleFields } = props.payload!;

  const toggleField = (fieldKey: string) => {
    const newVisibleFields = new Set(visibleFields);
    if (newVisibleFields.has(fieldKey)) {
      newVisibleFields.delete(fieldKey);
    } else {
      newVisibleFields.add(fieldKey);
    }
    setVisibleFields(newVisibleFields);
    SheetManager.hide("additional-field-sheet");
  };

  return (
    <ActionSheet
      gestureEnabled
      id={props.sheetId}
      containerStyle={{
        ...styles.container,
        backgroundColor: theme.colors.elevation.level2,
      }}
      snapPoints={[60]}
      indicatorStyle={{
        backgroundColor: theme.colors.onSurfaceVariant,
        width: 30,
        height: 4,
        marginTop: 12,
      }}
      headerAlwaysVisible
    >
      <View
        style={[
          styles.bottomSheetContainer,
          { backgroundColor: theme.colors.elevation.level2 },
        ]}
      >
        <View
          style={[
            styles.header,
            {
              borderBottomColor: theme.colors.outline,
              backgroundColor: theme.colors.elevation.level2,
            },
          ]}
        >
          <Text
            variant="headlineSmall"
            style={[styles.title, { color: theme.colors.onSurface }]}
          >
            Choose fields to add
          </Text>
        </View>
        <ScrollView style={styles.bottomSheetContent}>
          {additionalFields
            .filter((field) => !visibleFields.has(field.key))
            .map((field, index) => (
              <List.Item
                key={field.key}
                title={field.label}
                left={() => <List.Icon icon={field.icon} />}
                onPress={() => toggleField(field.key)}
                style={styles.list}
              />
            ))}
        </ScrollView>
      </View>
    </ActionSheet>
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
  bottomSheetContent: {
    flex: 1,
    paddingVertical: 16,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 16,
    paddingHorizontal: 24,
  },
  title: {
    fontWeight: "600",
  },
  list: {
    paddingHorizontal: 16,
  },
});

export default AdditionalFieldSheet;
