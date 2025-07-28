import PhoneNumberInput from "@/components/phone-number-input";
import { additionalFields } from "@/constants/AdditionalFields";
import { getCountryByCode } from "@/lib/countries";
import { ContactFormData } from "@/lib/types";
import { getFormattedDate } from "@/lib/utils";
import CallerIdModule from "@/modules/caller-id";
import useContactStore from "@/store/contactStore";
import { router } from "expo-router";
import React from "react";
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
  TextInput,
  useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export default function NewContactScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const addContact = useContactStore.use.addContact();
  const error = useContactStore.use.addContactError();
  const clearError = useContactStore.use.clearAddError();
  const countryCode = CallerIdModule.getDialCountryCode();

  const [visible, setVisible] = React.useState(false);
  const [visibleFields, setVisibleFields] = React.useState<Set<string>>(
    new Set()
  );
  const [showDatePicker, setShowDatePicker] = React.useState(false);
  const [selectedDate, setSelectedDate] = React.useState(new Date());

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting, isValid },
    setError,
    reset,
  } = useForm<ContactFormData>({
    defaultValues: {
      name: "",
      phoneNumber: {
        number: "",
        countryCode: countryCode,
        dialCode: getCountryByCode(countryCode)?.dialCode,
      },
      appointment: "",
      location: "",
      suffix: "",
      prefix: "",
      email: "",
      notes: "",
      website: "",
      birthday: "",
      labels: "",
      nickname: "",
    },
    mode: "onChange", // Validate on change for better UX
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
    // Check if phone number is already in the system
    const existingContact = await CallerIdModule.getCallerInfo(
      data.phoneNumber.number.trim()
    );
    if (existingContact) {
      setError("phoneNumber", { message: "This number already exists" });
      return;
    }

    const fullPhoneNumber =
      data.phoneNumber.dialCode + data.phoneNumber.number.trim();

    const success = await addContact({
      name: data.name.trim(),
      fullPhoneNumber: fullPhoneNumber,
      phoneNumber: data.phoneNumber.number.trim(),
      countryCode: data.phoneNumber.countryCode.trim(),
      appointment: data.appointment?.trim() || "",
      location: data.location?.trim() || "",
      iosRow: "",
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
      reset(); // Reset form on success
      router.back();
    } else {
      setVisible(true);
    }
  };

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
                  mode="outlined"
                  error={!!errors.name}
                  disabled={isSubmitting}
                />
              )}
            />
            {errors.name && (
              <HelperText type="error">{errors.name.message}</HelperText>
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
              <HelperText type="error">{errors.phoneNumber.message}</HelperText>
            )}
          </View>
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
                disabled={isSubmitting}
              />
            )}
          />
          <Controller
            control={control}
            name="location"
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                left={<TextInput.Icon icon="map-marker-outline" />}
                label="Location"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                mode="outlined"
                disabled={isSubmitting}
              />
            )}
          />
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
              style={styles.saveButton}
              labelStyle={{ fontSize: 16 }}
              disabled={!isValid || isSubmitting}
              loading={isSubmitting}
            >
              Save Contact
            </Button>
          </View>
        </View>
        <Portal>
          <Snackbar
            visible={visible}
            onDismiss={onDismissSnackBar}
            action={{
              label: "Dismiss",
              onPress: clearError,
            }}
          >
            {error || "Failed to save contact. Please try again."}
          </Snackbar>
        </Portal>
      </ScrollView>
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
    padding: 16,
    gap: 16,
  },
  list: {
    paddingHorizontal: 16,
  },
  addFieldButton: {
    borderRadius: 50,
    paddingVertical: 5,
  },
  saveButton: {
    paddingVertical: 5,
    borderRadius: 50,
  },
  buttonContainer: {
    marginTop: 20,
    gap: 12,
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
  datePickerContainer: {
    flex: 1,
    paddingHorizontal: 24,
    paddingVertical: 16,
  },
  datePickerButtons: {
    flexDirection: "row",
    justifyContent: "space-around",
    paddingTop: 20,
    gap: 16,
  },
  dateButton: {
    flex: 1,
    borderRadius: 25,
  },
});
