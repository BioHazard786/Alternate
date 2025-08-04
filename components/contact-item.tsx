import { Contact } from "@/lib/types";
import { getFormattedName, getFormattedPhoneNumber } from "@/lib/utils";
import useSelectedContactStore from "@/store/selectedContactStore";
import { router } from "expo-router";
import React from "react";
import { Image, StyleSheet, Text, View } from "react-native";
import { Avatar, Icon, TouchableRipple, useTheme } from "react-native-paper";
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
  const selectionMode = useSelectedContactStore.use.selectionMode();
  const toogleSelectionMode = useSelectedContactStore.use.toggleSelectionMode();
  const selectedContacts = useSelectedContactStore.use.selectedContacts();
  const setSelectContact = useSelectedContactStore.use.selectContact();
  const clearSelection = useSelectedContactStore.use.clearSelection();
  const [avatarBackgroundColor, avatarTextColor] = getAvatarColor(
    letter,
    theme.dark,
    index
  );

  const isSelected = selectedContacts.some(
    (c) => c.fullPhoneNumber === contact.fullPhoneNumber
  );

  const handlePress = () => {
    if (selectionMode) {
      if (isSelected && selectedContacts.length === 1) {
        toogleSelectionMode(false);
        clearSelection();
      } else {
        setSelectContact(contact);
      }
    } else {
      router.push({
        pathname: "/preview-contact",
        params: { contact: JSON.stringify(contact), index },
      });
    }
  };

  const handleLongPress = () => {
    if (selectedContacts.length === 0) toogleSelectionMode(true);
    if (isSelected && selectedContacts.length === 1) {
      toogleSelectionMode(false);
      clearSelection();
    } else {
      setSelectContact(contact);
    }
  };

  return (
    <TouchableRipple
      style={[
        styles.touchableContainer,
        {
          backgroundColor: isSelected
            ? theme.colors.elevation.level4
            : theme.colors.elevation.level1,
        },
        isFirst && styles.firstItem,
        isLast && styles.lastItem,
        !isLast && styles.itemWithGap,
      ]}
      onPress={handlePress}
      onLongPress={handleLongPress}
      borderless={true}
    >
      <View style={styles.container}>
        {isSelected ? (
          <View
            style={[
              styles.selectedContainer,
              { backgroundColor: theme.colors.primaryContainer },
            ]}
          >
            <Icon
              size={25}
              source="check"
              color={theme.colors.onPrimaryContainer}
            />
          </View>
        ) : contact.photo ? (
          <Image source={{ uri: contact.photo }} style={styles.avatarImage} />
        ) : (
          <Avatar.Text
            size={45}
            label={letter}
            labelStyle={{ color: avatarTextColor, fontSize: 24 }}
            style={{ backgroundColor: avatarBackgroundColor }}
          />
        )}
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
  selectedContainer: {
    borderRadius: 50,
    width: 45,
    height: 45,
    justifyContent: "center",
    alignItems: "center",
  },
  avatarImage: {
    width: 45,
    height: 45,
    borderRadius: 22.5,
  },
});
