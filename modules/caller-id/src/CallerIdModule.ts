import { NativeModule, requireNativeModule } from "expo";

import { CallerInfo } from "@/lib/types";
import { CallerIdModuleEvents } from "./CallerId.types";

declare class CallerIdModule extends NativeModule<CallerIdModuleEvents> {
  hasOverlayPermission(): Promise<boolean>;
  requestOverlayPermission(): Promise<boolean>;
  storeCallerInfo(
    phoneNumber: string,
    countryCode: string,
    name: string,
    appointment: string,
    city: string,
    iosRow: string
  ): Promise<boolean>;
  getCallerInfo(phoneNumber: string): Promise<CallerInfo | null>;
  removeCallerInfo(phoneNumber: string): Promise<boolean>;
  getAllCallerInfo(): Promise<CallerInfo[]>;
  getAllStoredNumbers(): Promise<string[]>;
  clearAllCallerInfo(): Promise<boolean>;

  // Settings functions
  setShowPopup(showPopup: boolean): boolean;
  getShowPopup(): boolean;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<CallerIdModule>("CallerId");
