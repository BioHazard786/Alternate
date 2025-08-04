import { Contact } from "@/lib/types";
import { getFormattedName } from "@/lib/utils";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system";
import { shareAsync } from "expo-sharing";
import parsePhoneNumberFromString from "libphonenumber-js";
import { Platform } from "react-native";

/**
 * Escapes special characters in VCF fields
 */
function escapeVCFValue(value: string): string {
  return value
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "");
}

/**
 * Converts a contact object to VCF format string (Google Contacts compatible)
 */
export function contactToVCF(contact: Contact): string {
  const name = contact.name;

  const vcfLines = [
    "BEGIN:VCARD",
    "VERSION:2.1",
    `N:;${name};;${escapeVCFValue(contact.prefix || "")};${escapeVCFValue(
      contact.suffix || ""
    )}`,
    `FN:${escapeVCFValue(getFormattedName(contact))}`,
  ];

  // Add nickname if exists
  if (contact.nickname) {
    vcfLines.push(
      `X-ANDROID-CUSTOM:vnd.android.cursor.item/nickname;${escapeVCFValue(
        contact.nickname
      )};1;;;;;;;;;;;;;`
    );
  }

  vcfLines.push(`TEL;CELL;PREF:+${contact.fullPhoneNumber}`);

  // Add email if exists
  if (contact.email) {
    vcfLines.push(`EMAIL;PREF;HOME:${escapeVCFValue(contact.email)}`);
  }

  // Add location/address if exists
  if (contact.location) {
    const escapedLocation = escapeVCFValue(contact.location);
    vcfLines.push(`ADR;PREF;HOME:;;;;${escapedLocation};;`);
  }

  // Add appointment as TITLE (not NOTE as requested)
  if (contact.appointment) {
    vcfLines.push(`TITLE:${escapeVCFValue(contact.appointment)}`);
  }

  // Add website if exists
  if (contact.website) {
    vcfLines.push(`URL:${escapeVCFValue(contact.website)}`);
  }

  // Add notes if exists
  if (contact.notes) {
    vcfLines.push(`NOTE:${escapeVCFValue(contact.notes)}`);
  }

  // Add birthday if exists
  if (contact.birthday) {
    vcfLines.push(`BDAY:${escapeVCFValue(contact.birthday)}`);
  }

  // Add photo if exists (base64 encoded)
  if (contact.photo) {
    // Extract base64 data from data URI if present
    const base64Data = contact.photo.startsWith("data:image/")
      ? contact.photo.split(",")[1]
      : contact.photo;

    // Get MIME type from data URI or default to JPEG
    const mimeType = contact.photo.startsWith("data:image/")
      ? contact.photo.split(";")[0].replace("data:", "")
      : "image/jpeg";

    vcfLines.push(
      `PHOTO;ENCODING=BASE64;TYPE=${mimeType.toUpperCase()}:${base64Data}`
    );
  }

  vcfLines.push("END:VCARD");

  return vcfLines.join("\r\n"); // Use CRLF for better compatibility
}

/**
 * Converts multiple contacts to a single VCF file content
 */
export function contactsToVCF(contacts: Contact[]): string {
  return contacts.map(contactToVCF).join("\r\n\r\n"); // Use CRLF for better compatibility
}

/**
 * Unescapes VCF field values
 */
function unescapeVCFValue(value: string): string {
  return value
    .replace(/\\n/g, "\n")
    .replace(/\\,/g, ",")
    .replace(/\\;/g, ";")
    .replace(/\\\\/g, "\\");
}

/**
 * Parses a phone number to extract country code and number
 */
function parsePhoneNumber(phoneStr: string): {
  countryCode: string;
  phoneNumber: string;
  fullPhoneNumber: string;
} {
  const phoneProto = parsePhoneNumberFromString(phoneStr, "IN");

  return {
    countryCode: phoneProto?.country || "IN",
    phoneNumber:
      phoneProto?.nationalNumber || phoneStr.replace(/[\s\-\(\)]/g, ""),
    fullPhoneNumber:
      phoneProto?.number?.replace(/^\+/, "") ||
      phoneStr.replace(/[\s\-\(\)]/g, ""),
  };
}

/**
 * Parses VCF content and extracts contact information (Google Contacts compatible)
 */
export function parseVCF(vcfContent: string): Contact[] {
  const contacts: Contact[] = [];

  // Normalize and unfold content in one pass
  const processedContent = vcfContent
    .replace(/\r\n?/g, "\n")
    .replace(/\n[ \t]/g, "");

  const vcards = processedContent.split("BEGIN:VCARD");

  for (const vcard of vcards) {
    if (!vcard.trim()) continue;

    const lines = vcard.split("\n").filter((line) => line.trim());
    const contact: Partial<Contact> = {
      name: "",
      fullPhoneNumber: "",
      phoneNumber: "",
      countryCode: "",
      appointment: "",
      location: "",
      iosRow: "",
      suffix: "",
      prefix: "",
      email: "",
      notes: "",
      website: "",
      labels: "",
      birthday: "",
      nickname: "",
      photo: "",
    };

    for (const line of lines) {
      const colonIndex = line.indexOf(":");
      if (colonIndex === -1) continue;

      const field = line.substring(0, colonIndex);
      const value = unescapeVCFValue(line.substring(colonIndex + 1));

      switch (true) {
        case field === "N":
          const [
            lastName = "",
            firstName = "",
            middleName = "",
            prefix = "",
            suffix = "",
          ] = value.split(";");
          contact.prefix = prefix;
          contact.suffix = suffix;
          if (firstName || lastName || middleName) {
            contact.name = [firstName, middleName, lastName]
              .filter(Boolean)
              .join(" ");
          }
          break;

        case field.startsWith("TEL") && !contact.fullPhoneNumber:
          const phone = parsePhoneNumber(value);
          Object.assign(contact, phone);
          break;

        case field.startsWith("EMAIL") &&
          !contact.email &&
          field.includes("PREF"):
          contact.email = value;
          break;

        case field.startsWith("ADR") && field.includes("PREF"):
          const addressParts = value.split(";");
          contact.location = addressParts[4] || addressParts[3] || "";
          break;

        case field === "TITLE":
          contact.appointment = value;
          break;

        case field === "URL":
          contact.website = value;
          break;

        case field === "NOTE":
          contact.notes = value;
          break;

        case field === "BDAY":
          contact.birthday = value;
          break;

        case field === "X-ANDROID-CUSTOM" &&
          value.startsWith("vnd.android.cursor.item/nickname;"):
          contact.nickname = value.split(";")[1] || "";
          break;

        case field.startsWith("PHOTO"):
          // Handle photo field with base64 encoding
          if (field.includes("ENCODING=BASE64") || field.includes("BASE64")) {
            // Extract MIME type from field parameters
            const typeMatch = field.match(/TYPE=([^;:]+)/i);
            const mimeType = typeMatch ? typeMatch[1].toLowerCase() : "jpeg";

            // Clean the base64 data (remove any whitespace/newlines)
            const cleanBase64 = value.replace(/\s/g, "");

            // Create data URI
            contact.photo = `data:image/${mimeType};base64,${cleanBase64}`;
          }
          break;
      }
    }

    if (contact.name && contact.fullPhoneNumber) {
      contacts.push(contact as Contact);
    }
  }

  return contacts;
}

async function saveFile(uri: string, filename: string, mimetype: string) {
  if (Platform.OS === "android") {
    const permissions =
      await FileSystem.StorageAccessFramework.requestDirectoryPermissionsAsync();
    if (permissions.granted) {
      const base64 = await FileSystem.readAsStringAsync(uri, {
        encoding: FileSystem.EncodingType.Base64,
      });
      await FileSystem.StorageAccessFramework.createFileAsync(
        permissions.directoryUri,
        filename,
        mimetype
      )
        .then(async (uri) => {
          await FileSystem.writeAsStringAsync(uri, base64, {
            encoding: FileSystem.EncodingType.Base64,
          });
        })
        .catch((e) => console.log(e));
    } else {
      shareAsync(uri);
    }
  } else {
    shareAsync(uri);
  }
}

/**
 * Exports contacts to a VCF file
 */
export async function exportContactsToVCF(
  contacts: Contact[]
): Promise<boolean> {
  try {
    const vcfContent = contactsToVCF(contacts);
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const fileName = `contacts_${timestamp}.vcf`;
    const fileUri = FileSystem.cacheDirectory + fileName;

    await FileSystem.writeAsStringAsync(fileUri, vcfContent, {
      encoding: FileSystem.EncodingType.UTF8,
    });

    await saveFile(fileUri, fileName, "text/vcard");

    await FileSystem.deleteAsync(fileUri, {
      idempotent: true,
    });

    return true;
  } catch (error) {
    console.error("Error exporting contacts to VCF:", error);
    return false;
  }
}

export async function shareContact(contact: Contact[]): Promise<void> {
  try {
    const vcfContent = contactsToVCF(contact);
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const fileName = `contacts_${timestamp}.vcf`;
    const fileUri = FileSystem.cacheDirectory + fileName;

    await FileSystem.writeAsStringAsync(fileUri, vcfContent, {
      encoding: FileSystem.EncodingType.UTF8,
    });

    await shareAsync(fileUri);

    await FileSystem.deleteAsync(fileUri, {
      idempotent: true,
    });
  } catch (error) {
    console.error("Error sharing contact:", error);
  }
}

/**
 * Imports contacts from a VCF file
 */
export async function importContactsFromVCF(): Promise<Contact[] | null> {
  try {
    const result = await DocumentPicker.getDocumentAsync({
      type: "text/*",
      copyToCacheDirectory: true,
      multiple: false,
    });

    if (result.canceled) {
      return null;
    }

    const fileUri = result.assets[0].uri;
    const vcfContent = await FileSystem.readAsStringAsync(fileUri, {
      encoding: FileSystem.EncodingType.UTF8,
    });

    const contacts = parseVCF(vcfContent);

    await FileSystem.deleteAsync(fileUri, {
      idempotent: true,
    });

    return contacts;
  } catch (error) {
    console.error("Error importing contacts from VCF:", error);
    return null;
  }
}
