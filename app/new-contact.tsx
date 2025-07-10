import PhoneNumberInput from "@/components/phone-number-input";
import { getCountryByCode } from "@/lib/countries";
import { ContactFormData } from "@/lib/types";
import CallerIdModule from "@/modules/caller-id";
import useContactStore from "@/store/contactStore";
import { router } from "expo-router";
import React from "react";
import { Controller, useForm } from "react-hook-form";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import {
  Button,
  HelperText,
  Portal,
  Snackbar,
  TextInput,
} from "react-native-paper";

export default function NewContactScreen() {
  const addContact = useContactStore.use.addContact();
  const error = useContactStore.use.addContactError();
  const clearError = useContactStore.use.clearAddError();

  const [visible, setVisible] = React.useState(false);

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting, isValid },
    setError,
    reset,
  } = useForm<ContactFormData>({
    defaultValues: {
      name: "",
      phoneNumber: { number: "", countryCode: "IN" },
      appointment: "",
      city: "",
    },
    mode: "onChange", // Validate on change for better UX
  });
  const onDismissSnackBar = () => setVisible(false);
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
      getCountryByCode(data.phoneNumber.countryCode.trim())?.dialCode +
      data.phoneNumber.number.trim();

    const success = await addContact({
      name: data.name.trim(),
      phoneNumber: fullPhoneNumber,
      countryCode: data.phoneNumber.countryCode.trim(),
      appointment: data.appointment?.trim() || "",
      city: data.city?.trim() || "",
      iosRow: "", // This is for iOS-specific handling
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
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
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
                  left={<TextInput.Icon icon="account" />}
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
                left={<TextInput.Icon icon="briefcase" />}
                label="Appointment (optional)"
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
            name="city"
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                left={<TextInput.Icon icon="map-marker" />}
                label="City (optional)"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                mode="outlined"
                disabled={isSubmitting}
              />
            )}
          />
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
    paddingHorizontal: 16,
    gap: 16,
  },
  saveButton: {
    marginTop: 20,
    paddingVertical: 5,
    borderRadius: 50,
  },
});
