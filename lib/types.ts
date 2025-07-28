import { Control, FieldPath, FieldValues } from "react-hook-form";
import { TextInputProps } from "react-native-paper";

export type Contact = {
  fullPhoneNumber: string;
  phoneNumber: string;
  countryCode: string;
  name: string;
  appointment: string;
  location: string;
  iosRow?: string;
  suffix?: string;
  prefix?: string;
  email?: string;
  notes?: string;
  website?: string;
  birthday?: string;
  labels?: string;
  nickname?: string;
};

export type ContactFormData = {
  name: string;
  phoneNumber: PhoneNumberData;
  appointment?: string;
  location?: string;
  suffix?: string;
  prefix?: string;
  email?: string;
  notes?: string;
  website?: string;
  birthday?: string;
  labels?: string;
  nickname?: string;
};
export type ListItem =
  | { type: "header"; letter: string }
  | {
      type: "item";
      contact: Contact;
      index: number;
      isFirst: boolean;
      isLast: boolean;
    };

export type Country = {
  name: string;
  code: string;
  dialCode: string;
  flag: string;
};

export type PhoneNumberData = {
  number: string;
  countryCode: string;
  dialCode: string;
};

export interface PhoneNumberInputProps<
  TFieldValues extends FieldValues = FieldValues,
  TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>
> extends Omit<TextInputProps, "value" | "onChangeText"> {
  control: Control<TFieldValues>;
  name: TName;
  rules?: any;
}
