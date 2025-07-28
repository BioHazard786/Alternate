import { Contact } from "@/lib/types";
import { create } from "zustand";
import createSelectors from "./selectors";

type State = {
  selectedContacts: Contact[];
  selectionMode: boolean;
};

type Action = {
  toggleSelectionMode: (toogle: boolean) => void;
  selectContact: (contact: Contact) => void;
  clearSelection: () => void;
};

const initialState: State = {
  selectedContacts: [],
  selectionMode: false,
};

const useSelectedContactStoreBase = create<State & Action>((set, get) => ({
  ...initialState,
  toggleSelectionMode: (toogle) => set(() => ({ selectionMode: toogle })),
  selectContact: (contact) =>
    set((state) => {
      if (
        state.selectedContacts.some(
          (c) => c.fullPhoneNumber === contact.fullPhoneNumber
        )
      ) {
        return {
          selectedContacts: state.selectedContacts.filter(
            (c) => c.fullPhoneNumber !== contact.fullPhoneNumber
          ),
        };
      }
      return { selectedContacts: [...state.selectedContacts, contact] };
    }),
  clearSelection: () => set({ selectedContacts: [] }),
}));

const useSelectedContactStore = createSelectors(useSelectedContactStoreBase);

export default useSelectedContactStore;
