// import { Image } from "expo-image";
import React from "react";
import { StyleProp, ViewStyle } from "react-native";
import { useTheme } from "react-native-paper";
import Svg, { ClipPath, Defs, Image, Path, Text } from "react-native-svg";

interface Material3AvatarProps {
  letter?: string;
  backgroundColor?: string;
  textColor?: string;
  style?: StyleProp<ViewStyle>;
  photo?: string;
}

const Material3Avatar = ({
  letter,
  backgroundColor,
  textColor,
  style,
  photo,
}: Material3AvatarProps) => {
  const theme = useTheme();

  const pathData = `M85.812 11.542a22.48 22.48 0 0 1 28.376 0 22.48 22.48 0 0 0 17.754 4.758 22.48 22.48 0 0 1 24.574 14.188 22.48 22.48 0 0 0 12.998 12.998 22.48 22.48 0 0 1 14.188 24.574 22.48 22.48 0 0 0 4.758 17.754 22.48 22.48 0 0 1 0 28.376 22.48 22.48 0 0 0 -4.758 17.754 22.48 22.48 0 0 1 -14.188 24.574 22.48 22.48 0 0 0 -12.998 12.998 22.48 22.48 0 0 1 -24.574 14.188 22.48 22.48 0 0 0 -17.754 4.758 22.48 22.48 0 0 1 -28.376 0 22.48 22.48 0 0 0 -17.754 -4.758 22.48 22.48 0 0 1 -24.574 -14.188 22.48 22.48 0 0 0 -12.996 -12.998A22.48 22.48 0 0 1 16.3 131.944a22.48 22.48 0 0 0 -4.758 -17.754 22.48 22.48 0 0 1 0 -28.376A22.48 22.48 0 0 0 16.3 68.06a22.48 22.48 0 0 1 14.188 -24.574 22.48 22.48 0 0 0 12.998 -12.996A22.48 22.48 0 0 1 68.06 16.302a22.48 22.48 0 0 0 17.754 -4.758`;

  const size = 200;
  const center = size / 2;
  const fontSize = size * 0.5;

  if (photo) {
    return (
      <Svg
        height={size}
        width={size}
        viewBox={`0 0 ${size} ${size}`}
        style={style}
      >
        {/* 1. Define the clipping path */}
        <Defs>
          <ClipPath id="myClipPath">
            {/* This path creates a rectangle with a diagonal slice at the top right */}
            <Path
              d={pathData}
              fill={backgroundColor || theme.colors.surfaceVariant}
            />
          </ClipPath>
        </Defs>

        {/* 2. Apply the clipping path to the image */}
        <Image
          href={{ uri: photo }}
          width="100%"
          height="100%"
          preserveAspectRatio="xMidYMid slice"
          clipPath="url(#myClipPath)"
        />
      </Svg>
    );
  }

  return (
    <Svg
      height={size}
      width={size}
      viewBox={`0 0 ${size} ${size}`}
      style={style}
    >
      {/* The scalloped shape */}
      <Path
        d={pathData}
        fill={backgroundColor || theme.colors.surfaceVariant}
      />

      {/* The centered letter */}
      <Text
        x={center}
        y={center}
        fill={textColor || theme.colors.onSurfaceVariant}
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
