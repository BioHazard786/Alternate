import PhoneNumberInput from "@/components/phone-number-input";
import { getAvatarColor } from "@/lib/avatar-utils";
import { getCountryByCode } from "@/lib/countries";
import { Contact, ContactFormData } from "@/lib/types";
import { trimDialCode } from "@/lib/utils";
import useContactStore from "@/store/contactStore";
import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import {
  Avatar,
  Button,
  HelperText,
  Portal,
  Snackbar,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";

export default function EditContactScreen() {
  const theme = useTheme();
  const { contact: contactParam, index } = useLocalSearchParams();

  // Parse the contact from JSON string
  const contact: Contact | null = contactParam
    ? JSON.parse(contactParam as string)
    : null;

  const phoneNumber = contact?.phoneNumber;
  const letter = contact?.name?.charAt(0) || "?";

  const updateContact = useContactStore.use.updateContact();
  const deleteContact = useContactStore.use.deleteContact();
  const updateError = useContactStore.use.updateContactError();
  const deleteError = useContactStore.use.deleteContactError();
  const clearUpdateError = useContactStore.use.clearUpdateError();
  const clearDeleteError = useContactStore.use.clearDeleteError();
  const [visible, setVisible] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const [avatarBackgroundColor, avatarTextColor] = getAvatarColor(
    letter,
    theme.dark,
    Number(index)
  );

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting, isValid },
    setError,
  } = useForm<ContactFormData>({
    defaultValues: {
      name: contact?.name || "",
      phoneNumber: {
        number: trimDialCode(
          contact?.phoneNumber || "",
          contact?.countryCode || "IN"
        ),
        countryCode: contact?.countryCode || "IN",
      },
      appointment: contact?.appointment || "",
      city: contact?.city || "",
    },
    mode: "onChange",
  });

  const onDismissSnackBar = () => setVisible(false);

  const onSubmit = async (data: ContactFormData) => {
    const fullPhoneNumber =
      getCountryByCode(data.phoneNumber.countryCode.trim())?.dialCode +
      data.phoneNumber.number.trim();

    const success = await updateContact(phoneNumber!, {
      name: data.name.trim(),
      phoneNumber: fullPhoneNumber,
      countryCode: data.phoneNumber.countryCode.trim(),
      appointment: data.appointment?.trim() || "",
      city: data.city?.trim() || "",
      iosRow: contact?.iosRow || "",
    });

    if (success) {
      router.back();
    } else {
      setVisible(true);
    }
  };

  const handleDelete = () => {
    Alert.alert(
      "Delete Contact",
      `Are you sure you want to delete ${contact?.name}?`,
      [
        {
          text: "Cancel",
          style: "cancel",
        },
        {
          text: "Delete",
          style: "destructive",
          onPress: async () => {
            setIsDeleting(true);
            const success = await deleteContact(phoneNumber!);
            setIsDeleting(false);

            if (success) {
              router.back();
            } else {
              setVisible(true);
            }
          },
        },
      ]
    );
  };

  // Show error if contact not found
  if (!contact) {
    return (
      <View style={styles.container}>
        <Text variant="bodyLarge" style={{ textAlign: "center" }}>
          This app needs permission to access contacts and phone state.
        </Text>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.formContainer}>
          <Avatar.Text
            size={130}
            label={letter}
            labelStyle={{ color: avatarTextColor, fontSize: 80 }}
            style={{
              backgroundColor: avatarBackgroundColor,
              alignSelf: "center",
              marginVertical: 16,
            }}
          />
          <View>
            <Controller
              control={control}
              name="name"
              rules={{
                required: "Name is required",
                minLength: {
                  value: 2,
                  message: "Name must be at least 2 characters",
                },
                validate: (value) => {
                  const trimmed = value?.trim();
                  if (!trimmed) return "Name cannot be empty";
                  if (trimmed.length < 2)
                    return "Name must be at least 2 characters";
                  return true;
                },
              }}
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  left={<TextInput.Icon icon="account" />}
                  label="Name *"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  error={!!errors.name}
                  mode="outlined"
                />
              )}
            />
            {errors.name && (
              <HelperText type="error" visible={!!errors.name}>
                {errors.name.message}
              </HelperText>
            )}
          </View>
          <View>
            <PhoneNumberInput
              control={control}
              name="phoneNumber"
              label="Phone Number *"
              error={!!errors.phoneNumber}
              disabled={isSubmitting}
            />
            {errors.phoneNumber && (
              <HelperText type="error" visible={!!errors.phoneNumber}>
                {errors.phoneNumber.message}
              </HelperText>
            )}
          </View>
          <View>
            <Controller
              control={control}
              name="appointment"
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  left={<TextInput.Icon icon="briefcase" />}
                  label="Appointment"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  mode="outlined"
                />
              )}
            />
          </View>
          <View>
            <Controller
              control={control}
              name="city"
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  left={<TextInput.Icon icon="map-marker" />}
                  label="City"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  mode="outlined"
                />
              )}
            />
          </View>
          <View style={styles.buttonContainer}>
            <Button
              mode="contained"
              onPress={handleSubmit(onSubmit)}
              loading={isSubmitting}
              disabled={!isValid || isSubmitting || isDeleting}
              style={styles.saveButton}
            >
              Save Changes
            </Button>

            <Button
              mode="contained"
              onPress={handleDelete}
              loading={isDeleting}
              disabled={isSubmitting || isDeleting}
              style={styles.deleteButton}
              buttonColor={theme.colors.error}
              textColor={theme.colors.onError}
            >
              Delete Contact
            </Button>
          </View>
        </View>
      </ScrollView>

      <Portal>
        <Snackbar
          visible={visible}
          onDismiss={onDismissSnackBar}
          action={{
            label: "Dismiss",
            onPress: () => {
              clearUpdateError();
              clearDeleteError();
              onDismissSnackBar();
            },
          }}
        >
          {updateError || deleteError || "An error occurred"}
        </Snackbar>
        {/* <Dialog visible={visible} onDismiss={hideDialog}>
          <Dialog.Icon icon="alert" />
          <Dialog.Title >This is a title</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">This is simple dialog</Text>
          </Dialog.Content>
        </Dialog> */}
      </Portal>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
  },
  formContainer: {
    flex: 1,
    padding: 16,
    paddingHorizontal: 16,
    gap: 16,
  },
  buttonContainer: {
    marginTop: 20,
    gap: 12,
  },
  saveButton: {
    paddingVertical: 5,
    borderRadius: 50,
  },
  deleteButton: {
    paddingVertical: 5,
    borderRadius: 50,
  },
});
