import Material3Avatar from "@/components/material3-avatar";
import CustomNavigationBar from "@/components/navigation-bar";
import { getAvatarColor } from "@/lib/avatar-utils";
import { Contact } from "@/lib/types";
import {
  getFormattedDate,
  getFormattedName,
  getFormattedPhoneNumber,
} from "@/lib/utils";
import { shareContact } from "@/lib/vcf-utils";
import useContactStore from "@/store/contactStore";
import FontAwesome6 from "@expo/vector-icons/FontAwesome6";
import { router, Stack, useLocalSearchParams } from "expo-router";
import React, { useState } from "react";
import { Linking, ScrollView, StyleSheet, View } from "react-native";
import {
  Button,
  Card,
  Dialog,
  IconButton,
  List,
  Portal,
  Snackbar,
  Text,
  useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export default function PreviewContactScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const { contact: contactParam, index } = useLocalSearchParams();
  const deleteContact = useContactStore.use.deleteContact();
  const deleteError = useContactStore.use.deleteContactError();
  const clearDeleteError = useContactStore.use.clearDeleteError;
  const [visible, setVisible] = useState(false);
  const [open, setOpen] = React.useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  // Parse the contact from JSON string
  const contact: Contact | null = contactParam
    ? JSON.parse(contactParam as string)
    : null;

  const letter = contact?.name?.charAt(0).toUpperCase() || "?";
  const [avatarBackgroundColor, avatarTextColor] = getAvatarColor(
    letter,
    theme.dark,
    Number(index)
  );

  if (!contact) {
    return (
      <View style={styles.container}>
        <Text variant="bodyLarge" style={{ textAlign: "center" }}>
          Contact not found.
        </Text>
      </View>
    );
  }

  const handleCall = () => {
    Linking.openURL(`tel:${contact.fullPhoneNumber}`);
  };

  const handleMessage = () => {
    Linking.openURL(`sms:${contact.fullPhoneNumber}`);
  };

  const handleEmail = () => {
    if (contact.email) {
      Linking.openURL(`mailto:${contact.email}`);
    }
  };

  const onDismissSnackBar = () => setVisible(false);

  const showDialog = () => setOpen(true);

  const hideDialog = () => setOpen(false);

  const handleDelete = async () => {
    hideDialog();
    setIsDeleting(true);
    const success = await deleteContact(contact.fullPhoneNumber!);
    setIsDeleting(false);

    if (success) {
      router.back();
    } else {
      setVisible(true);
    }
  };

  const handleShare = async () => {
    await shareContact([contact]);
  };

  return (
    <ScrollView
      style={[styles.container, { paddingBottom: insets.bottom }]}
      contentContainerStyle={[
        styles.scrollContent,
        { paddingBottom: insets.bottom },
      ]}
    >
      <Stack.Screen
        name="preview-contact"
        options={{
          title: "",
          header: (props) => (
            <CustomNavigationBar
              {...props}
              mode="small"
              actions={[
                {
                  icon: "pencil-outline",
                  onPress: () =>
                    router.push({
                      pathname: "/edit-contact",
                      params: { contact: contactParam, index: index },
                    }),
                },
              ]}
            />
          ),
        }}
      />
      {/* Header with Avatar and Name */}
      <View style={styles.headerContainer}>
        <Material3Avatar
          letter={letter}
          backgroundColor={avatarBackgroundColor}
          textColor={avatarTextColor}
          style={{ marginVertical: 20 }}
        />

        <Text
          variant="headlineMedium"
          style={[styles.name, { color: theme.colors.onSurface }]}
        >
          {getFormattedName(contact)}
        </Text>

        {contact.nickname && (
          <Text
            variant="bodyLarge"
            style={[
              styles.subtitle,
              { color: theme.colors.onSurfaceVariant, marginBottom: 6 },
            ]}
          >
            {contact.nickname}
          </Text>
        )}

        {contact.appointment && (
          <Text
            variant="bodyLarge"
            style={[styles.subtitle, { color: theme.colors.onSurfaceVariant }]}
          >
            {contact.appointment}
          </Text>
        )}
      </View>

      {/* Action Buttons */}
      <View style={styles.actionButtonsContainer}>
        <View style={styles.actionButton}>
          <IconButton
            icon="phone-outline"
            size={30}
            mode="contained"
            onPress={handleCall}
            style={{ width: 60, height: 60, borderRadius: 50 }}
          />
          <Text
            variant="titleMedium"
            style={{
              color: theme.colors.onSurface,
              textAlign: "center",
              fontWeight: "600",
            }}
          >
            Call
          </Text>
        </View>

        <View style={styles.actionButton}>
          <IconButton
            icon="message-outline"
            size={30}
            mode="contained"
            onPress={handleMessage}
            style={{ width: 60, height: 60, borderRadius: 50 }}
          />
          <Text
            variant="titleMedium"
            style={{
              color: theme.colors.onSurface,
              textAlign: "center",
              fontWeight: "600",
            }}
          >
            Message
          </Text>
        </View>

        {contact.email && (
          <View style={styles.actionButton}>
            <IconButton
              icon="email-outline"
              size={30}
              mode="contained"
              onPress={handleEmail}
              style={{ width: 60, height: 60, borderRadius: 50 }}
            />
            <Text
              variant="titleMedium"
              style={{
                color: theme.colors.onSurface,
                textAlign: "center",
                fontWeight: "600",
              }}
            >
              Email
            </Text>
          </View>
        )}
      </View>

      {/* Contact Info Card */}
      <Card
        mode="contained"
        style={[
          styles.infoCard,
          { backgroundColor: theme.colors.elevation.level1 },
        ]}
      >
        <Card.Content>
          <Text
            variant="titleMedium"
            style={[styles.sectionTitle, { color: theme.colors.onSurface }]}
          >
            Contact info
          </Text>

          {/* Phone */}
          <View style={styles.infoRow}>
            <IconButton icon="phone-outline" size={27} />
            <View style={styles.infoTextContainer}>
              <Text
                variant="bodyLarge"
                style={[styles.infoText, { color: theme.colors.onSurface }]}
              >
                +{getFormattedPhoneNumber(contact)}
              </Text>
              <Text
                variant="bodySmall"
                style={[
                  styles.infoSubtitle,
                  { color: theme.colors.onSurfaceVariant },
                ]}
              >
                Mobile
              </Text>
            </View>
          </View>

          {contact.email && (
            <View style={styles.infoRow}>
              <IconButton icon="email-outline" size={27} />
              <View style={styles.infoTextContainer}>
                <Text
                  variant="bodyLarge"
                  style={[styles.infoText, { color: theme.colors.onSurface }]}
                >
                  {contact.email}
                </Text>
              </View>
            </View>
          )}

          {contact.location && (
            <View style={styles.infoRow}>
              <IconButton icon="map-marker-outline" size={27} />
              <View style={styles.infoTextContainer}>
                <Text
                  variant="bodyLarge"
                  style={[styles.infoText, { color: theme.colors.onSurface }]}
                >
                  {contact.location}
                </Text>
              </View>
            </View>
          )}
        </Card.Content>
      </Card>

      {/* Labels Section
      {contact.labels && (
        <Card
          mode="contained"
          style={[
            styles.infoCard,
            { backgroundColor: theme.colors.elevation.level2 },
          ]}
        >
          <Card.Content>
            <Text
              variant="titleMedium"
              style={[styles.sectionTitle, { color: theme.colors.onSurface }]}
            >
              Labels
            </Text>
            <View style={styles.labelsContainer}>
              <IconButton icon="tag" size={20} />
              <Chip mode="outlined" style={styles.labelChip}>
                {contact.labels}
              </Chip>
            </View>
          </Card.Content>
        </Card>
      )} */}

      {/* Social Media Section */}
      <Card
        mode="contained"
        style={[
          styles.infoCard,
          { backgroundColor: theme.colors.elevation.level1 },
        ]}
      >
        <Card.Content>
          <Text
            variant="titleMedium"
            style={[styles.sectionTitle, { color: theme.colors.onSurface }]}
          >
            Connected Apps
          </Text>

          <List.AccordionGroup>
            <List.Accordion
              title="WhatsApp"
              background={{ color: "transparent" }}
              id="1"
              left={(props) => (
                <FontAwesome6 name="whatsapp" size={26} {...props} />
              )}
              style={{ backgroundColor: theme.colors.elevation.level1 }}
              titleStyle={{ fontSize: 18 }}
            >
              <List.Item
                title={`Message  +${getFormattedPhoneNumber(contact)}`}
                left={(props) => (
                  <List.Icon {...props} icon="message-outline" />
                )}
                onPress={() =>
                  Linking.openURL(`https://wa.me/${contact.fullPhoneNumber}`)
                }
              />
              <List.Item
                title={`Voice call  +${getFormattedPhoneNumber(contact)}`}
                left={(props) => <List.Icon {...props} icon="phone-outline" />}
                onPress={() =>
                  Linking.openURL(`https://wa.me/${contact.fullPhoneNumber}`)
                }
              />
            </List.Accordion>
            <List.Accordion
              title="Telegram"
              background={{ color: "transparent" }}
              id="2"
              left={(props) => (
                <FontAwesome6 name="telegram" size={26} {...props} />
              )}
              style={{ backgroundColor: theme.colors.elevation.level1 }}
              titleStyle={{ fontSize: 18 }}
            >
              <List.Item
                title={`Message  +${getFormattedPhoneNumber(contact)}`}
                left={(props) => (
                  <List.Icon {...props} icon="message-outline" />
                )}
                onPress={() =>
                  Linking.openURL(`https://t.me/+${contact.fullPhoneNumber}`)
                }
              />
              <List.Item
                title={`Voice call  +${getFormattedPhoneNumber(contact)}`}
                left={(props) => <List.Icon {...props} icon="phone-outline" />}
                onPress={() =>
                  Linking.openURL(
                    `https://t.me/+${contact.fullPhoneNumber}&profile`
                  )
                }
              />
            </List.Accordion>
          </List.AccordionGroup>
        </Card.Content>
      </Card>

      {/* Additional Info */}
      {(contact.website || contact.birthday || contact.notes) && (
        <Card
          mode="contained"
          style={[
            styles.infoCard,
            { backgroundColor: theme.colors.elevation.level1, marginBottom: 0 },
          ]}
        >
          <Card.Content>
            <Text
              variant="titleMedium"
              style={[styles.sectionTitle, { color: theme.colors.onSurface }]}
            >
              About {contact.name.split(" ")[0]}
            </Text>

            {contact.website && (
              <View style={styles.infoRow}>
                <IconButton icon="link" size={27} />
                <Text
                  variant="bodyLarge"
                  style={[styles.infoText, { color: theme.colors.primary }]}
                  onPress={() => {
                    Linking.openURL(contact.website!);
                  }}
                >
                  {contact.website}
                </Text>
              </View>
            )}

            {contact.birthday && (
              <View style={styles.infoRow}>
                <IconButton icon="cake-variant-outline" size={27} />
                <View style={styles.infoTextContainer}>
                  <Text
                    variant="bodyLarge"
                    style={[styles.infoText, { color: theme.colors.onSurface }]}
                  >
                    {getFormattedDate(contact.birthday)}
                  </Text>
                  <Text
                    variant="bodySmall"
                    style={[
                      styles.infoSubtitle,
                      { color: theme.colors.onSurfaceVariant },
                    ]}
                  >
                    Birthday
                  </Text>
                </View>
              </View>
            )}

            {contact.notes && (
              <View style={styles.infoRow}>
                <IconButton icon="note-text-outline" size={27} />
                <Text
                  variant="bodyLarge"
                  style={[
                    styles.infoText,
                    { color: theme.colors.onSurface, flex: 1 },
                  ]}
                >
                  {contact.notes}
                </Text>
              </View>
            )}
          </Card.Content>
        </Card>
      )}

      <View style={styles.buttonContainer}>
        <Button
          mode="contained-tonal"
          onPress={handleShare}
          loading={isDeleting}
          disabled={isDeleting}
          style={styles.button}
          labelStyle={{ fontSize: 16 }}
        >
          Share Contact
        </Button>
        <Button
          mode="contained"
          onPress={showDialog}
          loading={isDeleting}
          disabled={isDeleting}
          style={styles.button}
          buttonColor={theme.colors.error}
          textColor={theme.colors.onError}
          labelStyle={{ fontSize: 16 }}
        >
          Delete Contact
        </Button>
      </View>

      <Portal>
        <Snackbar
          visible={visible}
          onDismiss={onDismissSnackBar}
          action={{
            label: "Dismiss",
            onPress: () => {
              clearDeleteError();
              onDismissSnackBar();
            },
          }}
        >
          {deleteError || "An error occurred"}
        </Snackbar>
        <Dialog visible={open} onDismiss={hideDialog}>
          <Dialog.Title>Delete contact?</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">
              This contact will be permanently deleted from your device
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
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
  },
  headerContainer: {
    position: "relative",
    paddingTop: 16,
    paddingHorizontal: 16,
    alignItems: "center",
  },
  name: {
    textAlign: "center",
    fontWeight: "600",
    marginBottom: 6,
  },
  subtitle: {
    textAlign: "center",
    marginBottom: 8,
  },
  actionButtonsContainer: {
    flexDirection: "row",
    justifyContent: "space-around",
    paddingHorizontal: 32,
    paddingVertical: 24,
  },
  actionButton: {
    alignItems: "center",
    minWidth: 60,
  },
  infoCard: {
    marginHorizontal: 16,
    marginVertical: 8,
    borderRadius: 16,
  },
  sectionTitle: {
    marginBottom: 16,
    fontWeight: "600",
    fontSize: 18,
  },
  infoRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingTop: 12,
    gap: 8,
  },
  infoTextContainer: {
    flex: 1,
    gap: 3,
  },
  infoText: {
    fontSize: 18,
  },
  infoSubtitle: {
    fontSize: 14,
  },
  button: {
    paddingVertical: 5,
    borderRadius: 50,
    marginHorizontal: 16,
  },
  buttonContainer: {
    marginTop: 20,
    gap: 12,
  },
});
