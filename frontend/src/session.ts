import { createContext, useContext } from "react";
import type { Me } from "./types";

export const MeContext = createContext<Me>({ authenticated: false, demoMode: false });

export function useMe(): Me {
  return useContext(MeContext);
}

export function isManagement(me: Me): boolean {
  return me.role === "OWNER" || me.role === "MANAGER";
}
