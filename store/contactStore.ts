import { CallerInfo } from "@/lib/types";
import { exportContactsToVCF, importContactsFromVCF } from "@/lib/vcf-utils";
import CallerIdModule from "@/modules/caller-id";
import createSelectors from "@/store/selectors";
import { create } from "zustand";

type State = {
  contacts: CallerInfo[];
  isLoading: boolean;
  fetchContactError: string | null;
  addContactError: string | null;
  deleteContactError: string | null;
  updateContactError: string | null;
  importError: string | null;
  exportError: string | null;
  isImporting: boolean;
  isExporting: boolean;
};

type Action = {
  fetchContacts: () => Promise<void>;
  addContact: (contact: CallerInfo) => Promise<boolean>;
  deleteContact: (fullPhoneNumber: string) => Promise<boolean>;
  updateContact: (
    originalPhoneNumber: string,
    updatedContact: CallerInfo
  ) => Promise<boolean>;
  importContacts: () => Promise<number | boolean>;
  exportContacts: () => Promise<boolean>;
  // Error clearing actions for better UX
  clearFetchError: () => void;
  clearAddError: () => void;
  clearDeleteError: () => void;
  clearUpdateError: () => void;
  clearImportError: () => void;
  clearExportError: () => void;
};

const initialState: State = {
  contacts: [],
  isLoading: false,
  fetchContactError: null,
  addContactError: null,
  deleteContactError: null,
  updateContactError: null,
  importError: null,
  exportError: null,
  isImporting: false,
  isExporting: false,
};

const useContactStoreBase = create<State & Action>((set, get) => ({
  ...initialState,

  fetchContacts: async () => {
    set({ fetchContactError: null, isLoading: true });
    try {
      const contacts = await CallerIdModule.getAllCallerInfo();
      set({ contacts });
    } catch (error) {
      console.error("Failed to fetch contacts:", error);
      set({ fetchContactError: "Failed to load contacts" });
    } finally {
      set({ isLoading: false });
    }
  },

  addContact: async (contact) => {
    set({ addContactError: null }); // Clear previous errors
    try {
      const success = await CallerIdModule.storeCallerInfo(contact);

      if (success) {
        // Refresh contacts list
        const updatedContacts = [...get().contacts, contact];
        updatedContacts.sort((a, b) => a.name.localeCompare(b.name));
        set({ contacts: updatedContacts });
      }

      return success;
    } catch (error) {
      set({ addContactError: "Failed to add contact" });
      console.error("Failed to add contact:", error);
      return false;
    }
  },

  deleteContact: async (fullPhoneNumber) => {
    set({ deleteContactError: null }); // Clear previous errors
    try {
      const success = await CallerIdModule.removeCallerInfo(fullPhoneNumber);

      if (success) {
        // Remove from local state
        const updatedContacts = get().contacts.filter(
          (contact) => contact.fullPhoneNumber !== fullPhoneNumber
        );
        set({ contacts: updatedContacts });
      }

      return success;
    } catch (error) {
      console.error("Failed to delete contact:", error);
      set({ deleteContactError: "Failed to delete contact" });
      return false;
    }
  },

  updateContact: async (originalPhoneNumber, updatedContact) => {
    set({ updateContactError: null }); // Clear previous errors
    try {
      // If phone number changed, we need to delete the old one first
      if (originalPhoneNumber !== updatedContact.fullPhoneNumber) {
        const deleteSuccess = await CallerIdModule.removeCallerInfo(
          originalPhoneNumber
        );
        if (!deleteSuccess) {
          throw new Error("Failed to remove original contact");
        }
      }

      // Store the updated contact (this will now overwrite if phone number is the same due to REPLACE strategy)
      const success = await CallerIdModule.storeCallerInfo(updatedContact);

      if (success) {
        // Update local state
        const contacts = get().contacts;
        const updatedContacts = contacts.filter(
          (contact) => contact.fullPhoneNumber !== originalPhoneNumber
        );
        updatedContacts.push(updatedContact);
        updatedContacts.sort((a, b) => a.name.localeCompare(b.name));
        set({ contacts: updatedContacts });
      }

      return success;
    } catch (error) {
      console.error("Failed to update contact:", error);
      set({ updateContactError: "Failed to update contact" });
      return false;
    }
  },

  importContacts: async () => {
    set({ importError: null, isImporting: true });
    try {
      const importedContacts = await importContactsFromVCF();

      if (!importedContacts || importedContacts.length === 0) {
        set({ importError: "No contacts found in the selected file" });
        return false;
      }

      const contacts = get().contacts;
      const existingNumbers = new Set(
        contacts.map((contact) => contact.fullPhoneNumber)
      );

      const newContacts = importedContacts.filter(
        (contact) => !existingNumbers.has(contact.fullPhoneNumber)
      );

      const success = await CallerIdModule.storeMultipleCallerInfo(newContacts);

      if (success) {
        // Refresh contacts list to show imported contacts
        const updatedContacts = [...contacts, ...newContacts];
        updatedContacts.sort((a, b) => a.name.localeCompare(b.name));
        set({ contacts: updatedContacts });
        return newContacts.length;
      } else {
        set({ importError: "Failed to import any contacts" });
        return false;
      }
    } catch (error) {
      console.error("Error importing contacts:", error);
      set({ importError: "Failed to import contacts from file" });
      return false;
    } finally {
      set({ isImporting: false });
    }
  },

  exportContacts: async () => {
    set({ exportError: null, isExporting: true });
    try {
      const contacts = get().contacts;

      if (contacts.length === 0) {
        set({ exportError: "No contacts to export" });
        return false;
      }

      const success = await exportContactsToVCF(contacts);

      if (!success) {
        set({ exportError: "Failed to export contacts" });
        return false;
      }

      return true;
    } catch (error) {
      console.error("Error exporting contacts:", error);
      set({ exportError: "Failed to export contacts to file" });
      return false;
    } finally {
      set({ isExporting: false });
    }
  },

  // Error clearing actions for better UX
  clearFetchError: () => set({ fetchContactError: null }),
  clearAddError: () => set({ addContactError: null }),
  clearDeleteError: () => set({ deleteContactError: null }),
  clearUpdateError: () => set({ updateContactError: null }),
  clearImportError: () => set({ importError: null }),
  clearExportError: () => set({ exportError: null }),
}));

const useContactStore = createSelectors(useContactStoreBase);

export default useContactStore;
