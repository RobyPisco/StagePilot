import { Fragment } from "react";
import { transpose } from "./domain";

export function Chart({ body, steps = 0 }: { body: string; steps?: number }) {
  return (
    <>
      {body.split("\n").map((line, n) => {
        if (!line.trim()) return <div key={n} style={{ height: 10 }} />;
        if (line.startsWith("#"))
          return (
            <div key={n} className="sp-chart-section">
              {line.slice(1).trim()}
            </div>
          );
        const parts: { chord: string; text: string }[] = [];
        let last = 0,
          chord = "";
        for (const match of line.matchAll(/\[([^\]]+)\]/g)) {
          const text = line.slice(last, match.index);
          if (text || chord) parts.push({ chord, text });
          chord = match[1];
          last = (match.index || 0) + match[0].length;
        }
        parts.push({ chord, text: line.slice(last) });
        return (
          <div className="sp-lyric-line" key={n}>
            {parts.map((p, i) => (
              <span className="sp-piece" key={i}>
                <b>{p.chord ? transpose(p.chord, steps) : "\u00a0"}</b>
                {p.text || "\u00a0"}
              </span>
            ))}
          </div>
        );
      })}
    </>
  );
}
