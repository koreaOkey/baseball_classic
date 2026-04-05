import React from "react";
import { IosScreenWrapper } from "./IosScreenWrapper";
import { HomeScreen } from "./HomeScreen";

export const IosHomeScreen: React.FC = () => (
  <IosScreenWrapper>
    <HomeScreen />
  </IosScreenWrapper>
);
