// Shared Tailwind classes for the documentation tables, so every table on the site looks the same.
export const headCell =
  "px-4 py-3 text-left text-xs font-bold text-gray-700 dark:text-gray-200";

export const row =
  "border-b border-gray-200 bg-white last:border-b-0 dark:border-gray-700 dark:bg-gray-800";

export const nameCell =
  "break-words px-4 py-3 text-left align-top font-semibold text-gray-950 dark:text-white";

export const cell =
  "break-words px-4 py-3 text-left align-top text-gray-700 dark:text-gray-300";

export const codeCell = `${cell} font-mono text-xs`;

export const link = "underline decoration-dotted underline-offset-2 hover:text-gray-950 dark:hover:text-white";

export const badge = (on: boolean) =>
  on
    ? "inline-flex w-fit rounded-full bg-emerald-100 px-2 py-1 text-xs font-semibold text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200"
    : "inline-flex w-fit rounded-full bg-gray-100 px-2 py-1 text-xs font-semibold text-gray-600 dark:bg-gray-700 dark:text-gray-300";
