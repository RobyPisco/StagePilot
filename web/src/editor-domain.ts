import { transpose } from "./domain.ts";

export type Anchor = { at: number; chord: string };
export function readLine(line: string): { text: string; anchors: Anchor[] } {
  let text = "",
    last = 0;
  const anchors: Anchor[] = [];
  for (const m of line.matchAll(/\[([^\]]+)\]/g)) {
    text += line.slice(last, m.index);
    anchors.push({ at: text.length, chord: m[1] });
    last = m.index! + m[0].length;
  }
  return { text: text + line.slice(last), anchors };
}
export function writeLine(text: string, anchors: Anchor[]): string {
  let result = "",
    last = 0;
  for (const a of [...anchors].sort((a, b) => a.at - b.at)) {
    const at = Math.max(0, Math.min(text.length, a.at));
    result += text.slice(last, at) + `[${a.chord}]`;
    last = at;
  }
  return result + text.slice(last);
}
// A single contiguous text edit shifts later anchors and keeps chords in a
// replaced passage attached to the start of that passage. No chord is dropped.
export function rewriteLyrics(line: string, next: string): string {
  const { text, anchors } = readLine(line);
  let start = 0,
    end = text.length,
    nextEnd = next.length;
  while (start < end && start < nextEnd && text[start] === next[start]) start++;
  while (
    end > start &&
    nextEnd > start &&
    text[end - 1] === next[nextEnd - 1]
  ) {
    end--;
    nextEnd--;
  }
  const delta = nextEnd - end;
  return writeLine(
    next,
    anchors.map((a) => ({
      ...a,
      at: a.at < start ? a.at : a.at >= end ? a.at + delta : start,
    })),
  );
}
export function isChord(chord: string): boolean {
  return (
    chord === "N.C." ||
    /^[A-G][#b]?(?:(?:maj|min|dim|aug|sus|add|omit|no|m|M|Δ|ø|°|[-+])|[0-9#b()])*(?:\/[A-G][#b]?)?$/.test(
      chord,
    )
  );
}
export function putChord(line: string, at: number, chord: string): string {
  const parsed = readLine(line);
  if (!Number.isInteger(at) || at < 0 || at > parsed.text.length)
    throw new Error("Posizione non valida.");
  if (chord && !isChord(chord))
    throw new Error("Usa un accordo come C, Am7, Bb o F#/A#.");
  return writeLine(parsed.text, [
    ...parsed.anchors.filter((a) => a.at !== at),
    ...(chord ? [{ at, chord }] : []),
  ]);
}
export function moveChord(line: string, at: number, to: number): string {
  const parsed = readLine(line);
  if (to < 0 || to > parsed.text.length) return line;
  return writeLine(
    parsed.text,
    parsed.anchors.map((a) => (a.at === at ? { ...a, at: to } : a)),
  );
}
export function transposeBody(body: string, steps: number): string {
  if (!Number.isInteger(steps) || Math.abs(steps) > 11)
    throw new Error("Trasposizione non valida.");
  return body
    .split("\n")
    .map((line) =>
      line.startsWith("#")
        ? line
        : line.replace(/\[([^\]]+)\]/g, (all, chord) =>
            isChord(chord) && chord !== "N.C."
              ? `[${transpose(chord, steps)}]`
              : all,
          ),
    )
    .join("\n");
}
export function moveSection(
  body: string,
  index: number,
  delta: -1 | 1,
): string {
  const lines = body.split("\n");
  const starts = lines.flatMap((line, i) => (line.startsWith("#") ? [i] : []));
  const section = starts.indexOf(index),
    target = section + delta;
  if (section < 0 || target < 0 || target >= starts.length) return body;
  const a = starts[Math.min(section, target)],
    b = starts[Math.max(section, target)];
  const end = starts[Math.max(section, target) + 1] ?? lines.length;
  return [
    ...lines.slice(0, a),
    ...lines.slice(b, end),
    ...lines.slice(a, b),
    ...lines.slice(end),
  ].join("\n");
}
