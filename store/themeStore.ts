import { MMKV } from "react-native-mmkv";
import { create } from "zustand";
import { createJSONStorage, persist, StateStorage } from "zustand/middleware";
import createSelectors from "./selectors";

const storage = new MMKV({ id: "theme-storage" });

const zustandStorage: StateStorage = {
  setItem: (name: string, value: string) => {
    return storage.set(name, value);
  },
  getItem: (name: string) => {
    const value = storage.getString(name);
    return value ?? null;
  },
  removeItem: (name: string) => {
    return storage.delete(name);
  },
};

export type ThemeMode = "light" | "dark" | "system";

type State = {
  themeMode: ThemeMode;
};

type Action = {
  setThemeMode: (mode: ThemeMode) => void;
};

const initialState = {
  themeMode: "system" as ThemeMode, // Default to system theme
};

const useThemeStoreBase = create<State & Action>()(
  persist(
    (set) => ({
      ...initialState,
      setThemeMode: (mode: ThemeMode) => set({ themeMode: mode }),
    }),
    {
      name: "theme-storage",
      storage: createJSONStorage(() => zustandStorage),
    }
  )
);

const useThemeStore = createSelectors(useThemeStoreBase);

export default useThemeStore;
