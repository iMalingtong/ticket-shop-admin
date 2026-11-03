import { useUserStoreHook } from "@/store/modules/user";

export function hasPerms(value: string | string[]): boolean {
  const permissions = useUserStoreHook().permissions;
  return (Array.isArray(value) ? value : [value]).every(item =>
    permissions.includes(item)
  );
}
