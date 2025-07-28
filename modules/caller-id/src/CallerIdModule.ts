import { NativeModule, requireNativeModule } from "expo";

import { Contact } from "@/lib/types";
import { CallerIdModuleEvents } from "./CallerId.types";

declare class CallerIdModule extends NativeModule<CallerIdModuleEvents> {
  hasOverlayPermission(): Promise<boolean>;
  requestOverlayPermission(): Promise<boolean>;
  storeCallerInfo(callerData: Contact): Promise<boolean>;
  storeMultipleCallerInfo(callerData: Contact[]): Promise<boolean>;
  getCallerInfo(phoneNumber: string): Promise<Contact | null>;
  removeCallerInfo(fullPhoneNumber: string): Promise<boolean>;
  removeMultipleCallerInfo(fullPhoneNumbers: string[]): Promise<boolean>;
  getAllCallerInfo(): Promise<Contact[]>;
  getAllStoredNumbers(): Promise<string[]>;
  clearAllCallerInfo(): Promise<boolean>;

  // Settings functions
  setShowPopup(showPopup: boolean): boolean;
  getShowPopup(): boolean;

  // Get SIM card country code
  getDialCountryCode(): string;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<CallerIdModule>("CallerId");
