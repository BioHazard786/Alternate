import { getHeaderTitle } from "@react-navigation/elements";
import { type NativeStackHeaderProps } from "@react-navigation/native-stack";
import React, { memo, useCallback } from "react";
import { Animated, StyleProp, ViewStyle } from "react-native";
import { Appbar } from "react-native-paper";

type CustomNavigationBarProps = NativeStackHeaderProps & {
  actions?: { icon: string; onPress: () => void; disabled?: boolean }[];
  mode?: "small" | "medium" | "large" | "center-aligned";
  popToTop?: boolean;
  style?: Animated.WithAnimatedValue<StyleProp<ViewStyle>>;
  elevated?: boolean;
};

const CustomNavigationBar: React.FC<CustomNavigationBarProps> = ({
  navigation,
  route,
  options,
  back,
  actions = [],
  mode = "large",
  popToTop = false,
  style,
  elevated = false,
}) => {
  const title = getHeaderTitle(options, route.name);

  const handleGoBack = useCallback(() => {
    popToTop ? navigation.popToTop() : navigation.goBack();
  }, [navigation, popToTop]);

  return (
    <Appbar.Header mode={mode} style={style} elevated={elevated}>
      {back && <Appbar.BackAction onPress={handleGoBack} />}
      <Appbar.Content title={title} />
      {actions.map((action, idx) => (
        <Appbar.Action
          key={idx}
          icon={action.icon}
          onPress={action.onPress}
          disabled={action.disabled}
        />
      ))}
    </Appbar.Header>
  );
};

export default memo(CustomNavigationBar);
