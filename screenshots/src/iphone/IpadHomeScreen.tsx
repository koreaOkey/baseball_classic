import React from "react";
import { IpadScreenWrapper } from "./IpadScreenWrapper";
import { HomeScreen } from "./HomeScreen";

export const IpadHomeScreen: React.FC = () => (
  <IpadScreenWrapper>
    <HomeScreen />
  </IpadScreenWrapper>
);
