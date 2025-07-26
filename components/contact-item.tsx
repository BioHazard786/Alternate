import { Contact } from "@/lib/types";
import { getFormattedName, getFormattedPhoneNumber } from "@/lib/utils";
import { router } from "expo-router";
import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar, TouchableRipple, useTheme } from "react-native-paper";
import { getAvatarColor } from "../lib/avatar-utils";

interface ContactItemProps {
  contact: Contact;
  index: number;
  isFirst?: boolean;
  isLast?: boolean;
}

export const ContactItem: React.FC<ContactItemProps> = ({
  contact,
  index,
  isFirst = false,
  isLast = false,
}) => {
  const theme = useTheme();
  const letter = contact.name.charAt(0).toUpperCase();
  const [avatarBackgroundColor, avatarTextColor] = getAvatarColor(
    letter,
    theme.dark,
    index
  );

  const handlePress = () => {
    router.push({
      pathname: "/preview-contact",
      params: { contact: JSON.stringify(contact), index: index },
    });
  };

  return (
    <TouchableRipple
      style={[
        styles.touchableContainer,
        { backgroundColor: theme.colors.elevation.level1 },
        isFirst && styles.firstItem,
        isLast && styles.lastItem,
        !isLast && styles.itemWithGap,
      ]}
      onPress={handlePress}
      borderless={true}
    >
      <View style={styles.container}>
        <Avatar.Text
          size={45}
          label={letter}
          labelStyle={{ color: avatarTextColor, fontSize: 24 }}
          style={{ backgroundColor: avatarBackgroundColor }}
        />
        <View style={styles.textContainer}>
          <Text style={[styles.title, { color: theme.colors.onSurface }]}>
            {getFormattedName(contact)}
          </Text>
          <Text style={[styles.description, { color: theme.colors.outline }]}>
            +{getFormattedPhoneNumber(contact)}
          </Text>
        </View>
      </View>
    </TouchableRipple>
  );
};

const styles = StyleSheet.create({
  touchableContainer: {
    paddingHorizontal: 20,
    paddingVertical: 15,
    borderRadius: 5,
  },
  container: {
    flexDirection: "row",
    alignItems: "center",
  },
  firstItem: {
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
  },
  lastItem: {
    borderBottomLeftRadius: 16,
    borderBottomRightRadius: 16,
    marginBottom: 16,
  },
  itemWithGap: {
    marginBottom: 2,
  },
  textContainer: {
    flex: 1,
    marginLeft: 16,
  },
  title: {
    fontSize: 18,
  },
  description: {
    fontSize: 14,
    marginTop: 2,
  },
});
