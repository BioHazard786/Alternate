import AdditionalFieldSheet from "@/components/additional-field-sheet";
import CountrySelectorSheet from "@/components/country-selector-sheet";
import { Country } from "@/lib/types";
import { registerSheet, SheetDefinition } from "react-native-actions-sheet";

registerSheet("country-selector-sheet", CountrySelectorSheet);
registerSheet("additional-field-sheet", AdditionalFieldSheet);

// We extend some of the types here to give us great intellisense
// across the app for all registered sheets.
declare module "react-native-actions-sheet" {
  interface Sheets {
    "country-selector-sheet": SheetDefinition<{
      payload: {
        selectedCountry: Country;
        setSelectedCountry: (country: Country) => void;
        onChange: (...event: any[]) => void;
        currentValue: any;
      };
    }>;
    "additional-field-sheet": SheetDefinition<{
      payload: {
        visibleFields: Set<string>;
        setVisibleFields: (fields: Set<string>) => void;
      };
    }>;
  }
}

export {};
