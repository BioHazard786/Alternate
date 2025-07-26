import { getHeaderTitle } from "@react-navigation/elements";
import { type NativeStackHeaderProps } from "@react-navigation/native-stack";
import React from "react";
import { Appbar } from "react-native-paper";

export default function CustomNavigationBar({
  navigation,
  route,
  options,
  back,
  action,
  mode = "large",
  popToTop = false,
}: NativeStackHeaderProps & {
  action?: { icon: string; onPress: () => void };
  mode?: "small" | "medium" | "large" | "center-aligned";
  popToTop?: boolean;
}) {
  const title = getHeaderTitle(options, route.name);

  const handleGoBack = () => {
    if (popToTop) {
      navigation.popToTop();
    } else {
      navigation.goBack();
    }
  };
  return (
    <Appbar.Header mode={mode}>
      {back ? <Appbar.BackAction onPress={handleGoBack} /> : null}
      <Appbar.Content title={title} />
      {action ? (
        <Appbar.Action icon={action.icon} onPress={action.onPress} />
      ) : null}
    </Appbar.Header>
  );
}
