import Material3Avatar from "@/components/material3-avatar";
import PhoneNumberInput from "@/components/phone-number-input";
import { additionalFields } from "@/constants/AdditionalFields";
import { getAvatarColor } from "@/lib/avatar-utils";
import { getCountryByCode } from "@/lib/countries";
import { Contact, ContactFormData } from "@/lib/types";
import { getFormattedDate, getVisibleFields, trimDialCode } from "@/lib/utils";
import useContactStore from "@/store/contactStore";
import { router, useLocalSearchParams } from "expo-router";
import React, { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  TextInput as RNTextInput,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import { SheetManager } from "react-native-actions-sheet";
import DatePicker from "react-native-date-picker";
import {
  Button,
  HelperText,
  Portal,
  Snackbar,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export default function EditContactScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { contact: contactParam, index } = useLocalSearchParams();

  // Parse the contact from JSON string
  const contact: Contact | null = contactParam
    ? JSON.parse(contactParam as string)
    : null;

  const originalFullPhoneNumber = contact?.fullPhoneNumber;
  const letter = contact?.name?.charAt(0) || "?";

  const updateContact = useContactStore.use.updateContact();
  const updateError = useContactStore.use.updateContactError();
  const clearUpdateError = useContactStore.use.clearUpdateError();
  const [visible, setVisible] = useState(false);
  const [visibleFields, setVisibleFields] = useState<Set<string>>(() =>
    getVisibleFields(contact)
  );
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(() =>
    contact?.birthday ? new Date(contact.birthday) : new Date()
  );

  const [avatarBackgroundColor, avatarTextColor] = getAvatarColor(
    letter,
    theme.dark,
    Number(index)
  );

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting, isValid },
    reset,
  } = useForm<ContactFormData>({
    defaultValues: {
      name: contact?.name || "",
      phoneNumber: {
        number: trimDialCode(
          contact?.phoneNumber || "",
          contact?.countryCode || "IN"
        ),
        countryCode: contact?.countryCode || "IN",
        dialCode: getCountryByCode(contact?.countryCode || "IN")?.dialCode,
      },
      appointment: contact?.appointment || "",
      location: contact?.location || "",
      suffix: contact?.suffix || "",
      prefix: contact?.prefix || "",
      email: contact?.email || "",
      notes: contact?.notes || "",
      website: contact?.website || "",
      birthday: contact?.birthday || "",
      labels: contact?.labels || "",
      nickname: contact?.nickname || "",
    },
    mode: "onChange",
  });

  const onDismissSnackBar = () => setVisible(false);

  const removeFieldAndReset = (fieldKey: string) => {
    const newVisibleFields = new Set(visibleFields);
    newVisibleFields.delete(fieldKey);
    setVisibleFields(newVisibleFields);

    // Reset the field value in the form
    reset({
      ...control._formValues,
      [fieldKey]: "",
    });
  };

  const renderAdditionalField = (field: (typeof additionalFields)[0]) => {
    if (!visibleFields.has(field.key)) return null;

    // Special handling for birthday field
    if (field.key === "birthday") {
      return (
        <Controller
          key={field.key}
          control={control}
          name={field.key as keyof ContactFormData}
          render={({ field: { onChange, onBlur, value } }) => (
            <View style={{ position: "relative" }}>
              <TextInput
                left={
                  <TextInput.Icon
                    icon={field.icon}
                    onPress={() => setShowDatePicker(true)}
                  />
                }
                right={
                  <TextInput.Icon
                    icon="close"
                    onPress={() => removeFieldAndReset(field.key)}
                  />
                }
                label={field.label}
                value={typeof value === "string" ? getFormattedDate(value) : ""}
                mode="outlined"
                disabled={isSubmitting}
                editable={false}
                render={(props) => (
                  <Pressable onPress={() => setShowDatePicker(true)}>
                    <RNTextInput {...props} />
                  </Pressable>
                )}
              />
              <DatePicker
                modal
                mode="date"
                open={showDatePicker}
                date={selectedDate}
                theme={theme.dark ? "dark" : "light"}
                buttonColor={theme.colors.onSecondaryContainer}
                dividerColor={theme.colors.onSecondaryContainer}
                confirmText="OK"
                onConfirm={(date) => {
                  setShowDatePicker(false);
                  onChange(date.toISOString().split("T")[0]);
                  setSelectedDate(date);
                }}
                onCancel={() => {
                  setShowDatePicker(false);
                }}
              />
            </View>
          )}
        />
      );
    }

    return (
      <Controller
        key={field.key}
        control={control}
        name={field.key as keyof ContactFormData}
        render={({ field: { onChange, onBlur, value } }) => (
          <TextInput
            left={<TextInput.Icon icon={field.icon} />}
            right={
              <TextInput.Icon
                icon="close"
                onPress={() => removeFieldAndReset(field.key)}
              />
            }
            label={field.label}
            value={typeof value === "string" ? value : ""}
            onChangeText={onChange}
            onBlur={onBlur}
            mode="outlined"
            disabled={isSubmitting}
            multiline={field.key === "notes"}
            numberOfLines={field.key === "notes" ? 3 : 1}
            keyboardType={field.key === "email" ? "email-address" : "default"}
          />
        )}
      />
    );
  };

  const onSubmit = async (data: ContactFormData) => {
    const fullPhoneNumber =
      data.phoneNumber.dialCode + data.phoneNumber.number.trim();

    const success = await updateContact(originalFullPhoneNumber!, {
      name: data.name.trim(),
      fullPhoneNumber: fullPhoneNumber,
      phoneNumber: data.phoneNumber.number.trim(),
      countryCode: data.phoneNumber.countryCode.trim(),
      appointment: data.appointment?.trim() || "",
      location: data.location?.trim() || "",
      iosRow: contact?.iosRow || "",
      suffix: data.suffix?.trim() || "",
      prefix: data.prefix?.trim() || "",
      email: data.email?.trim() || "",
      notes: data.notes?.trim() || "",
      website: data.website?.trim() || "",
      birthday: data.birthday || "",
      labels: data.labels?.trim() || "",
      nickname: data.nickname?.trim() || "",
    });

    if (success) {
      router.dismissAll();
    } else {
      setVisible(true);
    }
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
      style={[styles.container, { paddingBottom: insets.bottom }]}
    >
      <ScrollView
        contentContainerStyle={[
          styles.scrollContent,
          { paddingBottom: insets.bottom },
        ]}
      >
        <View style={styles.formContainer}>
          <Material3Avatar
            letter={letter}
            backgroundColor={avatarBackgroundColor}
            textColor={avatarTextColor}
            style={{ marginVertical: 20, alignSelf: "center" }}
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
                  left={<TextInput.Icon icon="account-outline" />}
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
                  left={<TextInput.Icon icon="briefcase-outline" />}
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
              name="location"
              render={({ field: { onChange, onBlur, value } }) => (
                <TextInput
                  left={<TextInput.Icon icon="map-marker-outline" />}
                  label="location"
                  value={value}
                  onChangeText={onChange}
                  onBlur={onBlur}
                  mode="outlined"
                />
              )}
            />
          </View>
          {additionalFields.map(renderAdditionalField)}

          <View style={styles.buttonContainer}>
            <Button
              mode="contained-tonal"
              onPress={() =>
                SheetManager.show("additional-field-sheet", {
                  payload: { visibleFields, setVisibleFields },
                })
              }
              style={styles.addFieldButton}
              labelStyle={{ fontSize: 16 }}
              disabled={
                isSubmitting || visibleFields.size >= additionalFields.length
              }
            >
              Add fields
            </Button>
            <Button
              mode="contained"
              onPress={handleSubmit(onSubmit)}
              loading={isSubmitting}
              disabled={!isValid || isSubmitting}
              style={styles.saveButton}
              labelStyle={{ fontSize: 16 }}
            >
              Save Changes
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
              onDismissSnackBar();
            },
          }}
        >
          {updateError || "An error occurred"}
        </Snackbar>
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
    padding: 16,
  },
  formContainer: {
    flex: 1,
    paddingHorizontal: 16,
    gap: 16,
  },
  buttonContainer: {
    marginTop: 20,
    gap: 12,
  },
  addFieldButton: {
    borderRadius: 50,
    paddingVertical: 5,
  },
  saveButton: {
    paddingVertical: 5,
    borderRadius: 50,
  },
  bottomSheetContent: {
    flex: 1,
    paddingVertical: 16,
  },
  bottomSheetBackground: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
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
