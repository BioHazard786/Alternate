import { NativeModule, requireNativeModule } from "expo";

import { CallerInfo } from "@/lib/types";
import { CallerIdModuleEvents } from "./CallerId.types";

declare class CallerIdModule extends NativeModule<CallerIdModuleEvents> {
  hasOverlayPermission(): Promise<boolean>;
  requestOverlayPermission(): Promise<boolean>;
  storeCallerInfo(callerData: CallerInfo): Promise<boolean>;
  storeMultipleCallerInfo(callerData: CallerInfo[]): Promise<boolean>;
  getCallerInfo(phoneNumber: string): Promise<CallerInfo | null>;
  removeCallerInfo(fullPhoneNumber: string): Promise<boolean>;
  getAllCallerInfo(): Promise<CallerInfo[]>;
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
