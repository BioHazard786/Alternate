import telegramIcon from "@/assets/in-app-icon/telegram.png";
import whatsappIcon from "@/assets/in-app-icon/whatsapp.png";
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
import * as Clipboard from "expo-clipboard";
import { router, Stack, useLocalSearchParams } from "expo-router";
import React, { useState } from "react";
import {
  Image,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  ToastAndroid,
  View,
} from "react-native";
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
    Linking.openURL(`tel:+${contact.fullPhoneNumber}`);
  };

  const handleMessage = () => {
    Linking.openURL(`sms:+${contact.fullPhoneNumber}`);
  };

  const handleEmail = () => {
    if (contact.email) {
      Linking.openURL(`mailto:${contact.email}`);
    }
  };

  const handleWhatsApp = async () => {
    const supported = await Linking.canOpenURL(
      `whatsapp://send?phone=${contact.fullPhoneNumber}`
    );

    if (supported) {
      Linking.openURL(`whatsapp://send?phone=${contact.fullPhoneNumber}`);
    } else {
      Linking.openURL(`https://wa.me/${contact.fullPhoneNumber}`);
    }
  };

  const handleTelegram = async (profile: boolean) => {
    const supported = await Linking.canOpenURL(
      `tg://resolve?phone=${contact.fullPhoneNumber}`
    );

    if (supported) {
      Linking.openURL(
        `tg://resolve?phone=${contact.fullPhoneNumber}${
          profile ? "&profile" : ""
        }`
      );
    } else {
      Linking.openURL(
        `https://t.me/+${contact.fullPhoneNumber}${profile ? "?profile" : ""}`
      );
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

  const handleCopyToClipboard = async (text: string) => {
    await Clipboard.setStringAsync(text);
    ToastAndroid.show("Copied to clipboard", ToastAndroid.SHORT);
  };

  return (
    <ScrollView
      style={[styles.container, { paddingBottom: insets.bottom + 16 }]}
      contentContainerStyle={[
        styles.scrollContent,
        { paddingBottom: insets.bottom + 16 },
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

        <Pressable onLongPress={() => handleCopyToClipboard(contact.name)}>
          <Text
            variant="headlineMedium"
            style={[styles.name, { color: theme.colors.onSurface }]}
          >
            {getFormattedName(contact)}
          </Text>
        </Pressable>

        {contact.nickname && (
          <Pressable
            onLongPress={() => handleCopyToClipboard(contact.nickname!)}
          >
            <Text
              variant="bodyLarge"
              style={[
                styles.subtitle,
                { color: theme.colors.onSurfaceVariant, marginBottom: 6 },
              ]}
            >
              {contact.nickname}
            </Text>
          </Pressable>
        )}

        {contact.appointment && (
          <Pressable
            onLongPress={() => handleCopyToClipboard(contact.appointment!)}
          >
            <Text
              variant="bodyLarge"
              style={[
                styles.subtitle,
                { color: theme.colors.onSurfaceVariant },
              ]}
            >
              {contact.appointment}
            </Text>
          </Pressable>
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
          <Pressable
            onLongPress={() =>
              handleCopyToClipboard("+" + contact.fullPhoneNumber)
            }
          >
            <View style={styles.infoRow}>
              <IconButton icon="phone-outline" size={25} />
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
          </Pressable>
          {contact.email && (
            <Pressable
              onLongPress={() => handleCopyToClipboard(contact.email!)}
            >
              <View style={styles.infoRow}>
                <IconButton icon="email-outline" size={25} />
                <View style={styles.infoTextContainer}>
                  <Text
                    variant="bodyLarge"
                    style={[styles.infoText, { color: theme.colors.onSurface }]}
                  >
                    {contact.email}
                  </Text>
                </View>
              </View>
            </Pressable>
          )}

          {contact.location && (
            <Pressable
              onLongPress={() => handleCopyToClipboard(contact.location!)}
            >
              <View style={styles.infoRow}>
                <IconButton icon="map-marker-outline" size={25} />
                <View style={styles.infoTextContainer}>
                  <Text
                    variant="bodyLarge"
                    style={[styles.infoText, { color: theme.colors.onSurface }]}
                  >
                    {contact.location}
                  </Text>
                </View>
              </View>
            </Pressable>
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

          <List.Accordion
            title="WhatsApp"
            background={{ color: "transparent" }}
            id="1"
            left={(props) => (
              <Image
                source={whatsappIcon}
                style={{ width: 25, height: 25, ...props.style }}
              />
            )}
            style={{
              backgroundColor: theme.colors.elevation.level1,
            }}
            titleStyle={{ fontSize: 18 }}
          >
            <List.Item
              title={`Message  +${getFormattedPhoneNumber(contact)}`}
              left={(props) => <List.Icon {...props} icon="message-outline" />}
              onPress={handleWhatsApp}
              style={styles.listStyle}
              borderless
            />
            <List.Item
              title={`Voice call  +${getFormattedPhoneNumber(contact)}`}
              left={(props) => <List.Icon {...props} icon="phone-outline" />}
              onPress={handleWhatsApp}
              style={styles.listStyle}
              borderless
            />
          </List.Accordion>
          <List.Accordion
            title="Telegram"
            background={{ color: "transparent" }}
            id="2"
            left={(props) => (
              <Image
                source={telegramIcon}
                style={{ width: 25, height: 25, ...props.style }}
              />
            )}
            style={{
              backgroundColor: theme.colors.elevation.level1,
            }}
            titleStyle={{ fontSize: 18 }}
          >
            <List.Item
              title={`Message  +${getFormattedPhoneNumber(contact)}`}
              left={(props) => <List.Icon {...props} icon="message-outline" />}
              onPress={() => handleTelegram(false)}
              style={styles.listStyle}
              borderless
            />
            <List.Item
              title={`Voice call  +${getFormattedPhoneNumber(contact)}`}
              left={(props) => <List.Icon {...props} icon="phone-outline" />}
              onPress={() => handleTelegram(true)}
              style={styles.listStyle}
              borderless
            />
          </List.Accordion>
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
              <Pressable
                onLongPress={() => handleCopyToClipboard(contact.website!)}
              >
                <View style={styles.infoRow}>
                  <IconButton icon="link" size={25} />
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
              </Pressable>
            )}

            {contact.birthday && (
              <Pressable
                onLongPress={() =>
                  handleCopyToClipboard(getFormattedDate(contact.birthday!))
                }
              >
                <View style={styles.infoRow}>
                  <IconButton icon="cake-variant-outline" size={25} />
                  <View style={styles.infoTextContainer}>
                    <Text
                      variant="bodyLarge"
                      style={[
                        styles.infoText,
                        { color: theme.colors.onSurface },
                      ]}
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
              </Pressable>
            )}

            {contact.notes && (
              <Pressable
                onLongPress={() => handleCopyToClipboard(contact.notes!)}
              >
                <View style={styles.infoRow}>
                  <IconButton icon="note-text-outline" size={25} />
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
              </Pressable>
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
  pressableStyle: {
    paddingTop: 12,
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
  listStyle: {
    borderRadius: 16,
  },
});
