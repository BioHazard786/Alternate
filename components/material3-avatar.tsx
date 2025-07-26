import React from "react";
import { StyleProp, ViewStyle } from "react-native";
import { Path, Svg, Text } from "react-native-svg";

interface Material3AvatarProps {
  letter: string;
  backgroundColor: string;
  textColor: string;
  style?: StyleProp<ViewStyle>;
}

const Material3Avatar = ({
  letter,
  backgroundColor,
  textColor,
  style,
}: Material3AvatarProps) => {
  const pathData = `M85.812 11.542a22.48 22.48 0 0 1 28.376 0 22.48 22.48 0 0 0 17.754 4.758 22.48 22.48 0 0 1 24.574 14.188 22.48 22.48 0 0 0 12.998 12.998 22.48 22.48 0 0 1 14.188 24.574 22.48 22.48 0 0 0 4.758 17.754 22.48 22.48 0 0 1 0 28.376 22.48 22.48 0 0 0 -4.758 17.754 22.48 22.48 0 0 1 -14.188 24.574 22.48 22.48 0 0 0 -12.998 12.998 22.48 22.48 0 0 1 -24.574 14.188 22.48 22.48 0 0 0 -17.754 4.758 22.48 22.48 0 0 1 -28.376 0 22.48 22.48 0 0 0 -17.754 -4.758 22.48 22.48 0 0 1 -24.574 -14.188 22.48 22.48 0 0 0 -12.996 -12.998A22.48 22.48 0 0 1 16.3 131.944a22.48 22.48 0 0 0 -4.758 -17.754 22.48 22.48 0 0 1 0 -28.376A22.48 22.48 0 0 0 16.3 68.06a22.48 22.48 0 0 1 14.188 -24.574 22.48 22.48 0 0 0 12.998 -12.996A22.48 22.48 0 0 1 68.06 16.302a22.48 22.48 0 0 0 17.754 -4.758`;

  const size = 200;
  const center = size / 2;
  const fontSize = size * 0.5;

  return (
    <Svg
      height={size}
      width={size}
      viewBox={`0 0 ${size} ${size}`}
      style={style}
    >
      {/* The scalloped shape */}
      <Path d={pathData} fill={backgroundColor} />

      {/* The centered letter */}
      <Text
        x={center}
        y={center}
        fill={textColor}
        fontSize={fontSize}
        fontWeight="400"
        textAnchor="middle"
        alignmentBaseline="central"
      >
        {letter}
      </Text>
    </Svg>
  );
};

export default Material3Avatar;
