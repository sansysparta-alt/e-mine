import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/** Assemble des classes Tailwind en résolvant les conflits. */
export function cn(...classes: ClassValue[]) {
  return twMerge(clsx(classes));
}
