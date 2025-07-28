import { COUNTRIES } from "@/lib/countries";
import { Country, PhoneNumberData, PhoneNumberInputProps } from "@/lib/types";
import React, { useState } from "react";
import { Controller, FieldPath, FieldValues } from "react-hook-form";

import { StyleSheet, TouchableOpacity, View } from "react-native";
import { SheetManager } from "react-native-actions-sheet";
import { Text, TextInput, useTheme } from "react-native-paper";

const PhoneNumberInput = <
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>
>({
  control,
  name,
  rules,
  ...props
}: PhoneNumberInputProps<TFieldValues, TName>) => {
  const theme = useTheme();

  // Selecting default country based on control's default values
  const defaultCountry =
    COUNTRIES.find(
      (country) =>
        country.code === control._defaultValues?.phoneNumber?.countryCode
    ) || COUNTRIES[0];

  // Internal state for selected country
  const [selectedCountry, setSelectedCountry] =
    useState<Country>(defaultCountry);

  const handlePhoneChange = (
    phoneNumber: string,
    onChange: (...event: any[]) => void
  ) => {
    const phoneData: PhoneNumberData = {
      number: phoneNumber,
      countryCode: selectedCountry.code,
      dialCode: selectedCountry.dialCode,
    };
    onChange(phoneData);
  };

  return (
    <Controller
      control={control}
      name={name}
      rules={
        rules || {
          required: "Phone number is required",
          validate: (value: PhoneNumberData) => {
            if (!value || !value.number?.trim()) {
              return "Phone number is required";
            }

            // Check if phone number contains only digits
            if (!/^\d+$/.test(value.number.trim())) {
              return "Phone number should contain only numbers";
            }
          },
        }
      }
      render={({ field: { onChange, onBlur, value } }) => {
        return (
          <View style={styles.container}>
            <TouchableOpacity
              style={[
                styles.flagContainer,
                {
                  borderColor: theme.colors.outline,
                  backgroundColor: theme.colors.surface,
                },
              ]}
              onPress={() =>
                SheetManager.show("country-selector-sheet", {
                  payload: {
                    selectedCountry,
                    setSelectedCountry,
                    onChange,
                    currentValue: value,
                  },
                })
              }
              activeOpacity={0.7}
              accessibilityLabel={`Selected country: ${selectedCountry.name}, dial code +${selectedCountry.dialCode}`}
              accessibilityHint="Tap to select a different country"
              accessibilityRole="button"
            >
              <Text style={[styles.flag, { color: theme.colors.onSurface }]}>
                {selectedCountry.flag}
              </Text>
              <Text
                style={[
                  styles.chevron,
                  { color: theme.colors.onSurfaceVariant },
                ]}
              >
                ▼
              </Text>
              <Text
                style={[styles.dialCode, { color: theme.colors.onSurface }]}
              >
                +{selectedCountry.dialCode}
              </Text>
            </TouchableOpacity>
            <TextInput
              {...props}
              value={value?.number || ""}
              onChangeText={(text) => handlePhoneChange(text, onChange)}
              onBlur={onBlur}
              mode="outlined"
              keyboardType="phone-pad"
              style={{ flex: 1 }}
            />
          </View>
        );
      }}
    />
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    gap: 12,
    flexDirection: "row",
    flexGrow: 1,
    alignItems: "flex-end",
  },
  flagContainer: {
    borderWidth: 1,
    minWidth: 80,
    height: 50,
    flexDirection: "row",
    flex: 0,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    borderRadius: 4,
    paddingHorizontal: 8,
    gap: 4,
  },
  flag: {
    fontSize: 18,
  },
  dialCode: {
    fontSize: 14,
    fontWeight: "500",
  },
  chevron: {
    fontSize: 8,
    opacity: 0.6,
  },
  sheetContainer: {
    flex: 1,
    alignItems: "center",
  },
  bottomSheetBackground: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
  },
  listContainer: {
    paddingBottom: 20,
    flexGrow: 1,
  },
  emptyContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 32,
  },
  listFlagContainer: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    marginRight: 8,
  },
});

export default PhoneNumberInput;
