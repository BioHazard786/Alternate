import Material3Avatar from "@/components/material3-avatar";
import Material3PhotoPickerPlaceholder from "@/components/material3-photopicker-placeholder";
import * as FileSystem from "expo-file-system";
import * as ImagePicker from "expo-image-picker";
import React, { useState } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Button, Dialog, Portal, Text } from "react-native-paper";

interface PhotoPickerProps {
  photo?: string; // Base64 encoded image string (data:image/jpeg;base64,...)
  onPhotoChange: (base64: string | undefined) => void; // Callback with base64 string
  size?: number;
  disabled?: boolean;
}

export default function PhotoPicker({
  photo,
  onPhotoChange,
  size = 200,
  disabled = false,
}: PhotoPickerProps) {
  const [permissionDialogVisible, setPermissionDialogVisible] = useState(false);
  const [cameraPermissionDialogVisible, setCameraPermissionDialogVisible] =
    useState(false);
  const [photoOptionsDialogVisible, setPhotoOptionsDialogVisible] =
    useState(false);
  const [errorDialogVisible, setErrorDialogVisible] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const convertImageToBase64 = async (uri: string): Promise<string> => {
    try {
      const base64 = await FileSystem.readAsStringAsync(uri, {
        encoding: FileSystem.EncodingType.Base64,
      });
      await FileSystem.deleteAsync(uri, {
        idempotent: true,
      }); // Clean up the temporary file
      return `data:image/jpeg;base64,${base64}`;
    } catch (error) {
      console.error("Error converting image to base64:", error);
      throw error;
    }
  };

  const requestPermission = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== "granted") {
      setPermissionDialogVisible(true);
      return false;
    }
    return true;
  };

  const pickImage = async () => {
    if (disabled) return;

    const hasPermission = await requestPermission();
    if (!hasPermission) return;

    setPhotoOptionsDialogVisible(true);
  };

  const takePhoto = async () => {
    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== "granted") {
      setCameraPermissionDialogVisible(true);
      return;
    }

    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ["images"],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.2,
    });

    if (!result.canceled && result.assets[0]) {
      try {
        const base64 = await convertImageToBase64(result.assets[0].uri);
        onPhotoChange(base64);
      } catch (error) {
        setErrorMessage("Failed to process the image. Please try again.");
        setErrorDialogVisible(true);
      }
    }
  };

  const pickFromGallery = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.2,
    });

    if (!result.canceled && result.assets[0]) {
      try {
        const base64 = await convertImageToBase64(result.assets[0].uri);
        onPhotoChange(base64);
      } catch (error) {
        setErrorMessage("Failed to process the image. Please try again.");
        setErrorDialogVisible(true);
      }
    }
  };

  return (
    <View style={styles.container}>
      <Pressable onPress={pickImage} disabled={disabled}>
        <View style={[styles.avatarContainer, { width: size, height: size }]}>
          {photo ? (
            <Material3Avatar photo={photo} />
          ) : (
            <Material3PhotoPickerPlaceholder />
          )}
        </View>
      </Pressable>
      <Button
        mode="text"
        onPress={pickImage}
        disabled={disabled}
        style={styles.button}
        labelStyle={styles.buttonLabel}
      >
        {photo ? "Change Photo" : "Add Photo"}
      </Button>

      <Portal>
        {/* Permission Dialog */}
        <Dialog
          visible={permissionDialogVisible}
          onDismiss={() => setPermissionDialogVisible(false)}
        >
          <Dialog.Title>Permission Required</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">
              Sorry, we need camera roll permissions to select photos.
            </Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setPermissionDialogVisible(false)}>
              OK
            </Button>
          </Dialog.Actions>
        </Dialog>

        {/* Camera Permission Dialog */}
        <Dialog
          visible={cameraPermissionDialogVisible}
          onDismiss={() => setCameraPermissionDialogVisible(false)}
        >
          <Dialog.Title>Permission Required</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">
              Sorry, we need camera permissions to take photos.
            </Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setCameraPermissionDialogVisible(false)}>
              OK
            </Button>
          </Dialog.Actions>
        </Dialog>

        {/* Photo Options Dialog */}
        <Dialog
          visible={photoOptionsDialogVisible}
          onDismiss={() => setPhotoOptionsDialogVisible(false)}
        >
          <Dialog.Title>Select Photo</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">Choose an option</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button
              onPress={() => {
                setPhotoOptionsDialogVisible(false);
                takePhoto();
              }}
            >
              Camera
            </Button>
            <Button
              onPress={() => {
                setPhotoOptionsDialogVisible(false);
                pickFromGallery();
              }}
            >
              Gallery
            </Button>
            {photo && (
              <Button
                onPress={() => {
                  setPhotoOptionsDialogVisible(false);
                  onPhotoChange(undefined);
                }}
                textColor="red"
              >
                Remove Photo
              </Button>
            )}
            <Button onPress={() => setPhotoOptionsDialogVisible(false)}>
              Cancel
            </Button>
          </Dialog.Actions>
        </Dialog>

        {/* Error Dialog */}
        <Dialog
          visible={errorDialogVisible}
          onDismiss={() => setErrorDialogVisible(false)}
        >
          <Dialog.Title>Error</Dialog.Title>
          <Dialog.Content>
            <Text variant="bodyMedium">{errorMessage}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setErrorDialogVisible(false)}>OK</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    marginVertical: 16,
  },
  avatarContainer: {
    elevation: 2,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  avatar: {
    elevation: 2,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  placeholderAvatar: {
    elevation: 2,
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  button: {
    marginTop: 8,
  },
  buttonLabel: {
    fontSize: 15,
  },
});
