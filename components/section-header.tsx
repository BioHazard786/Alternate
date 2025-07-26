import React from "react";
import { StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";

interface SectionHeaderProps {
  title: string;
}

export const SectionHeader: React.FC<SectionHeaderProps> = ({ title }) => {
  const theme = useTheme();
  return (
    <View style={styles.sectionHeader}>
      <Text variant="labelLarge" style={{ color: theme.colors.primary }}>
        {title}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  sectionHeader: {
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
});
