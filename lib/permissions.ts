import { PermissionsAndroid, Platform } from "react-native";
import CallerIdModule from "../modules/caller-id";

export async function requestAndroidPermissions() {
  if (Platform.OS === "android") {
    // Request standard permissions together in batch
    const standardPermissions = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
      PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
    ]);

    // Then handle overlay permission separately (this opens system settings)
    if (!(await hasOverlayPermission())) {
      await CallerIdModule.requestOverlayPermission();
    }

    return {
      readPhoneState:
        standardPermissions[PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE],
      readCallLog:
        standardPermissions[PermissionsAndroid.PERMISSIONS.READ_CALL_LOG],
      systemAlertWindow: await hasOverlayPermission(),
    };
  }
  return null;
}

export async function hasOverlayPermission() {
  if (Platform.OS !== "android") return true;
  try {
    return await CallerIdModule.hasOverlayPermission();
  } catch {
    return false;
  }
}
