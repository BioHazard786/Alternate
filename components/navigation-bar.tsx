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
}: NativeStackHeaderProps & {
  action?: { icon: string; onPress: () => void };
}) {
  const title = getHeaderTitle(options, route.name);

  return (
    <Appbar.Header mode="large" elevated={true}>
      {back ? <Appbar.BackAction onPress={navigation.goBack} /> : null}
      <Appbar.Content title={title} />
      {action ? (
        <Appbar.Action icon={action.icon} onPress={action.onPress} />
      ) : null}
    </Appbar.Header>
  );
}
